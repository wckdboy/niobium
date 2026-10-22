package io.sovware.niobium.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import io.sovware.niobium.MainActivity
import io.sovware.niobium.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class RuntimePhase { OFF, INSTALLING, STARTING, RUNNING, STOPPING, ERROR }
data class LocalRuntimeState(val phase: RuntimePhase = RuntimePhase.OFF, val progress: Int = 0,
                             val message: String? = null, val origin: String? = null, val cookie: String? = null) {
    val busy: Boolean get() = phase in listOf(RuntimePhase.INSTALLING, RuntimePhase.STARTING, RuntimePhase.STOPPING)
}

object LocalRuntime {
    internal val mutable = MutableStateFlow(LocalRuntimeState())
    val state = mutable.asStateFlow()
    fun start(context: Context) = ContextCompat.startForegroundService(context,
        Intent(context, LocalRuntimeService::class.java).setAction(LocalRuntimeService.START))
    fun stop(context: Context) = context.startService(
        Intent(context, LocalRuntimeService::class.java).setAction(LocalRuntimeService.STOP))
}

/** User-started, non-restarting runtime. Closing a screen never cancels a task. */
class LocalRuntimeService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val stopping = AtomicBoolean(false)
    private val active = AtomicBoolean(false)
    @Volatile private var process: Process? = null
    @Volatile private var proxy: LocalRuntimeProxy? = null
    private val channel = "local-dsh"

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, "Local Pixels", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            requestStop()
            if (!active.get()) stopSelf()
            return START_NOT_STICKY
        }
        startForeground(41, notification("Preparing your local Pixels…"))
        if (active.compareAndSet(false, true)) {
            stopping.set(false)
            worker.execute { runGuest() }
        }
        return START_NOT_STICKY
    }

    private fun runGuest() {
        var socket: File? = null
        var failureMessage: String? = null
        try {
            LocalRuntime.mutable.value = LocalRuntimeState(RuntimePhase.INSTALLING)
            val runtime = RuntimeInstaller(this).prepare(stopping::get) { percent ->
                if (!stopping.get()) LocalRuntime.mutable.value = LocalRuntimeState(RuntimePhase.INSTALLING, percent)
            }
            if (stopping.get()) throw InterruptedException()
            val run = UUID.randomUUID().toString().take(8)
            val guestPath = "/tmp/nb-" + run + ".sock"
            socket = File(runtime.root, guestPath.removePrefix("/"))
            check(socket.path.toByteArray().size < 108) { "Local socket path is too long." }
            socket.delete()
            val front = LocalRuntimeProxy(socket.path)
            proxy = front
            LocalRuntime.mutable.value = LocalRuntimeState(RuntimePhase.STARTING, 95)
            val child = RuntimeCommand.process(runtime, listOf("/usr/local/bin/dsh-serve"), mapOf(
                "DSH_PORT" to front.port.toString(), "NIOBIUM_SOCKET" to guestPath,
                "NODE_OPTIONS" to "--require /usr/local/lib/niobium-local.cjs")).start()
            process = child
            // Drain output without retaining provider secrets or conversation contents.
            val drain = Thread({ runCatching { child.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (input.read(buffer) >= 0) { }
            } } }, "niobium-runtime-output").apply { isDaemon = true; start() }
            val deadline = System.nanoTime() + 90_000_000_000L
            while (!stopping.get() && child.isAlive && System.nanoTime() < deadline) {
                if (LocalRuntimeProxy.ready(socket.path)) break
                Thread.sleep(200)
            }
            if (stopping.get()) throw InterruptedException()
            check(child.isAlive && LocalRuntimeProxy.ready(socket.path)) { "DSH could not start. Stop and try again. Your workspace is preserved." }
            LocalRuntime.mutable.value = LocalRuntimeState(RuntimePhase.RUNNING, 100, origin = front.origin, cookie = front.key)
            getSystemService(NotificationManager::class.java).notify(41, notification("Local Pixels are running · tap to open"))
            child.waitFor()
            drain.join(1000)
            if (!stopping.get()) error("DSH stopped unexpectedly. Start it again to resume your workspace.")
        } catch (_: InterruptedException) {
            stopping.set(true)
        } catch (failure: Exception) {
            if (!stopping.get()) failureMessage = failure.message ?: "Local DSH could not start."
        } finally {
            proxy?.close(); proxy = null
            process?.let { child ->
                // EOF asks our subreaper to terminate and reap the entire owned process tree.
                runCatching { child.outputStream.close() }
                runCatching { child.waitFor() }
            }
            process = null
            socket?.delete()
            active.set(false)
            if (stopping.get()) LocalRuntime.mutable.value = LocalRuntimeState()
            else if (failureMessage != null) LocalRuntime.mutable.value = LocalRuntimeState(RuntimePhase.ERROR, message = failureMessage)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    private fun requestStop() {
        stopping.set(true)
        if (active.get()) LocalRuntime.mutable.value = LocalRuntimeState(RuntimePhase.STOPPING)
        proxy?.close()
        runCatching { process?.outputStream?.close() }
    }
    override fun onDestroy() {
        requestStop()
        worker.shutdown()
        super.onDestroy()
    }
    private fun notification(detail: String): Notification {
        val open = PendingIntent.getActivity(this, 41,
            Intent(this, MainActivity::class.java).putExtra("tab", "local"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 42, Intent(this, LocalRuntimeService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, channel).setSmallIcon(R.drawable.ic_stat_pixel)
            .setContentTitle("Niobium · on device").setContentText(detail).setContentIntent(open)
            .setOngoing(true).addAction(Notification.Action.Builder(null, "Stop Pixels", stop).build()).build()
    }
    companion object {
        const val START = "io.sovware.niobium.LOCAL_START"
        const val STOP = "io.sovware.niobium.LOCAL_STOP"
    }
}

package ai.wckd.niobium.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

object AppPaths {
    val data: Path = if (System.getProperty("os.name").startsWith("Mac"))
        Path.of(System.getProperty("user.home"), "Library", "Application Support", "Niobium")
    else Path.of(System.getenv("XDG_DATA_HOME")?.takeIf { Path.of(it).isAbsolute } ?: Path.of(System.getProperty("user.home"), ".local", "share").toString(), "niobium")
    val workspace: Path get() = data.resolve("workspace")
    val dsh: Path get() = data.resolve("dsh")
    fun privateDirectory(path: Path) {
        Files.createDirectories(path)
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
    }
    fun runtime(packaged: Path? = System.getProperty("compose.application.resources.dir")?.let(Path::of)): Path {
        val platform = if (System.getProperty("os.name").startsWith("Mac")) "macos" else "linux"
        val arch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "arm64" else "x64"
        val candidates = if (packaged != null) listOf(packaged, packaged.resolve("$platform-$arch"))
            else listOf(Path.of("runtime/bundle/$platform-$arch"), Path.of("desktop/runtime/bundle/$platform-$arch"))
        return candidates.firstOrNull { root -> Files.isExecutable(root.resolve("node")) &&
            listOf("desktop-host.cjs", "node_modules/ws/package.json", "node_modules/@deepseek-ai/dsh/lib/bin.js").all { Files.isRegularFile(root.resolve(it)) } }
            ?: error("The local runtime is missing. Use a complete Niobium desktop package, or run tools/prepare-desktop-runtime.py when developing.")
    }
}

data class LocalStatus(val phase: String = "Stopped", val error: String? = null)
data class LocalEndpoint(val origin: String, val token: String)

class LocalEngine(private val scope: CoroutineScope, private val home: Path = AppPaths.dsh, val workspace: Path = AppPaths.workspace) {
    private val mutable = MutableStateFlow(LocalStatus())
    val state = mutable.asStateFlow()
    private var process: Process? = null
    private var generation = 0
    private var startup: Job? = null
    private var stopping: Job? = null

    fun start(onReady: (LocalEndpoint) -> Unit) {
        if (mutable.value.phase in listOf("Starting", "Running", "Stopping")) return
        val epoch = ++generation
        mutable.value = LocalStatus("Starting")
        startup = scope.launch {
            var owned: Process? = null
            try {
                val root = AppPaths.runtime().toAbsolutePath().normalize()
                withContext(Dispatchers.IO) { AppPaths.privateDirectory(home); AppPaths.privateDirectory(workspace) }
                val child = withContext(Dispatchers.IO) {
                    ProcessBuilder(root.resolve("node").toString(), root.resolve("desktop-host.cjs").toString())
                        .directory(workspace.toFile()).apply {
                            environment()["NIOBIUM_DSH_HOME"] = home.toAbsolutePath().toString()
                            environment()["NIOBIUM_WORKSPACE"] = workspace.toAbsolutePath().toString()
                        }.start()
                }
                owned = child
                if (epoch != generation) { terminate(child); return@launch }
                process = child
                val ready = CompletableDeferred<LocalEndpoint>()
                launch(Dispatchers.IO) {
                    try {
                        val line = child.inputStream.bufferedReader().readLine() ?: error("Local DSH exited before startup.")
                        require(line.length < 4096)
                        val value = JSONObject(line)
                        require(value.getString("type") == "ready")
                        val endpoint = LocalEndpoint(value.getString("origin"), value.getString("token"))
                        require(endpoint.origin.matches(Regex("^http://127\\.0\\.0\\.1:[0-9]+/niobium$")) && endpoint.token.matches(Regex("^[a-f0-9]{64}$")))
                        ready.complete(endpoint)
                    } catch (e: Exception) { ready.completeExceptionally(e) }
                }
                launch(Dispatchers.IO) { child.errorStream.bufferedReader().useLines { lines -> lines.forEach { /* No runtime credentials in app logs. */ } } }
                val endpoint = withTimeout(50_000) { ready.await() }
                if (epoch != generation) return@launch
                mutable.value = LocalStatus("Running")
                onReady(endpoint)
                runInterruptible(Dispatchers.IO) { child.waitFor() }
                if (epoch == generation) mutable.value = LocalStatus("Stopped", "Local DSH stopped. Start it again to reconnect.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (epoch == generation) mutable.value = LocalStatus("Stopped", e.message ?: "Local DSH could not start.") }
            finally {
                withContext(NonCancellable + Dispatchers.IO) { owned?.let { terminate(it) } }
                if (process === owned) process = null
            }
        }
    }

    fun stop() {
        ++generation
        mutable.value = LocalStatus("Stopping")
        startup?.cancel()
        stopping = scope.launch {
            startup?.join()
            process?.let { withContext(Dispatchers.IO) { terminate(it) } }
            process = null
            mutable.value = LocalStatus()
        }
    }

    suspend fun close() {
        stop()
        stopping?.join()
    }

    private fun terminate(child: Process) {
        // Closing stdin asks the host to stop its owned DSH process group.
        runCatching { child.outputStream.close() }
        if (!child.waitFor(6, TimeUnit.SECONDS)) {
            child.descendants().forEach { it.destroy() }
            child.destroy()
            if (!child.waitFor(2, TimeUnit.SECONDS)) { child.descendants().forEach { it.destroyForcibly() }; child.destroyForcibly() }
        }
    }
}

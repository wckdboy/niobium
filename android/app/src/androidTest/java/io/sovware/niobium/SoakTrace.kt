package io.sovware.niobium

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun soakArgument(name: String, default: Long, minimum: Long, maximum: Long): Long {
    val raw = InstrumentationRegistry.getArguments().getString(name) ?: return default
    val value = raw.toLongOrNull()
    require(value != null && value in minimum..maximum) { "$name must be between $minimum and $maximum" }
    return value
}

/** Test-process samples exclude guest Node/DSH memory and never contain prompts, endpoints or keys. */
internal class SoakTrace(context: Context, private val kind: String) {
    private val started = SystemClock.elapsedRealtime()
    private val output = AtomicFile(File(context.cacheDir, "niobium-soak-$kind.json"))
    private val samples = JSONArray()
    init { require(kind in setOf("idle", "dsh")); output.delete() }
    fun record(phase: String, counts: JSONObject = JSONObject()) {
        val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val runtime = Runtime.getRuntime()
        val row = JSONObject().put("elapsed_ms", SystemClock.elapsedRealtime() - started)
            .put("java_heap_used_bytes", runtime.totalMemory() - runtime.freeMemory())
            .put("native_heap_allocated_bytes", Debug.getNativeHeapAllocatedSize()).put("app_and_instrumentation_pss_kb", memory.totalPss)
            .put("counts", counts)
        samples.put(row)
        val receipt = JSONObject().put("kind", kind).put("phase", phase).put("recorded_at_ms", System.currentTimeMillis())
            .put("samples", samples).put("real_provider_account", false)
            .put("memory_scope", "App and instrumentation process only; excludes guest DSH/Node")
        val bytes = receipt.toString().toByteArray(Charsets.UTF_8)
        val stream = output.startWrite()
        try { stream.write(bytes); output.finishWrite(stream) }
        catch (e: Exception) { output.failWrite(stream); throw e }
        Log.i("NiobiumSoak", JSONObject().put("kind",kind).put("phase",phase).put("sample",row).toString())
    }
}

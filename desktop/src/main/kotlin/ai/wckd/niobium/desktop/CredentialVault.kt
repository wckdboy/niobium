package ai.wckd.niobium.desktop

import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

enum class VaultStatus { CHECKING, READY, LOCKED, UNAVAILABLE }
enum class CredentialKind(val wire: String) { GATEWAY("gateway"), IMAGES("images") }
enum class VaultProblem { LOCKED, UNAVAILABLE, CANCELLED, INVALID, UNCONFIRMED, DENIED }
class VaultError(val problem: VaultProblem) : Exception(when (problem) {
    VaultProblem.LOCKED -> "Unlock your OS vault, then retry."
    VaultProblem.UNAVAILABLE -> "Your OS vault is unavailable. Session-only use is available."
    VaultProblem.CANCELLED -> "Vault access was cancelled."
    VaultProblem.INVALID -> "The saved credential could not be read. Replace or delete it."
    VaultProblem.UNCONFIRMED -> "The vault operation was not confirmed. Check the saved entry before retrying."
    VaultProblem.DENIED -> "Your OS vault denied access. Allow Niobium there, then retry."
})

/** Intentionally not a data class: debugger/log output must never include a value. */
class VaultSecret internal constructor(private val value: String) {
    fun <T> use(block: (String) -> T): T = block(value)
    override fun toString() = "VaultSecret([private])"
}

interface CredentialVault {
    val name: String
    suspend fun status(): VaultStatus
    suspend fun read(kind: CredentialKind, id: String): VaultSecret?
    suspend fun write(kind: CredentialKind, id: String, label: String, value: String)
    suspend fun delete(kind: CredentialKind, id: String)
}

/** Process isolation bounds blocking native APIs and keeps values off argv/env. */
class NativeCredentialVault(private val helper: () -> Path = { AppPaths.runtime().resolve("niobium-vault") },
                            private val deadlineMillis: Long = 30_000) : CredentialVault {
    override val name = if (System.getProperty("os.name").startsWith("Mac")) "macOS Keychain" else "Secret Service"
    override suspend fun status(): VaultStatus = try {
        request("status"); VaultStatus.READY
    } catch (e: CancellationException) { throw e }
    catch (e: VaultError) { if (e.problem == VaultProblem.LOCKED) VaultStatus.LOCKED else VaultStatus.UNAVAILABLE }
    override suspend fun read(kind: CredentialKind, id: String): VaultSecret? = request("get", kind, id)?.let(::VaultSecret)
    override suspend fun write(kind: CredentialKind, id: String, label: String, value: String) { request("put", kind, id, label, value) }
    override suspend fun delete(kind: CredentialKind, id: String) { request("delete", kind, id) }

    private suspend fun request(op: String, kind: CredentialKind? = null, id: String = "", label: String = "", value: String? = null): String? = withContext(Dispatchers.IO) {
        if (op != "status" && !id.matches(Regex("[a-f0-9]{64}"))) throw VaultError(VaultProblem.INVALID)
        if (value != null && (value.length !in 1..4096 || value.any { it.code !in 33..126 })) throw VaultError(VaultProblem.INVALID)
        val child = try {
            val file = helper().toAbsolutePath()
            if (!Files.isExecutable(file)) throw VaultError(VaultProblem.UNAVAILABLE)
            val args = mutableListOf(file.toString(), op)
            if (kind != null) { args += kind.wire; args += id }
            if (op == "put") args += "Niobium · ${label.take(64)}"
            ProcessBuilder(args).apply {
                val allowed = setOf("DBUS_SESSION_BUS_ADDRESS", "XDG_RUNTIME_DIR", "XDG_DATA_HOME", "XDG_CONFIG_HOME", "DISPLAY", "WAYLAND_DISPLAY", "XDG_CURRENT_DESKTOP", "LANG", "LC_ALL", "LC_CTYPE", "HOME", "USER", "LOGNAME", "TMPDIR", "GNOME_KEYRING_CONTROL")
                environment().keys.retainAll(allowed)
                environment()["PATH"] = "/usr/bin:/bin"
            }.start()
        } catch (_: Exception) { throw VaultError(VaultProblem.UNAVAILABLE) }
        val input = value?.toByteArray(Charsets.UTF_8)
        try {
            coroutineScope {
                val stdout = async { runInterruptible {
                    val bytes = child.inputStream.readNBytes(4097)
                    try {
                        if (bytes.size > 4096) throw VaultError(VaultProblem.INVALID)
                        if (op == "get" && bytes.any { (it.toInt() and 255) !in 33..126 }) throw VaultError(VaultProblem.INVALID)
                        bytes.toString(Charsets.UTF_8)
                    } finally { bytes.fill(0) }
                } }
                val stderr = async { runInterruptible { val buffer = ByteArray(1024); while (child.errorStream.read(buffer) >= 0) { buffer.fill(0) } } }
                try {
                    val result = withTimeoutOrNull(deadlineMillis) {
                        runInterruptible { child.outputStream.use { if (input != null) it.write(input) }; child.waitFor() }
                        val output = stdout.await(); stderr.await()
                        if (op != "get" && output.isNotEmpty()) throw VaultError(VaultProblem.INVALID)
                        when (child.exitValue()) {
                            0 -> if (op == "get") output.takeIf { it.isNotEmpty() } ?: throw VaultError(VaultProblem.INVALID) else null
                            10 -> if (op == "get") null else throw VaultError(VaultProblem.UNCONFIRMED)
                            11 -> throw VaultError(VaultProblem.LOCKED)
                            12 -> throw VaultError(VaultProblem.UNAVAILABLE)
                            13 -> throw VaultError(VaultProblem.CANCELLED)
                            14 -> throw VaultError(VaultProblem.INVALID)
                            16 -> throw VaultError(VaultProblem.DENIED)
                            else -> throw VaultError(VaultProblem.UNCONFIRMED)
                        }.let { NativeResult(it) }
                    }
                    (result ?: throw VaultError(VaultProblem.UNCONFIRMED)).value
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) {
                        if (child.isAlive) { child.destroy(); if (!child.waitFor(500, TimeUnit.MILLISECONDS)) { child.destroyForcibly(); child.waitFor(1, TimeUnit.SECONDS) } }
                        runCatching { child.inputStream.close() }; runCatching { child.errorStream.close() }; runCatching { child.outputStream.close() }
                    }
                    stdout.cancel(); stderr.cancel()
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: VaultError) { throw e }
        catch (_: Exception) { throw VaultError(VaultProblem.UNCONFIRMED) }
        finally { input?.fill(0) }
    }
    private class NativeResult(val value: String?)
}

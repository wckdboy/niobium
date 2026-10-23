package ai.wckd.niobium.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

data class SavedCredential(val id: String, val kind: CredentialKind, val name: String, val address: String, val confirmed: Boolean)
data class CredentialState(val entries: List<SavedCredential> = emptyList(), val status: VaultStatus = VaultStatus.CHECKING,
                           val busy: Boolean = false, val message: String? = null, val indexWritable: Boolean = true, val checking: Boolean = false)

/** Plain metadata only. Native vault values are delivered directly to the selected owner. */
class DesktopCredentials(private val scope: CoroutineScope, private val vault: CredentialVault = NativeCredentialVault(),
                         private val index: Path = AppPaths.data.resolve("credentials/connections.json")) {
    private val mutable = MutableStateFlow(CredentialState())
    val state = mutable.asStateFlow()
    val vaultName get() = vault.name
    private val ownedJobs = java.util.concurrent.ConcurrentHashMap.newKeySet<Job>()
    private var closing = false
    private var epoch = 0
    init { refresh() }

    fun refresh() {
        if (mutable.value.busy) return
        begin(checking = true) { current ->
            val entries = readIndex()
            val status = vault.status()
            if (current == epoch) mutable.value = mutable.value.copy(entries = entries, status = status, indexWritable = true)
        }
    }
    fun save(kind: CredentialKind, name: String, address: String, value: String, onSaved: (SavedCredential) -> Unit = {}) {
        if (mutable.value.busy || !mutable.value.indexWritable) return
        val title = name.trim()
        val target = try { if (kind == CredentialKind.GATEWAY) gatewayAddress(address) else "https://api.openai.com" }
                     catch (_: Exception) { mutable.value = mutable.value.copy(message = "Use an HTTPS gateway URL without embedded credentials, query or fragment. Literal loopback HTTP is supported."); return }
        if (title.length !in 1..64 || title.any { it.isISOControl() } || value.length !in (if (kind == CredentialKind.IMAGES) 20..512 else 1..4096) || value.any { it.code !in 33..126 }) {
            mutable.value = mutable.value.copy(message = "Enter a name and a valid credential."); return
        }
        val id = identity(kind, target, title)
        begin { current ->
            val old = readIndex(); val entry = SavedCredential(id, kind, title, target, false)
            val pending = old.filterNot { it.id == id } + entry
            if (pending.size > 64) throw IllegalStateException()
            writeIndex(pending)
            if (current == epoch) mutable.value = mutable.value.copy(entries = pending)
            vault.write(kind, id, title, value)
            val confirmed = pending.map { if (it.id == id) it.copy(confirmed = true) else it }
            writeIndex(confirmed)
            if (current == epoch) { mutable.value = mutable.value.copy(entries = confirmed, status = VaultStatus.READY, message = "Saved in $vaultName."); onSaved(entry.copy(confirmed = true)) }
        }
    }
    fun use(id: String, onUse: (SavedCredential, String) -> Unit) {
        if (mutable.value.busy) return
        val entry = mutable.value.entries.firstOrNull { it.id == id } ?: return
        begin { current ->
            val value = vault.read(entry.kind, entry.id) ?: throw VaultError(VaultProblem.INVALID)
            if (current == epoch) {
                mutable.value = mutable.value.copy(status = VaultStatus.READY)
                value.use { onUse(entry, it) }
            }
        }
    }
    fun delete(id: String) {
        if (mutable.value.busy || !mutable.value.indexWritable) return
        val entry = mutable.value.entries.firstOrNull { it.id == id } ?: return
        begin { current ->
            vault.delete(entry.kind, id)
            val entries = readIndex().filterNot { it.id == id }; writeIndex(entries)
            if (current == epoch) mutable.value = mutable.value.copy(entries = entries, status = VaultStatus.READY, message = "Deleted from $vaultName.")
        }
    }
    fun dismissMessage() { mutable.value = mutable.value.copy(message = null) }
    fun cancel() {
        if (closing) return
        val pending = ownedJobs.toList()
        if (pending.isEmpty()) return
        val current = ++epoch; pending.forEach { it.cancel() }
        mutable.value = mutable.value.copy(busy = true, checking = false, message = "Cancelling…")
        launchOwned {
            pending.joinAll()
            val entries = runCatching { readIndex() }.getOrNull()
            if (current == epoch) mutable.value = mutable.value.copy(busy = false, entries = entries ?: mutable.value.entries,
                message = "Cancelled. A save may have completed; check the saved entry.")
        }
    }
    suspend fun close() {
        closing = true; ++epoch
        val pending = ownedJobs.toList(); pending.forEach { it.cancel() }; pending.joinAll()
    }
    private fun launchOwned(block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        ownedJobs.add(job); job.invokeOnCompletion { ownedJobs.remove(job) }; job.start(); return job
    }
    private fun begin(checking: Boolean = false, action: suspend (Int) -> Unit) {
        if (closing) return
        val current = ++epoch
        mutable.value = mutable.value.copy(busy = true, checking = checking, message = null)
        launchOwned {
            try { action(current) }
            catch (e: CancellationException) { throw e }
            catch (e: VaultError) { if (current == epoch) mutable.value = mutable.value.copy(message = e.message, status = when(e.problem) { VaultProblem.LOCKED -> VaultStatus.LOCKED; VaultProblem.UNAVAILABLE -> VaultStatus.UNAVAILABLE; else -> mutable.value.status }) }
            catch (_: Exception) { if (current == epoch) mutable.value = mutable.value.copy(message = "Saved connection metadata could not be confirmed. Refresh before another edit.", indexWritable = false) }
            finally { if (current == epoch) mutable.value = mutable.value.copy(busy = false, checking = false) }
        }
    }
    private suspend fun readIndex(): List<SavedCredential> = withContext(Dispatchers.IO) {
        if (!Files.exists(index, NOFOLLOW_LINKS)) return@withContext emptyList()
        check(!Files.isSymbolicLink(index) && Files.size(index) <= 256 * 1024)
        val json = JSONObject(Files.readString(index)); check(json.getInt("schema") == 1)
        val array = json.getJSONArray("entries"); check(array.length() <= 64)
        val entries = (0 until array.length()).map { n ->
            val row = array.getJSONObject(n); val kind = CredentialKind.entries.first { it.wire == row.getString("kind") }
            val id = row.getString("id"); val name = row.getString("name"); val address = row.getString("address")
            check(id.matches(Regex("[a-f0-9]{64}")) && name.length in 1..64 && name.none(Char::isISOControl))
            check(if (kind == CredentialKind.GATEWAY) gatewayAddress(address) == address else address == "https://api.openai.com")
            check(id == identity(kind, address, name))
            SavedCredential(id, kind, name, address, row.getBoolean("confirmed"))
        }
        check(entries.map { it.id }.distinct().size == entries.size); entries
    }
    private suspend fun writeIndex(entries: List<SavedCredential>) = withContext(Dispatchers.IO) {
        check(!Files.isSymbolicLink(index.parent) && !Files.isSymbolicLink(index))
        AppPaths.privateDirectory(index.parent)
        val stage = Files.createTempFile(index.parent, ".connections-", ".json", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        try {
            val rows = entries.map { JSONObject().put("id", it.id).put("kind", it.kind.wire).put("name", it.name).put("address", it.address).put("confirmed", it.confirmed) }
            Files.writeString(stage, JSONObject().put("schema", 1).put("entries", JSONArray(rows)).toString())
            Files.move(stage, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(stage) }
    }
    companion object {
        private fun identity(kind: CredentialKind, address: String, name: String) = MessageDigest.getInstance("SHA-256")
            .digest("${kind.wire}\u0000$address\u0000$name".toByteArray()).joinToString("") { "%02x".format(it) }
        fun gatewayAddress(value: String): String {
            require(value.trim().length in 1..2048)
            val url = value.trim().trimEnd('/').toHttpUrlOrNull() ?: error("Invalid URL")
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
            require(url.scheme == "https" || url.scheme == "http" && url.host in setOf("127.0.0.1", "::1"))
            return url.toString().trimEnd('/').also { require(it.length <= 2048) }
        }
    }
}

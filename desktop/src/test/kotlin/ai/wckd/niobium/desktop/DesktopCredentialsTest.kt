package ai.wckd.niobium.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

internal class FakeVault : CredentialVault {
    override val name = "Test vault"
    var availability = VaultStatus.READY
    var failure: VaultProblem? = null
    var gate: CompletableDeferred<Unit>? = null
    val entered = CompletableDeferred<Unit>()
    val values = mutableMapOf<Pair<CredentialKind, String>, String>()
    override suspend fun status() = availability
    override suspend fun write(kind: CredentialKind, id: String, label: String, value: String) {
        failure?.let { throw VaultError(it) }; values[kind to id] = value
    }
    override suspend fun read(kind: CredentialKind, id: String): VaultSecret? {
        entered.complete(Unit)
        gate?.let { withContext(NonCancellable) { withTimeout(5000) { it.await() } } }
        failure?.let { throw VaultError(it) }
        return values[kind to id]?.let(::VaultSecret)
    }
    override suspend fun delete(kind: CredentialKind, id: String) { failure?.let { throw VaultError(it) }; values.remove(kind to id) }
}

class DesktopCredentialsTest {
    private suspend fun fixture(block: suspend (DesktopCredentials, FakeVault, java.nio.file.Path, CoroutineScope) -> Unit) {
        val directory = Files.createTempDirectory("niobium-vault-controller-")
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val fake = FakeVault(); val index = directory.resolve("connections.json")
        val credentials = DesktopCredentials(scope, fake, index)
        try { idle(credentials); block(credentials, fake, index, scope) }
        finally { credentials.close(); scope.cancel(); dispatcher.close(); Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    private suspend fun idle(c: DesktopCredentials) = withTimeout(5000) { c.state.first { !it.busy } }
    private suspend fun save(c: DesktopCredentials, name: String = "Home", address: String = "https://hermes.example/proxy", key: String = "fixture-private-gateway-only") {
        c.save(CredentialKind.GATEWAY, name, address, key); idle(c)
        assertNull(c.state.value.message?.takeUnless { it.startsWith("Saved in") })
    }
    @Test fun savesOnlyMetadataAndRestoresTwoAccountsForTheSameGateway() = runBlocking { fixture { c, vault, index, scope ->
        save(c); save(c, "Work", key = "fixture-second-account-only")
        assertEquals(2, c.state.value.entries.size)
        val text = Files.readString(index); assertFalse(text.contains("fixture-private")); assertFalse(text.contains("fixture-second"))
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(index))
        val restored = DesktopCredentials(scope, vault, index); idle(restored)
        val result = CompletableDeferred<Pair<String, String>>()
        restored.use(restored.state.value.entries.first { it.name == "Work" }.id) { entry, value -> result.complete(entry.address to value) }
        val used = withTimeout(5000) { result.await() }; idle(restored)
        assertEquals("https://hermes.example/proxy", used.first); assertEquals("fixture-second-account-only", used.second)
        assertFalse(restored.state.value.toString().contains(used.second)); restored.close()
    } }
    @Test fun cancellationDiscardsALateCredentialFromThePreviousAccount() = runBlocking { fixture { c, vault, _, _ ->
        save(c); vault.gate = CompletableDeferred()
        var connected = false; c.use(c.state.value.entries.single().id) { _, _ -> connected = true }
        withTimeout(5000) { vault.entered.await() }; c.cancel(); vault.gate!!.complete(Unit); idle(c)
        assertFalse(connected); assertFalse(c.state.value.busy)
    } }
    @Test fun closingWaitsForTheOriginalReadAfterRepeatedCancellation() = runBlocking { fixture { c, vault, _, _ ->
        save(c); vault.gate = CompletableDeferred(); var used = false
        c.use(c.state.value.entries.single().id) { _, _ -> used = true }
        withTimeout(5000) { vault.entered.await() }; c.cancel(); c.cancel()
        delay(50); assertTrue(c.state.value.busy)
        val closing = async { c.close() }; delay(50); assertFalse(closing.isCompleted)
        vault.gate!!.complete(Unit); withTimeout(5000) { closing.await() }; assertFalse(used)
    } }
    @Test fun lockedSavesAreUnconfirmedAndLockedDeletionPreservesTheIndex() = runBlocking { fixture { c, vault, index, _ ->
        save(c); val original = c.state.value.entries.single(); vault.failure = VaultProblem.LOCKED
        c.delete(original.id); idle(c); assertEquals(1, c.state.value.entries.size); assertEquals(VaultStatus.LOCKED, c.state.value.status)
        c.save(CredentialKind.GATEWAY, "Other", "https://other.example", "fixture-other-gateway-only"); idle(c)
        assertEquals(1, vault.values.size); assertTrue(c.state.value.entries.any { !it.confirmed })
        assertFalse(Files.readString(index).contains("fixture-")); assertFalse(c.state.value.message!!.contains("fixture-"))
    } }
    @Test fun deletionRemovesOnlyTheChosenAccount() = runBlocking { fixture { c, vault, _, _ ->
        save(c); save(c, "Work", key = "fixture-work-private-only")
        val home = c.state.value.entries.first { it.name == "Home" }; c.delete(home.id); idle(c)
        assertEquals("Work", c.state.value.entries.single().name); assertEquals(1, vault.values.size)
    } }
    @Test fun editedMetadataCannotSendASavedTokenToAnotherOrigin() = runBlocking { fixture { c, _, index, _ ->
        save(c); val file = JSONObject(Files.readString(index)); file.getJSONArray("entries").getJSONObject(0).put("address", "https://different.example")
        Files.writeString(index, file.toString()); c.refresh(); idle(c)
        assertFalse(c.state.value.indexWritable); assertFalse(c.state.value.message!!.contains("fixture-"))
    } }
    @Test fun unknownMetadataVersionsAreNeverOverwritten() = runBlocking { fixture { c, _, index, _ ->
        val original = "{\"schema\":99,\"entries\":[]}"; Files.writeString(index, original); c.refresh(); idle(c)
        c.save(CredentialKind.GATEWAY, "Home", "https://hermes.example", "fixture-private-only"); idle(c)
        assertEquals(original, Files.readString(index)); assertFalse(c.state.value.indexWritable)
    } }
    @Test fun insecureOrCredentialBearingSavedUrlsFailBeforeAnyWrite() = runBlocking { fixture { c, vault, index, _ ->
        for (url in listOf("http://remote.example", "https://user:private@host.example", "https://host.example?token=private", "https://host.example#private")) {
            c.save(CredentialKind.GATEWAY, "Bad", url, "fixture-private-only"); idle(c)
        }
        assertTrue(vault.values.isEmpty()); assertFalse(Files.exists(index))
    } }
    @Test fun gatewayAndImageCredentialsUseDifferentNativeSlots() = runBlocking { fixture { c, vault, _, _ ->
        save(c, "Shared"); c.save(CredentialKind.IMAGES, "Shared", "", "fixture-images-private-only"); idle(c)
        assertEquals(2, vault.values.size); assertEquals(setOf(CredentialKind.IMAGES, CredentialKind.GATEWAY), c.state.value.entries.map { it.kind }.toSet())
        assertEquals("VaultSecret([private])", VaultSecret("private-value").toString())
    } }
}

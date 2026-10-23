package ai.wckd.niobium.desktop

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class NativeCredentialVaultTest {
    private suspend fun helper(code: String, block: suspend (java.nio.file.Path, java.nio.file.Path) -> Unit) {
        val directory = Files.createTempDirectory("niobium-vault-process-")
        val path = directory.resolve("helper")
        Files.writeString(path, "#!/usr/bin/python3\n" + code)
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
        try { block(path, directory) }
        finally { Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    @Test fun secretTravelsThroughStdinAndNeverThroughArgumentsOrTheProcessEnvironment() = runBlocking {
        helper("""
            import sys, json, pathlib, os
            value = sys.stdin.buffer.read()
            assert value == b'fixture-private-vault-only'
            assert b'fixture-private-vault-only' not in '\n'.join(sys.argv).encode()
            assert b'fixture-private-vault-only' not in '\n'.join(os.environ.values()).encode()
            assert not any(k.endswith('_API_KEY') for k in os.environ)
            pathlib.Path(__file__).with_name('called').write_text('safe')
        """.trimIndent()) { path, directory ->
            NativeCredentialVault({ path }).write(CredentialKind.GATEWAY, "a".repeat(64), "Home", "fixture-private-vault-only")
            assertEquals("safe", Files.readString(directory.resolve("called")))
        }
    }
    @Test fun stalledHelpersAreTerminatedAndNeverReportedAsSuccessfulSaves() = runBlocking {
        helper("import time, os, pathlib\npathlib.Path(__file__).with_name('pid').write_text(str(os.getpid()))\ntime.sleep(30)\n") { path, directory ->
            val vault = NativeCredentialVault({ path }, deadlineMillis = 2000)
            try { vault.write(CredentialKind.GATEWAY, "a".repeat(64), "Home", "fixture-private-only"); fail("Expected timeout") }
            catch (e: VaultError) { assertEquals(VaultProblem.UNCONFIRMED, e.problem); assertFalse(e.message!!.contains("fixture-private")) }
            val pid = Files.readString(directory.resolve("pid")).toLong(); assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
        }
    }
    @Test fun nativeErrorsAreMappedWithoutReturningTheirDiagnosticOrSecret() = runBlocking {
        helper("import sys\nsys.stdin.buffer.read()\nsys.stderr.write('PRIVATE-DIAGNOSTIC')\nsys.exit(11)\n") { path, _ ->
            val vault = NativeCredentialVault({ path })
            assertEquals(VaultStatus.LOCKED, vault.status())
            try { vault.read(CredentialKind.GATEWAY, "b".repeat(64)); fail("Expected lock") }
            catch (e: VaultError) { assertEquals(VaultProblem.LOCKED, e.problem); assertFalse(e.toString().contains("PRIVATE")) }
        }
    }
    @Test fun callerCancellationKillsTheOwnedHelper() = runBlocking {
        helper("import time, os, pathlib\npathlib.Path(__file__).with_name('pid').write_text(str(os.getpid()))\ntime.sleep(30)\n") { path, directory ->
            val task = async { NativeCredentialVault({ path }).read(CredentialKind.GATEWAY, "c".repeat(64)) }
            withTimeout(3000) { while (!Files.exists(directory.resolve("pid"))) delay(10) }
            task.cancelAndJoin()
            val pid = Files.readString(directory.resolve("pid")).toLong(); assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
        }
    }
}

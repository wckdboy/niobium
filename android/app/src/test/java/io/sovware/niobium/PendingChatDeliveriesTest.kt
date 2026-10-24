package io.sovware.niobium

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class PendingChatDeliveriesTest {
    private val scope = PendingChatDeliveries.scope(false,"https://example.com/proxy","private-token","nova","session-1")
    @Test fun uncertainReceiptsSurviveRestartWithoutSavingCredentialsOrContent() {
        var data: String? = null
        fun ledger() = PendingChatDeliveries({data},{data=it;true})
        val id = ledger().reserve(scope)
        assertTrue(ledger().pending(scope))
        assertFalse(data!!.contains("private-token")); assertFalse(data!!.contains("example.com")); assertFalse(data!!.contains("nova"))
        assertThrows(IllegalStateException::class.java) { ledger().reserve(scope) }
        ledger().resolve(scope,"another-id"); assertTrue(ledger().pending(scope))
        ledger().resolve(scope,id); assertFalse(ledger().pending(scope))
    }
    @Test fun accountAndConversationScopesAreSeparateWhileLocalRunCookiesCanRotate() {
        assertNotEquals(scope,PendingChatDeliveries.scope(false,"https://example.com/proxy","other","nova","session-1"))
        assertNotEquals(scope,PendingChatDeliveries.scope(false,"https://other.example","private-token","nova","session-1"))
        assertNotEquals(scope,PendingChatDeliveries.scope(false,"https://example.com/proxy","private-token","nova","session-2"))
        assertEquals(PendingChatDeliveries.scope(true,"http://127.0.0.1:1","first","nova","local"),PendingChatDeliveries.scope(true,"http://127.0.0.1:2","second","nova","local"))
    }
    @Test fun corruptOrFailedStorageNeverAllowsAnotherSend() {
        val broken=PendingChatDeliveries({"bad JSON"},{error("Must not overwrite corruption")})
        assertThrows(IllegalStateException::class.java) { broken.reserve(scope) }
        assertThrows(IllegalStateException::class.java) { broken.resolve(scope) }
        var data: String?=null
        val failed=PendingChatDeliveries({data},{data=it;false})
        assertThrows(IllegalStateException::class.java) { failed.reserve(scope) }
        assertThrows(IllegalStateException::class.java) { failed.pending(scope) }
    }
    @Test fun confirmedDeletionFailureKeepsTheControllerLocked() {
        var data: String?=null;var allowed=true
        val ledger=PendingChatDeliveries({data},{data=it;allowed})
        val id=ledger.reserve(scope);allowed=false
        assertThrows(IllegalStateException::class.java) { ledger.resolve(scope,id) }
        assertThrows(IllegalStateException::class.java) { ledger.reserve(scope) }
    }
    @Test fun separateEngineOwnersCannotLoseEachOthersPendingReceipts() {
        var data: String? = null
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        val start = java.util.concurrent.CountDownLatch(1)
        val scopes = (0 until 32).map { PendingChatDeliveries.scope(false,"https://example.com","token","pixel-$it","session") }
        try {
            val jobs = scopes.map { key -> pool.submit {
                start.await()
                PendingChatDeliveries({data},{data=it;true}).reserve(key)
            } }
            start.countDown(); jobs.forEach { it.get(5,java.util.concurrent.TimeUnit.SECONDS) }
            val restored = PendingChatDeliveries({data},{data=it;true})
            scopes.forEach { assertTrue(restored.pending(it)) }
        } finally { pool.shutdownNow() }
    }
    @Test fun boundedPhotoReadRejectsOversizedAndUnknownLengthInputs() {
        assertArrayEquals(byteArrayOf(1,2,3),PhotoAttachments.readBounded(ByteArrayInputStream(byteArrayOf(1,2,3)),3))
        assertThrows(IllegalArgumentException::class.java) { PhotoAttachments.readBounded(ByteArrayInputStream(ByteArray(4)),3) }
    }
}

package ai.wckd.niobium.desktop

import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class RoomOutboxTest {
    @Test fun anAmbiguousSendKeepsItsReceiptAfterRestartAndScopesEngines() {
        val dir = Files.createTempDirectory("nb-outbox-test-")
        try {
            val file = dir.resolve("receipts.json"); val first = RoomOutbox(file)
            val receipt = first.reserve("local", "room-1", "Private message")
            val restored = RoomOutbox(file)
            assertEquals(receipt, restored.reserve("local", "room-1", "Private message"))
            assertNotEquals(receipt, restored.reserve("remote", "room-1", "Private message"))
            assertNotEquals(receipt, restored.reserve("local", "room-2", "Private message"))
            assertFalse(Files.readString(file).contains("Private message"))
            restored.acknowledge(setOf(receipt))
            assertNotEquals(receipt, restored.reserve("local", "room-1", "Private message"))
        } finally { Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }
    @Test fun corruptReceiptsBlockAReplayAndPreserveTheirOriginalBytes() {
        val dir = Files.createTempDirectory("nb-outbox-corrupt-")
        try {
            val file = dir.resolve("receipts.json"); Files.writeString(file, "broken")
            assertThrows(IllegalStateException::class.java) { RoomOutbox(file).reserve("local", "room", "same prompt") }
            assertEquals("broken", Files.readString(file))
        } finally { Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }
}

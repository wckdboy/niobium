package ai.wckd.niobium.desktop

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AppLeaseTest {
    @Test fun aSecondInstanceCannotWriteTheEngineHomeUntilTheFirstCloses() {
        val directory = Files.createTempDirectory("nb-lease-test-")
        try {
            val first = requireNotNull(AppLease.acquire(directory))
            try { assertNull(AppLease.acquire(directory)) } finally { first.close() }
            requireNotNull(AppLease.acquire(directory)).close()
        } finally { Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }
}

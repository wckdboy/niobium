package ai.wckd.niobium.desktop

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class RuntimeLocatorTest {
    private fun populate(root: Path) {
        Files.createDirectories(root)
        Files.writeString(root.resolve("node"), "fixture executable")
        Files.setPosixFilePermissions(root.resolve("node"), PosixFilePermissions.fromString("rwx------"))
        for (name in listOf("desktop-host.cjs", "node_modules/ws/package.json", "node_modules/@deepseek-ai/dsh/lib/bin.js")) {
            val file = root.resolve(name); Files.createDirectories(file.parent); Files.writeString(file, "fixture")
        }
    }
    @Test fun installedResourcesSupportFlatAndArchitectureLayoutsWithoutDevelopmentFallback() {
        val directory = Files.createTempDirectory("nb-resources-test-")
        try {
            assertThrows(IllegalStateException::class.java) { AppPaths.runtime(directory) }
            val platform = if (System.getProperty("os.name").startsWith("Mac")) "macos" else "linux"
            val arch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "arm64" else "x64"
            val nested = directory.resolve("$platform-$arch")
            populate(nested); assertEquals(nested, AppPaths.runtime(directory))
            populate(directory); assertEquals(directory, AppPaths.runtime(directory))
        } finally { Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }
}

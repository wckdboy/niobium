package io.sovware.niobium

import io.sovware.niobium.runtime.RuntimeArchive
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class RuntimeArchiveTest {
    private data class Entry(val path: String, val content: String = "", val type: Char = '0', val link: String = "")
    private fun archive(vararg entries: Entry): ByteArray {
        val raw = ByteArrayOutputStream()
        for (entry in entries) {
            val header = ByteArray(512)
            fun field(at: Int, width: Int, value: String) { value.toByteArray().copyInto(header, at, 0, minOf(width, value.length)) }
            field(0, 100, entry.path); field(100, 8, "0000755")
            field(124, 12, entry.content.toByteArray().size.toString(8).padStart(11, '0'))
            for (at in 148..155) header[at] = 32
            header[156] = entry.type.code.toByte(); field(157, 100, entry.link)
            field(148, 8, header.sumOf { it.toInt() and 255 }.toString(8).padStart(6, '0') + "\u0000 ")
            raw.write(header); raw.write(entry.content.toByteArray())
            repeat((512 - entry.content.toByteArray().size % 512) % 512) { raw.write(0) }
        }
        raw.write(ByteArray(1024))
        return ByteArrayOutputStream().also { result -> GZIPOutputStream(result).use { it.write(raw.toByteArray()) } }.toByteArray()
    }
    private fun directory() = Files.createTempDirectory("niobium-archive-test").toFile()
    @Test fun extractsFilesAndGuestLinks() {
        val root = directory()
        RuntimeArchive().extract(archive(Entry("usr/bin"), Entry("usr/tool", type = '2', link = "/bin/busybox")).inputStream(), root)
        assertTrue(File(root, "usr/bin").isFile)
        assertEquals("/bin/busybox", Files.readSymbolicLink(File(root, "usr/tool").toPath()).toString())
    }
    @Test fun traversalCannotWriteOutside() {
        val root = directory()
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeArchive().extract(archive(Entry("../outside")).inputStream(), root)
        }
        assertFalse(File(root.parentFile, "outside").exists())
    }
    @Test fun symlinkParentCannotEvenCreateOutsideDirectories() {
        val root = directory(); val outside = directory()
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeArchive().extract(archive(Entry("escape", type = '2', link = outside.path), Entry("escape/should-not-exist/file", "oops")).inputStream(), root)
        }
        assertFalse(File(outside, "should-not-exist").exists())
    }
    @Test fun enforcesSizeAndEntryBudgets() {
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeArchive(maxBytes = 2).extract(archive(Entry("large", "abc")).inputStream(), directory())
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeArchive(maxEntries = 1).extract(archive(Entry("one"), Entry("two")).inputStream(), directory())
        }
    }
    @Test fun cancellationStopsBeforeWriting() {
        val root = directory()
        assertThrows(InterruptedException::class.java) {
            RuntimeArchive().extract(archive(Entry("never-written", "data")).inputStream(), root, { true })
        }
        assertTrue(root.list().isNullOrEmpty())
    }
    @Test fun doesNotReplaceLinksWithFilesOrDirectories() {
        for (kind in listOf('0', '5')) {
            assertThrows(IllegalArgumentException::class.java) {
                RuntimeArchive().extract(archive(Entry("link", type = '2', link = "/tmp"), Entry("link", type = kind)).inputStream(), directory())
            }
        }
    }
}

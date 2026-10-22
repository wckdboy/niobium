package io.sovware.niobium.runtime

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.GZIPInputStream

/** Extract into a new private directory. Never follows a previously extracted link. */
class RuntimeArchive(private val maxBytes: Long = 512L * 1024 * 1024, private val maxEntries: Int = 100000) {
    fun extract(compressed: InputStream, destination: File, cancelled: () -> Boolean = { false },
                progress: (Long) -> Unit = {}) {
        require(destination.isDirectory && destination.list().isNullOrEmpty()) { "Extraction needs an empty directory." }
        val root = destination.canonicalFile.toPath()
        var expanded = 0L
        var count = 0
        var longPath: String? = null
        var longLink: String? = null
        fun target(name: String): File {
            require(name.length <= 4096 && !name.startsWith("/") && '\u0000' !in name) { "Invalid archive path." }
            val relative = name.removePrefix("./")
            require(relative.split('/').none { it == ".." }) { "Archive path escapes the runtime." }
            val path = root.resolve(relative).normalize()
            require(path.startsWith(root)) { "Archive path escapes the runtime." }
            val parent = path.parent
            if (path != root && parent != null) {
                var current = root
                for (part in root.relativize(parent)) {
                    current = current.resolve(part)
                    require(!Files.isSymbolicLink(current)) { "Archive parent follows a link." }
                    if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(current)
                    require(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) { "Invalid archive parent." }
                }
            }
            return path.toFile()
        }
        GZIPInputStream(compressed.buffered(), 64 * 1024).use { input ->
            val header = ByteArray(512)
            val buffer = ByteArray(64 * 1024)
            fun fully(bytes: ByteArray, length: Int) {
                var at = 0
                while (at < length) {
                    if (cancelled()) throw InterruptedException("Runtime preparation cancelled.")
                    val read = input.read(bytes, at, length - at)
                    check(read > 0) { "The runtime archive is truncated." }
                    at += read
                }
            }
            fun text(at: Int, length: Int): String = header.copyOfRange(at, at + length)
                .takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
            fun number(at: Int, length: Int): Long = text(at, length).trim().ifEmpty { "0" }.toLong(8)
            while (true) {
                fully(header, 512)
                if (header.all { it == 0.toByte() }) break
                require(++count <= maxEntries) { "Too many runtime archive entries." }
                val expected = number(148, 8)
                val checksum = header.indices.sumOf { if (it in 148..155) 32L else (header[it].toInt() and 255).toLong() }
                require(expected == checksum) { "Runtime archive header checksum failed." }
                val size = number(124, 12)
                require(size >= 0 && size <= maxBytes && expanded <= maxBytes - size) { "Runtime archive is too large." }
                expanded += size
                val kind = header[156].toInt().toChar()
                val prefix = text(345, 155)
                val name = longPath ?: (if (prefix.isEmpty()) "" else "$prefix/") + text(0, 100)
                val link = longLink ?: text(157, 100)
                longPath = null; longLink = null
                var remaining = size
                when (kind) {
                    'L', 'K', 'x', 'g' -> {
                        require(size <= 65536) { "Runtime archive metadata is too large." }
                        val payload = ByteArray(size.toInt()); fully(payload, payload.size); remaining = 0
                        if (kind == 'L') longPath = payload.toString(Charsets.UTF_8).trimEnd('\u0000')
                        else if (kind == 'K') longLink = payload.toString(Charsets.UTF_8).trimEnd('\u0000')
                        else if (kind == 'x') {
                            var at = 0
                            while (at < payload.size) {
                                val end = payload.indexOfFrom(' '.code.toByte(), at)
                                require(end > at) { "Malformed runtime archive metadata." }
                                val length = payload.copyOfRange(at, end).toString(Charsets.US_ASCII).toInt()
                                require(length > end - at && at + length <= payload.size) { "Malformed runtime archive metadata." }
                                val entry = payload.copyOfRange(end + 1, at + length).toString(Charsets.UTF_8).trimEnd('\n')
                                if (entry.startsWith("path=")) longPath = entry.removePrefix("path=")
                                if (entry.startsWith("linkpath=")) longLink = entry.removePrefix("linkpath=")
                                at += length
                            }
                        }
                    }
                    '5' -> {
                        val output = target(name)
                        require(!Files.isSymbolicLink(output.toPath())) { "Archive directory replaces a link." }
                        check(output.isDirectory || output.mkdirs()) { "Could not create runtime directory." }
                    }
                    '0', '\u0000' -> {
                        val output = target(name)
                        require(!Files.exists(output.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Archive entry replaces an existing file." }
                        output.outputStream().use { sink ->
                            while (remaining > 0) {
                                val amount = minOf(buffer.size.toLong(), remaining).toInt()
                                fully(buffer, amount); sink.write(buffer, 0, amount); remaining -= amount
                            }
                        }
                        check(output.setReadable(true, true) && output.setWritable(true, true)) { "Could not protect runtime file." }
                        if (number(100, 8) and 73L != 0L) check(output.setExecutable(true, true)) { "Could not prepare runtime executable." }
                    }
                    '2' -> {
                        val output = target(name)
                        require(link.length <= 4096 && link.isNotEmpty() && '\u0000' !in link) { "Invalid archive link." }
                        require(!Files.exists(output.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Archive link replaces a file." }
                        // Absolute guest links are valid; extraction never follows them.
                        Files.createSymbolicLink(output.toPath(), File(link).toPath())
                    }
                    '1' -> {
                        val output = target(name); val original = target(link)
                        require(Files.isRegularFile(original.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Invalid archive hard link." }
                        Files.createLink(output.toPath(), original.toPath())
                    }
                    '3', '4', '6' -> require(name.removePrefix("./").startsWith("dev/")) { "Unexpected runtime device." }
                    else -> error("Unsupported runtime archive entry.")
                }
                while (remaining > 0) {
                    val amount = minOf(buffer.size.toLong(), remaining).toInt()
                    fully(buffer, amount); remaining -= amount
                }
                val padding = ((512 - size % 512) % 512).toInt()
                if (padding > 0) fully(buffer, padding)
                progress(expanded)
            }
        }
    }
}
private fun ByteArray.indexOfFrom(value: Byte, start: Int): Int {
    for (index in start until size) if (this[index] == value) return index
    return -1
}

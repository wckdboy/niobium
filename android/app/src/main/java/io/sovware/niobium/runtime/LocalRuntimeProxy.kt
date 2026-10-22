package io.sovware.niobium.runtime

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** A per-run authenticated HTTP/WebSocket bridge to the private guest socket. */
class LocalRuntimeProxy(private val socketPath: String) : Closeable {
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int = server.localPort
    val origin: String = "http://127.0.0.1:" + port
    val key: String = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
    private val closed = AtomicBoolean(false)
    private val permits = Semaphore(12)
    private val workers = Executors.newCachedThreadPool()
    private val connections = Collections.synchronizedSet(mutableSetOf<Closeable>())

    init {
        workers.execute {
            while (!closed.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                if (!permits.tryAcquire()) { client.close(); continue }
                connections.add(client)
                workers.execute {
                    try { bridge(client) } catch (_: Exception) { /* Per-request failures stay private. */ }
                    finally { connections.remove(client); runCatching { client.close() }; permits.release() }
                }
            }
        }
    }

    private fun bridge(client: Socket) {
        val raw = header(client.getInputStream(), System.nanoTime() + 2_000_000_000L) { client.soTimeout = it }
        val lines = raw.split("\r\n")
        val request = lines.first().split(' ')
        val fields = lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val colon = line.indexOf(':')
            require(colon > 0 && !line.startsWith(' ') && !line.startsWith('\t')) { "Invalid header" }
            line.substring(0, colon).lowercase() to line.substring(colon + 1).trim()
        }
        fun values(name: String) = fields.filter { it.first == name }.map { it.second }
        val token = values("cookie").flatMap { it.split(';') }.map { it.trim() }
            .filter { it.startsWith("niobium_session=") }.singleOrNull()?.substringAfter('=').orEmpty()
        val allowed = request.size == 3 && request[1].startsWith("/") && !request[1].startsWith("//") &&
            request[2] == "HTTP/1.1" && values("host") == listOf("127.0.0.1:" + port) &&
            values("origin").all { it == origin } &&
            MessageDigest.isEqual(key.toByteArray(), token.toByteArray())
        if (!allowed) {
            client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        client.soTimeout = 0
        val guest = LocalSocket()
        connections.add(guest)
        try {
            guest.connect(LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM))
            val upgrade = values("upgrade").any { it.equals("websocket", true) }
            val forwarded = buildString {
                append(lines.first()); append("\r\n")
                fields.forEach { (name, value) ->
                    if (name != "cookie" && name != "connection") append(name + ": " + value + "\r\n")
                    if (name == "cookie") {
                        val cookies = value.split(';').filterNot { it.trim().startsWith("niobium_session=") }.joinToString(";")
                        if (cookies.isNotBlank()) append("cookie: " + cookies + "\r\n")
                    }
                }
                append("connection: " + (if (upgrade) "Upgrade" else "close") + "\r\n\r\n")
            }
            guest.outputStream.write(forwarded.toByteArray(Charsets.ISO_8859_1)); guest.outputStream.flush()
            workers.execute {
                try { client.getInputStream().copyTo(guest.outputStream); guest.shutdownOutput() }
                catch (_: Exception) { }
            }
            // Set CSP on HTTP documents to keep the privileged WebView on this owned origin.
            val response = header(guest.inputStream)
            val responseLines = response.split("\r\n").filter { it.isNotEmpty() }
            val output = client.getOutputStream()
            output.write((responseLines.filterNot { it.startsWith("content-security-policy:", true) }.joinToString("\r\n") +
                "\r\nContent-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data: blob:; " +
                "font-src 'self' data:; frame-src 'none'; form-action 'self'; base-uri 'self'\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
            guest.inputStream.copyTo(output); output.flush()
        } finally { connections.remove(guest); runCatching { guest.close() } }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        server.close()
        synchronized(connections) { connections.toList().forEach { runCatching { it.close() } }; connections.clear() }
        workers.shutdownNow()
    }

    companion object {
        private fun header(input: InputStream, deadline: Long = Long.MAX_VALUE, timeout: ((Int) -> Unit)? = null): String {
            val bytes = java.io.ByteArrayOutputStream()
            var tail = 0
            while (bytes.size() < 32768) {
                if (deadline != Long.MAX_VALUE) {
                    val remaining = (deadline - System.nanoTime()) / 1_000_000
                    check(remaining > 0) { "HTTP header deadline exceeded" }
                    timeout?.invoke(remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                }
                val value = input.read()
                check(value >= 0) { "Truncated HTTP header" }
                bytes.write(value)
                tail = (tail shl 8) or value
                if (tail == 0x0d0a0d0a) return bytes.toString("ISO-8859-1")
            }
            error("HTTP header is too large")
        }
        fun ready(socketPath: String): Boolean = probe(socketPath)?.startsWith("HTTP/1.1 200") == true
        fun probe(socketPath: String): String? = runCatching {
            LocalSocket().use { guest ->
                guest.connect(LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM))
                guest.soTimeout = 2000
                guest.outputStream.write("GET / HTTP/1.1\r\nHost: localhost:3080\r\nConnection: close\r\n\r\n".toByteArray())
                header(guest.inputStream).lineSequence().first()
            }
        }.getOrElse { "Unavailable: " + it.javaClass.simpleName + ": " + it.message }
    }
}

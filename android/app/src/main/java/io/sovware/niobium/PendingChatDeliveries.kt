package io.sovware.niobium

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Persist only opaque conversation digests and IDs, before any send side effect. */
class PendingChatDeliveries(private val read: () -> String?, private val write: (String) -> Boolean) {
    private var storageFailed = false
    private fun persist(value: String) {
        if (!write(value)) { storageFailed = true; error("Delivery receipt storage failed. Restart after checking device storage; no automatic retry will run.") }
    }
    private fun load(): JSONObject {
        check(!storageFailed) { "Delivery receipt storage is unavailable. Sending is locked." }
        val raw = read() ?: return JSONObject()
        check(raw.length <= 32768) { "Saved delivery receipts need repair. Sending is locked." }
        return runCatching { JSONObject(raw).also { rows ->
            require(rows.length() <= 128)
            rows.keys().forEach { key ->
                require(key.matches(Regex("[a-f0-9]{64}")))
                UUID.fromString(rows.getString(key))
            }
        } }.getOrElse { error("Saved delivery receipts need repair. Sending is locked.") }
    }
    fun pending(scope: String): Boolean = synchronized(lock) { load().has(scope) }
    fun reserve(scope: String): String = synchronized(lock) {
        val rows = load()
        check(!rows.has(scope)) { "Check the previous delivery before sending again." }
        check(rows.length() < 128) { "Review pending deliveries before sending more messages." }
        val id = UUID.randomUUID().toString(); rows.put(scope, id)
        persist(rows.toString())
        id
    }
    fun resolve(scope: String, id: String? = null) = synchronized(lock) {
        val rows = load()
        if (!rows.has(scope) || id != null && rows.getString(scope) != id) return@synchronized
        rows.remove(scope)
        persist(rows.toString())
    }
    companion object {
        private val lock = Any()
        fun scope(local: Boolean, origin: String, token: String, profile: String, session: String): String {
            // DSH run cookies/ports rotate; its app-private home and session stay the same.
            val key = JSONArray(listOf(if (local) "dsh" else "hermes", if (local) "" else origin.trimEnd('/'),
                if (local) "" else token, profile, session)).toString()
            return MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}

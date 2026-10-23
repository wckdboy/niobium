package ai.wckd.niobium.desktop

import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.UUID

/** Stores only opaque content digests and UUID receipts, never tokens or messages. */
class RoomOutbox(private val file: Path = AppPaths.data.resolve("room-receipts.json")) {
    private fun load(): JSONObject {
        if (!Files.exists(file)) return JSONObject()
        return runCatching { JSONObject(Files.readString(file)).also { rows ->
            require(rows.keys().asSequence().all { key -> key.matches(Regex("^[a-f0-9]{64}$")) && rows.get(key) is String && UUID.fromString(rows.getString(key)) != null })
        } }.getOrElse { error("Saved room receipts need repair. Check existing deliveries before sending again.") }
    }
    private fun save(rows: JSONObject) {
        AppPaths.privateDirectory(file.parent)
        val stage = Files.createTempFile(file.parent, ".receipts-", ".json", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        try { Files.writeString(stage, rows.toString()); Files.move(stage, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(stage) }
    }
    fun reserve(scope: String, room: String, text: String): String = synchronized(lock) {
        val key = digest(JSONArray(listOf(scope, room, text)).toString())
        val rows = load()
        if (rows.has(key)) { save(rows); return@synchronized rows.getString(key) }
        check(rows.length() < 256) { "Check pending room deliveries before sending more messages." }
        val id = UUID.randomUUID().toString(); rows.put(key, id); save(rows); id
    }
    fun acknowledge(ids: Set<String>) = synchronized(lock) {
        if (ids.isEmpty()) return@synchronized
        val rows = load()
        val matches = rows.keys().asSequence().filter { rows.getString(it) in ids }.toList()
        matches.forEach(rows::remove)
        if (matches.isNotEmpty()) save(rows)
    }
    companion object {
        private val lock = Any()
        fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

package io.sovware.niobium

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurePrefs(private val context: Context, prefsName: String = "niobium.prefs", private val alias: String = "niobium.gateway.token") {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun loadConnection(): Connection? {
        if (!prefs.getBoolean("has_connection", false)) return null
        val url = prefs.getString("gateway_url", null) ?: return null
        val name = prefs.getString("gateway_name", "My Hermes") ?: "My Hermes"
        val demo = prefs.getBoolean("demo", false)
        val encrypted = prefs.getString("gateway_token", null)
        val token = encrypted?.let(::decrypt) ?: ""
        return Connection(name, url, token, demo)
    }

    fun saveConnection(connection: Connection) {
        prefs.edit()
            .putBoolean("has_connection", true)
            .putString("gateway_url", connection.url)
            .putString("gateway_name", connection.name)
            .putBoolean("demo", connection.isDemo)
            .putString("gateway_token", if (connection.token.isBlank()) null else encrypt(connection.token))
            .apply()
    }

    fun loadHarnessUrl(): String {
        prefs.getString("harness_url", null)?.let { return decrypt(it) ?: "" }
        val legacy = context.getSharedPreferences("niobium", Context.MODE_PRIVATE)
        val url = legacy.getString("niobium.harnessURL", "") ?: ""
        if (url.isNotEmpty()) {
            saveHarnessUrl(url)
            legacy.edit().remove("niobium.harnessURL").apply()
        }
        return url
    }
    fun saveHarnessUrl(url: String) { prefs.edit().putString("harness_url", encrypt(url)).apply() }
    fun loadImageKey(): String? = prefs.getString("image_key", null)?.let(::decrypt)
    fun saveImageKey(value: String?) { prefs.edit().putString("image_key", value?.let(::encrypt)).commit().also { check(it) { "Key storage failed" } } }
    fun clear() {
        prefs.edit().remove("has_connection").remove("gateway_url").remove("gateway_name")
            .remove("demo").remove("gateway_token").apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val body = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()
}

/** Uncertain send IDs survive context changes and restarts; only digests are stored. */
class RoomReceiptBook(context: Context, prefsName: String = "niobium.room.receipts") {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private fun load(): org.json.JSONObject {
        val raw = prefs.getString("receipts", null) ?: return org.json.JSONObject()
        return runCatching { org.json.JSONObject(raw).also { rows ->
            require(rows.keys().asSequence().all { key -> key.length == 64 && rows.get(key) is String && java.util.UUID.fromString(rows.getString(key)) != null })
        } }.getOrElse { error("Saved room receipts need repair. Check existing deliveries before sending again.") }
    }
    fun reserve(scope: String, room: String, text: String): String = synchronized(lock) {
        val raw = org.json.JSONArray(listOf(scope,room,text)).toString().toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it.toInt() and 255) }
        val rows = load()
        if (rows.has(digest)) {
            check(prefs.edit().putString("receipts",rows.toString()).commit()) { "Could not retain a delivery receipt. No message was sent." }
            return@synchronized rows.getString(digest)
        }
        check(rows.length() < 256) { "Check pending room deliveries before sending more messages." }
        val id = java.util.UUID.randomUUID().toString(); rows.put(digest,id)
        check(prefs.edit().putString("receipts",rows.toString()).commit()) { "Could not retain a delivery receipt. No message was sent." }
        id
    }
    fun acknowledge(ids: Set<String>) = synchronized(lock) {
        if (ids.isEmpty()) return@synchronized
        val rows = runCatching { load() }.getOrNull() ?: return@synchronized
        val matches = rows.keys().asSequence().filter { rows.getString(it).lowercase() in ids.map(String::lowercase) }.toList()
        matches.forEach(rows::remove)
        if (matches.isNotEmpty()) prefs.edit().putString("receipts",rows.toString()).commit()
    }
    companion object { private val lock = Any() }
}

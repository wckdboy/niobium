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

class SecurePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("niobium.prefs", Context.MODE_PRIVATE)
    private val alias = "niobium.gateway.token"

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

    fun clear() {
        prefs.edit().clear().apply()
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

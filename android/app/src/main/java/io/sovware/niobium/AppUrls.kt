package io.sovware.niobium

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Apply the same transport policy to entered and saved gateway/WebView addresses. */
internal fun normalizeAppUrl(raw: String, keepRoute: Boolean = false): String? {
    var text = raw.trim()
    if (text.isEmpty()) return null
    if (!text.contains("://")) text = "https://$text"
    val url = text.toHttpUrlOrNull() ?: return null
    if (url.scheme != "https" && !(url.scheme == "http" && url.host in setOf("127.0.0.1", "::1"))) return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    return if (keepRoute) url.toString() else url.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
}

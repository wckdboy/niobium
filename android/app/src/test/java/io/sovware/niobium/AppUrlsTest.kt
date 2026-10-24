package io.sovware.niobium

import org.junit.Assert.*
import org.junit.Test

class AppUrlsTest {
    @Test fun remoteAddressesRequireHttpsAndRejectEmbeddedCredentials() {
        for (url in listOf("http://example.com","http://192.168.1.8:3080","http://localhost:3080",
            "http://127.0.0.1.evil.example","https://user:secret@example.com","file:///private/file","javascript:alert(1)","")) {
            assertNull(url,normalizeAppUrl(url));assertNull(url,normalizeAppUrl(url,keepRoute=true))
        }
        assertEquals("https://example.com/proxy",normalizeAppUrl(" example.com/proxy/ "))
    }
    @Test fun webWorkspacesRetainRoutesWhileGatewayBasesDropQueriesAndFragments() {
        val url="https://example.com/proxy/?view=agents#chat"
        assertEquals(url,normalizeAppUrl(url,keepRoute=true))
        assertEquals("https://example.com/proxy",normalizeAppUrl(url))
    }
    @Test fun onlyLiteralLoopbackCanUseHttp() {
        for(url in listOf("http://127.0.0.1:3080","http://[::1]:3080")) {
            assertEquals(url,normalizeAppUrl(url));assertEquals("$url/",normalizeAppUrl(url,keepRoute=true))
        }
    }
}

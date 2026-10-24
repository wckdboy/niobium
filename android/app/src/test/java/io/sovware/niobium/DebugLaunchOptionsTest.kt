package io.sovware.niobium

import org.junit.Test
import org.junit.Assert.*

class DebugLaunchOptionsTest {
    @Test fun releaseStartupDoesNotInspectUntrustedFixtureExtras() {
        val options=DebugLaunchOptions.read(false,{error("Untrusted text extra inspected")},{error("Untrusted flag inspected")})
        assertFalse(options.demo);assertFalse(options.tools);assertFalse(options.demoAction)
        assertNull(options.connection);assertNull(options.bot);assertNull(options.room);assertNull(options.tab)
    }
    @Test fun debugFixtureConnectionAndNavigationArePreserved() {
        val values=mapOf("gateway" to "http://127.0.0.1:9119/proxy","token" to "fixture-token+&?","bot" to "pixel","tab" to "pixels","room" to "room")
        val options=DebugLaunchOptions.read(true,values::get,{it in setOf("tools","demoTool")})
        assertEquals(values["gateway"],options.connection?.url);assertEquals(values["token"],options.connection?.token)
        assertEquals("pixel",options.bot);assertEquals("pixels",options.tab);assertEquals("room",options.room)
        assertTrue(options.tools);assertFalse(options.demoAction)
    }
    @Test fun demoAliasTakesPrecedenceOverAProvidedFixtureConnection() {
        val options=DebugLaunchOptions.read(true,{"provided"},{it in setOf("NIOBIUM_DEMO","demoTool")})
        assertTrue(options.demo);assertTrue(options.demoAction);assertNull(options.connection)
    }
    @Test fun incompleteConnectionsAreIgnoredAndDiagnosticsKeepCredentialsOut() {
        for (values in listOf(mapOf("gateway" to "https://example.test"),mapOf("gateway" to "https://example.test","token" to " "))) {
            assertNull(DebugLaunchOptions.read(true,values::get,{false}).connection)
        }
        val options=DebugLaunchOptions.read(true,mapOf("gateway" to "https://private.example.test","token" to "never-log-this")::get,{false})
        assertNotNull(options.connection);assertFalse(options.toString().contains("never-log-this"));assertFalse(options.toString().contains("private.example.test"))
    }
}

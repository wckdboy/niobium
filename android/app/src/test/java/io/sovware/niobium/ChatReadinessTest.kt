package io.sovware.niobium

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatReadinessTest {
    @Test fun submissionWaitsForTheCurrentSessionAndConnection() {
        val bot = BotProfile.parse(JSONObject().put("name", "scout"))!!
        val attached = NbState(activeBot = bot, connected = true, sessionId = "session")
        assertTrue(attached.canSubmitChat)
        assertFalse(attached.copy(loadingChat = true).canSubmitChat)
        assertFalse(attached.copy(connected = false).canSubmitChat)
        assertFalse(attached.copy(sessionId = null).canSubmitChat)
        assertFalse(attached.copy(activeBot = null).canSubmitChat)
        assertTrue(NbState(activeBot = bot, demo = true).canSubmitChat)
    }
}

package io.sovware.niobium

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class InventoryEligibilityTest {
    private val ready = NbState(connected = true, sessionId = "chat", activeBot = BotProfile.parse(JSONObject().put("name", "pixel")))
    @Test fun aReadyLiveChatAllowsItsInventoryActions() {
        assertTrue(ready.canChangeTools); assertTrue(ready.canReloadSkills); assertTrue(ready.canChangeRoutines)
    }
    @Test fun reconnectingOrLoadingAChatDisablesAllInventoryMutations() {
        for (state in listOf(ready.copy(connected = false), ready.copy(loadingChat = true))) {
            assertFalse(state.canChangeTools); assertFalse(state.canReloadSkills); assertFalse(state.canChangeRoutines)
        }
    }
    @Test fun eachCatalogHoldsItsOwnMutationWhileLoadingUpdatingOrUnconfirmed() {
        for (state in listOf(ready.copy(toolsLoad = InventoryLoad(loading = true)), ready.copy(toolsUpdating = true), ready.copy(toolsLoad = InventoryLoad(error = "Unconfirmed")))) assertFalse(state.canChangeTools)
        for (state in listOf(ready.copy(skillsLoad = InventoryLoad(loading = true)), ready.copy(skillsUpdating = true), ready.copy(skillsLoad = InventoryLoad(error = "Unconfirmed")))) assertFalse(state.canReloadSkills)
        for (state in listOf(ready.copy(routinesLoad = InventoryLoad(loading = true)), ready.copy(routinesUpdating = true), ready.copy(routinesLoad = InventoryLoad(error = "Unconfirmed")))) assertFalse(state.canChangeRoutines)
    }
    @Test fun demoToolsRemainInteractiveAndLiveToolsRequireASession() {
        val home=ready.copy(activeBot = null, sessionId = null)
        assertFalse(home.canChangeTools); assertFalse(home.canChangeRoutines)
        assertTrue(home.copy(demo = true).canChangeTools); assertFalse(home.copy(demo = true).canReloadSkills)
    }
}

package io.sovware.niobium

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class InventoryCatalogsTest {
    @Test fun skillNamesKeepTheirCategoriesWithoutDuplicateRows() {
        assertEquals(mapOf("general" to listOf("audit","brief")), InventoryCatalogs.skills(JSONObject("""{"skills":{"general":["audit","brief","audit"],"empty":[]}}""")))
    }
    @Test fun malformedSkillCategoriesAndNamesCannotBecomeSuccessfulEmptyRefreshes() {
        for (raw in listOf("{}", """{"skills":[]}""", """{"skills":{"general":"not an array"}}""", """{"skills":{"general":[null]}}""", """{"skills":{"general":[7]}}""", """{"skills":{"general":[""]}}""")) {
            assertThrows(raw,IllegalStateException::class.java) {InventoryCatalogs.skills(JSONObject(raw))}
        }
    }
    @Test fun genuinelyEmptySkillInventoriesRemainValid() {
        assertTrue(InventoryCatalogs.skills(JSONObject("""{"skills":{}}""")).isEmpty())
        assertTrue(InventoryCatalogs.skills(JSONObject("""{"skills":{"general":[]}}""")).isEmpty())
    }
    @Test fun objectCatalogsRejectMalformedRowsWithoutSilentlyDroppingThem() {
        for (key in listOf("toolsets","servers","jobs")) {
            assertTrue(InventoryCatalogs.objects(JSONObject().put(key,org.json.JSONArray()),key).isEmpty())
            assertThrows(IllegalStateException::class.java) {InventoryCatalogs.objects(JSONObject(),key)}
            assertThrows(IllegalStateException::class.java) {InventoryCatalogs.objects(JSONObject().put(key,org.json.JSONArray().put("bad row")),key)}
        }
    }
}

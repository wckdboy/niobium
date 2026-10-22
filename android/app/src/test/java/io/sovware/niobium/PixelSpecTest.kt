package io.sovware.niobium

import org.junit.Assert.*
import org.junit.Test

class PixelSpecTest {
    @Test fun portableSeedsMatchTheSharedRecipeFixtures() {
        val text = javaClass.getResourceAsStream("/character-recipes.json")!!.bufferedReader().use { it.readText() }
        val fixtures = org.json.JSONArray(text)
        for (i in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(i)
            assertEquals(CharacterRecipe.parse(fixture.getJSONObject("recipe")),
                CharacterRecipe.generated(fixture.getString("archetype"), fixture.getString("seed")))
        }
    }
    @Test fun legacyRecipesDecodeAndRejectInvalidOptionalTypes() {
        val legacy = org.json.JSONObject("""{"body":"woman","palette":"nyx","hair":"cat_black","face":"fierce","outfit":"rogue","accessory":"cat_ears"}""")
        val original = CharacterRecipe.parse(legacy)
        assertEquals(CharacterRecipe.cast["nyx"], original)
        listOf("skin" to true, "hair_tone" to org.json.JSONObject.NULL, "detail" to 7).forEach { (key, value) ->
            assertTrue(runCatching { CharacterRecipe.parse(org.json.JSONObject(legacy.toString()).put(key, value)) }.isFailure)
        }
        assertFalse(original.copy(skin = "anything").valid)
    }
    @Test fun pinsPreserveTraitsAndInheritedColors() {
        val original = CharacterRecipe.cast.getValue("nyx")
        assertEquals(original, original.remixed("nyx", "new", CharacterRecipe.fields.toSet()))
        CharacterRecipe.fields.forEach { field ->
            listOf("first", "second", "æøå-星").forEach { seed ->
                val next = original.remixed("nyx", seed, setOf(field))
                assertEquals(original.value(field), next.value(field))
                assertTrue(next.valid)
                if (field in listOf("skin", "hair_tone")) assertEquals(original.palette, next.palette)
            }
        }
    }
    @Test fun distinctRolesAndExplicitCharacterIntent() {
        val cases = listOf("research biology" to "orion", "creative art" to "nova", "release engineer who watches a build" to "forge",
            "organize memory notes" to "sage", "audit security logs" to "nyx", "camera vision" to "astra",
            "write a newsletter" to "vesper", "plan a travel route" to "rook")
        val voices = cases.map { (prompt, expected) ->
            val spec = PixelBuilder.fallback(prompt, "atelier")
            assertEquals(prompt, expected, spec.character.archetype)
            assertNull(spec.validationError)
            assertTrue(spec.character.age in 28..40)
            assertFalse(spec.permissions.shell)
            assertTrue(spec.connectors.isEmpty())
            spec.soul
        }
        assertEquals(8, voices.toSet().size)
        assertEquals("woman", PixelBuilder.fallback("security witch", "same").character.recipe.body)
        assertEquals("man", PixelBuilder.fallback("male creative director", "same").character.recipe.body)
        assertEquals("axolotl", PixelBuilder.fallback("research with an axolotl", "same").companion.kind)
        assertNotEquals(PixelBuilder.fallback("research", "one").character.recipe, PixelBuilder.fallback("research", "two").character.recipe)
    }
    @Test fun identicalPixelNamesHaveSeparateProviderAndAppearanceIdentity() {
        val remote = BotProfile.parse(org.json.JSONObject().put("name", "scout"))!!
        val local = BotProfile.parse(org.json.JSONObject().put("name", "scout").put("origin", "dsh"))!!
        assertEquals(remote.name, local.name)
        assertNotEquals(remote.id, local.id)
        assertNotEquals(remote.castKey, local.castKey)
    }
    @Test fun roomMembersUseQualifiedIDsWhenProfilesRepeat() {
        val room = GroupRoom.parse(org.json.JSONObject("""{"room_id":"room","name":"Cross-gateway","members":[
            {"member_id":"a:scout","profile":"scout","display_name":"Scout A"},
            {"member_id":"b:scout","profile":"scout","display_name":"Scout B"}]}"""))!!
        assertEquals("Scout A", room.member("a:scout")?.name)
        assertEquals("Scout B", room.member("b:scout")?.name)
        assertNull(room.member("scout"))
    }
    @Test fun jsonRoundTripPreservesCustomCompanionAndRecipe() {
        val spec = PixelBuilder.fallback("security with an owl").copy(companion = PixelCompanion("owl", "Hex", "Counts loose threads."))
        val parsed = PixelBuilder.parse(spec.toJson().toString())
        assertNotNull(parsed)
        assertEquals("Hex", parsed!!.companion.name)
        assertEquals("owl", parsed.companion.kind)
        assertEquals(spec.character.recipe, parsed.character.recipe)
    }
    @Test fun malformedAndTrailingModelOutputIsRejected() {
        val json = PixelBuilder.fallback("research").toJson().toString()
        assertNull(PixelBuilder.parse("Here is your Pixel: $json"))
        assertNull(PixelBuilder.parse(json + json))
        assertNull(PixelBuilder.parse("{}"))
    }
    @Test fun permissionsAndAgesUseStrictJSONTypes() {
        for (key in listOf("network", "shell")) {
            val json = PixelBuilder.fallback("research").toJson()
            json.getJSONObject("permissions").put(key, "true")
            assertNull(PixelBuilder.parse(json.toString()))
        }
        val json = PixelBuilder.fallback("research").toJson()
        json.getJSONObject("character").put("age", 28.5)
        assertNull(PixelBuilder.parse(json.toString()))
    }
    @Test fun templatesAreValidAndNeverGrantShell() {
        listOf("security logs with a bat", "build a release", "creative images", "memory and notes", "vision camera", "research papers").forEach { prompt ->
            val spec = PixelBuilder.fallback(prompt)
            assertNull(spec.validationError)
            assertFalse(spec.permissions.shell)
            assertTrue(spec.character.age >= 18)
        }
    }
    @Test fun toolsRespectIndependentPermissionFlags() {
        val spec = PixelBuilder.fallback("security").copy(
            toolsets = listOf("web", "file", "terminal", "code_execution", "browser", "memory", "delegation", "future_root_tool"),
            permissions = PixelPermissions(network = false, filesystem = "none", shell = false))
        assertEquals(listOf("memory", "delegation"), spec.permittedToolsets)
        val networkOnly = spec.copy(permissions = PixelPermissions(network = true))
        assertFalse(networkOnly.permittedToolsets.contains("terminal"))
        assertFalse(networkOnly.permittedToolsets.contains("code_execution"))
        assertTrue(networkOnly.copy(permissions = networkOnly.permissions.copy(shell = true)).permittedToolsets.contains("terminal"))
    }
    @Test fun reviewChangesRecalculateGatewayAccess() {
        val spec = PixelBuilder.fallback("research").copy(toolsets = listOf("web", "file", "memory"))
        val draft = PixelDraft(spec, false, setOf("file", "memory"), emptySet(), emptyList())
        assertEquals(listOf("file", "memory"), draft.enabledToolsets)
        assertEquals(listOf("memory"), draft.copy(spec = spec.copy(permissions = spec.permissions.copy(filesystem = "none"))).enabledToolsets)
    }
    @Test fun invalidNamesAgesLayersAndCapabilitiesAreRejected() {
        val spec = PixelBuilder.fallback("security")
        assertNotNull(spec.copy(name = "../../bad").validationError)
        assertNotNull(spec.copy(character = spec.character.copy(age = 17)).validationError)
        assertNotNull(spec.copy(character = spec.character.copy(recipe = spec.character.recipe.copy(hair = "invented"))).validationError)
        assertNotNull(spec.copy(soul = "Too short.").validationError)
        assertNotNull(spec.copy(connectors = listOf("unknown")).validationError)
        assertNotNull(spec.copy(toolsets = listOf("web", "web")).validationError)
    }
}

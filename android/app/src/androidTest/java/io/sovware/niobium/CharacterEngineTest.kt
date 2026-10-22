package io.sovware.niobium

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.runner.RunWith
import org.json.JSONArray
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class CharacterEngineTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun workshopKeepsPinnedBuildAndSelectsNewCompanions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo", true).putExtra("tab", "pixel-review"))
        try {
            compose.onNodeWithText("Appearance & companion ↓").performScrollTo().performClick()
            compose.onNodeWithTag("character.body").assertTextContains("Feminine")
            compose.onNodeWithTag("character.remix").performScrollTo().performClick()
            compose.onNodeWithTag("character.body").assertTextContains("Feminine")
            val hair = compose.onNodeWithTag("character.hair")
            val style = hair.fetchSemanticsNode().config[SemanticsProperties.Text]
            compose.onNodeWithTag("character.pin.hair").performScrollTo().performClick()
            compose.onNodeWithText("Appearance & companion ↑").performScrollTo().performClick()
            compose.onNodeWithText("Appearance & companion ↓").performScrollTo().performClick()
            compose.onNodeWithTag("character.pin.hair")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pinned"))
            compose.onNodeWithTag("character.remix").performScrollTo().performClick()
            assertEquals(style, hair.fetchSemanticsNode().config[SemanticsProperties.Text])
            compose.onNodeWithTag("familiar.mimic").performScrollTo().performClick().assertIsSelected()
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun nativePixelsMatchTheSharedRgbaFixtures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtures = JSONArray(instrumentation.context.assets.open("character-recipes.json").bufferedReader().use { it.readText() })
        for (i in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(i)
            val recipe = CharacterRecipe.generated(fixture.getString("archetype"), fixture.getString("seed"))
            val image = requireNotNull(CharacterEngine.image(instrumentation.targetContext, recipe)).asAndroidBitmap()
            assertEquals(48, image.width); assertEquals(48, image.height)
            val rgba = ByteArray(48 * 48 * 4)
            for (y in 0 until 48) for (x in 0 until 48) {
                val pixel = image.getPixel(x, y)
                val at = (y * 48 + x) * 4
                rgba[at] = (pixel ushr 16).toByte(); rgba[at+1] = (pixel ushr 8).toByte()
                rgba[at+2] = pixel.toByte(); rgba[at+3] = (pixel ushr 24).toByte()
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(rgba).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(fixture.getString("rgba_sha256"), digest)
        }
    }
    @Test fun familiarChoicePreservesAuthoredPixels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Cast.all.forEach { member ->
            val original = requireNotNull(CharacterEngine.authoredImage(context, member))
            assertEquals(48, original.width); assertEquals(48, original.height)
            val changed = member.copy(companion = "mimic", companionName = "Bite")
            assertTrue(CharacterEngine.usesAuthored(changed))
            assertSame(original, CharacterEngine.authoredImage(context, changed))
            assertFalse(CharacterEngine.usesAuthored(member.copy(recipe = member.recipe!!.copy(detail = "scar"))))
        }
    }
}

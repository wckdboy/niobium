package io.sovware.niobium

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.compose.setContent
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

    @Test fun disablingPreviewMotionImmediatelyRestoresTheOpenFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo", true)) as MainActivity
        val enabled = androidx.compose.runtime.mutableStateOf(true)
        val observed = java.util.concurrent.atomic.AtomicBoolean(false)
        val recipe = CharacterRecipe.generated("nova", "motion-preview")
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    val blinking = portraitBlink(recipe,enabled.value)
                    androidx.compose.runtime.SideEffect { observed.set(blinking) }
                    androidx.compose.material3.Text(if (blinking) "Closed" else "Open")
                }
            }
            compose.waitUntil(timeoutMillis = 7500) { observed.get() }
            instrumentation.runOnMainSync { enabled.value = false }
            compose.waitUntil(timeoutMillis = 1500) { !observed.get() }
            assertFalse(observed.get())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun workshopKeepsThePortraitThroughRemixUndoAndCreatureSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo", true).putExtra("tab", "pixel-review"))
        try {
            compose.onNodeWithTag("look.nyx").assertIsSelected()
            val color = compose.onNodeWithTag("look.color.outfit")
            val original = color.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            compose.onNodeWithTag("look.remix").performScrollTo().performClick()
            compose.onNodeWithTag("look.nyx").assertIsSelected()
            assertNotEquals(original, color.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
            compose.onNodeWithTag("look.undo").performScrollTo().performClick()
            assertEquals(original, color.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
            compose.onNodeWithTag("builder.tab.creature").performScrollTo().performClick()
            compose.onNodeWithTag("familiar.mimic").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("builder.tab.look").performScrollTo().performClick()
            compose.onNodeWithTag("look.nyx").assertIsSelected()
            assertEquals(original, color.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
            compose.onNodeWithText("Create Nyx").assertIsDisplayed()
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun authoredLooksMatchSharedPixelsAndSurviveAppearanceRoundTrip() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixtures = JSONArray(instrumentation.context.assets.open("portrait-looks.json").bufferedReader().use { it.readText() })
        for (i in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(i)
            val look = PixelLook.parse(fixture.getJSONObject("look"))
            val rendered = requireNotNull(CharacterEngine.image(context, look))
            val image = rendered.asAndroidBitmap()
            assertEquals(48, image.width); assertEquals(48, image.height)
            val rgba = ByteArray(48 * 48 * 4)
            for (y in 0 until 48) for (x in 0 until 48) {
                val p = image.getPixel(x, y); val at = (y * 48 + x) * 4
                rgba[at] = (p ushr 16).toByte(); rgba[at+1] = (p ushr 8).toByte()
                rgba[at+2] = p.toByte(); rgba[at+3] = (p ushr 24).toByte()
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(rgba).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(fixture.getString("rgba_sha256"), digest)
            assertSame(rendered, CharacterEngine.image(context, look))
        }
        val profile = "dsh:test-look-" + java.util.UUID.randomUUID()
        val old = Cast.all.first()
        val custom = old.copy(look = PixelLook("nyx", "astra"), companion = "mimic", companionName = "Bite")
        CastStore.put(context, profile, custom)
        assertEquals(custom, CastStore.get(context, profile))
        val metadata = org.json.JSONObject().put("appearance", org.json.JSONObject()
            .put("archetype", old.id).put("look", org.json.JSONObject.NULL))
        CastStore.restore(context, profile, metadata)
        assertNull(CastStore.get(context, profile).look)
    }
    @Test fun nativePixelsMatchTheSharedRgbaFixtures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtures = JSONArray(instrumentation.context.assets.open("character-recipes.json").bufferedReader().use { it.readText() })
        for (i in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(i)
            val recipe = CharacterRecipe.generated(fixture.getString("archetype"), fixture.getString("seed"))
            for (blink in listOf(false,true)) {
            val rendered = requireNotNull(CharacterEngine.image(instrumentation.targetContext, recipe, blink))
            val image = rendered.asAndroidBitmap()
            assertEquals(48, image.width); assertEquals(48, image.height)
            val rgba = ByteArray(48 * 48 * 4)
            for (y in 0 until 48) for (x in 0 until 48) {
                val pixel = image.getPixel(x, y)
                val at = (y * 48 + x) * 4
                rgba[at] = (pixel ushr 16).toByte(); rgba[at+1] = (pixel ushr 8).toByte()
                rgba[at+2] = pixel.toByte(); rgba[at+3] = (pixel ushr 24).toByte()
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(rgba).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(fixture.getString(if (blink) "blink_rgba_sha256" else "rgba_sha256"), digest)
            assertSame(rendered, CharacterEngine.image(instrumentation.targetContext, recipe, blink))
            }
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

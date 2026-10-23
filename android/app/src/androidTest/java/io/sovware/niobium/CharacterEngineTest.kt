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
            compose.onNodeWithTag("look.nyx").performScrollTo().performClick().assertIsSelected()
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
    @Test fun wardrobeFiltersNewLooksAndKeepsAiOptional() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo",true).putExtra("tab","pixel-review"))
        try {
            compose.onNodeWithTag("look.browse").performScrollTo().performClick()
            compose.onNodeWithTag("wardrobe.family.dark").performClick()
            compose.onNodeWithTag("look.lilith").performScrollTo().performClick()
            compose.onNodeWithText("Create Nyx").assertIsDisplayed()
            compose.onNodeWithTag("artist.open.pixel").performScrollTo().performClick()
            compose.onNodeWithTag("artist.generate").performScrollTo().assertIsNotEnabled()
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun uncertainRoomReceiptsSurviveRecreationWithoutStoringMessages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "room-receipts-tests-" + java.util.UUID.randomUUID()
        try {
            val first = RoomReceiptBook(context,namespace)
            val id = first.reserve("dsh","a","Keep the draft")
            val restored = RoomReceiptBook(context,namespace)
            assertEquals(id,restored.reserve("dsh","a","Keep the draft"))
            assertNotEquals(id,restored.reserve("hermes","a","Keep the draft"))
            assertNotEquals(id,restored.reserve("dsh","b","Keep the draft"))
            restored.acknowledge(setOf(id))
            assertNotEquals(id,restored.reserve("dsh","a","Keep the draft"))
            assertFalse(context.getSharedPreferences(namespace,android.content.Context.MODE_PRIVATE).all.toString().contains("Keep the draft"))
        } finally { context.deleteSharedPreferences(namespace) }
    }
    @Test fun changingTheCastPreservesCustomCompanionArtWhenSaving() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val profile = "studio-art-test-" + java.util.UUID.randomUUID()
        val fixture = JSONArray(instrumentation.context.assets.open("custom-art.json").bufferedReader().use { it.readText() })
        val art = (0 until fixture.length()).map { PixelArt.parse(fixture.getJSONObject(it).getJSONObject("art")) }.first { it.size == 16 }
        val initial = Cast.all.first().copy(companion = "mimic", companionName = "Bite", companionArt = art)
        CastStore.put(context,profile,initial)
        val activity = instrumentation.startActivitySync(android.content.Intent(context,MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo",true)) as MainActivity
        val vmStore = androidx.lifecycle.ViewModelStore()
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            instrumentation.runOnMainSync {
                val vm = NiobiumViewModel(activity.application)
                vmStore.put("studio-test",vm)
                vm.bootstrap(demoRequested = true)
                activity.setContent { NiobiumTheme { CastStudioSheet(vm,profile,"Studio test") { finished.set(true) } } }
            }
            compose.onNodeWithTag("studio.content").performScrollToNode(hasTestTag("studio.cast.nova"))
            compose.onNodeWithTag("studio.cast.nova").performClick()
            compose.onNodeWithTag("studio.content").performScrollToNode(hasText("Use this character"))
            compose.onNodeWithText("Use this character").performClick()
            compose.waitUntil(3000) { finished.get() }
            val saved = CastStore.get(context,profile)
            assertEquals("nova",saved.id);assertEquals("mimic",saved.companion)
            assertEquals("Bite",saved.companionName);assertEquals(art,saved.companionArt)
        } finally {
            instrumentation.runOnMainSync { vmStore.clear();activity.finish() }
            for (namespace in listOf("niobium_cast","niobium_portraits","niobium_companions")) {
                val prefs = context.getSharedPreferences(namespace,android.content.Context.MODE_PRIVATE)
                val edit = prefs.edit()
                prefs.all.keys.filter { it == profile || it.endsWith(".$profile") }.forEach { edit.remove(it) }
                edit.commit()
            }
        }
    }
    @Test fun imageProviderKeyUsesTheActualDeviceKeystore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "niobium.artist.tests." + java.util.UUID.randomUUID()
        val secure = SecurePrefs(context,namespace,namespace)
        val marker = "fixture-key-for-device-vault-only"
        try {
            secure.saveImageKey(marker)
            assertEquals(marker,secure.loadImageKey())
            val raw = context.getSharedPreferences(namespace,android.content.Context.MODE_PRIVATE).getString("image_key",null)
            assertNotNull(raw);assertFalse(raw!!.contains(marker))
            secure.saveImageKey(null);assertNull(secure.loadImageKey())
        } finally {
            context.deleteSharedPreferences(namespace)
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null);deleteEntry(namespace) }
        }
    }
    @Test fun generatedArtRendersPortablePixelsAndBakerRejectsOpaqueBackgrounds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtures = JSONArray(instrumentation.context.assets.open("custom-art.json").bufferedReader().use { it.readText() })
        for (i in 0 until fixtures.length()) {
            val f = fixtures.getJSONObject(i)
            val art = PixelArt.parse(f.getJSONObject("art"))
            val rendered = requireNotNull(CharacterEngine.image(art)).asAndroidBitmap()
            assertEquals(art.size,rendered.width)
            val rgba = ByteArray(art.size*art.size*4)
            for (y in 0 until art.size) for (x in 0 until art.size) {
                val c = rendered.getPixel(x,y);val offset=(y*art.size+x)*4
                rgba[offset]=(c ushr 16).toByte();rgba[offset+1]=(c ushr 8).toByte();rgba[offset+2]=c.toByte();rgba[offset+3]=(c ushr 24).toByte()
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(rgba).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(f.getString("rgba_sha256"),hash)
            val bytes = java.io.ByteArrayOutputStream().use { rendered.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);it.toByteArray() }
            assertTrue(PixelArtBaker.bake(bytes,art.size).valid)
        }
        val opaque = android.graphics.Bitmap.createBitmap(48,48,android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        val bytes = java.io.ByteArrayOutputStream().use { opaque.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);it.toByteArray() }
        assertTrue(runCatching { PixelArtBaker.bake(bytes,16) }.isFailure)
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

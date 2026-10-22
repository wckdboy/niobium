package io.sovware.niobium

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class CharacterEngineTest {
    @Test fun workshopKeepsPinnedBuildAndSelectsNewCompanions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo", true).putExtra("tab", "pixel-review"))
        fun nodes(node: android.view.accessibility.AccessibilityNodeInfo?): List<android.view.accessibility.AccessibilityNodeInfo> =
            if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
        fun find(text: String) = nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {
            it.isVisibleToUser && (it.text?.toString()?.startsWith(text) == true || it.contentDescription?.toString() == text)
        }
        fun click(text: String) {
            repeat(40) {
                var node = find(text)
                repeat(6) {
                    if (node?.isClickable == true && node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true) {
                        instrumentation.waitForIdleSync(); Thread.sleep(200); return
                    }
                    node = node?.parent
                }
                nodes(instrumentation.uiAutomation.rootInActiveWindow).filter { it.isScrollable }.forEach {
                    it.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                }
                Thread.sleep(150)
            }
            fail("Missing workshop control: " + text)
        }
        try {
            click("Appearance & companion")
            assertNotNull(find("Feminine"))
            click("Remix look")
            assertNotNull("Pinned build changed during remix", find("Feminine"))
            click("Mimic companion")
            val until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3)
            // The label can be a child view; selection belongs to its radio ancestor.
            fun selected() = generateSequence(find("Mimic companion")) { it.parent }.take(6).any { it.isChecked || it.isSelected }
            while (System.nanoTime() < until && !selected()) Thread.sleep(100)
            assertTrue("Mimic selection was not retained: " + generateSequence(find("Mimic companion")) { it.parent }
                .take(6).joinToString { it.className.toString() + "/" + it.isChecked + "/" + it.isSelected }, selected())
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

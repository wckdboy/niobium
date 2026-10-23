package ai.wckd.niobium.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.sovware.niobium.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class DesktopUsabilityTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val directory = Files.createTempDirectory("niobium-usability-")
    private val credentials = DesktopCredentials(scope, FakeVault(), directory.resolve("connections.json"))
    private val hermes = DesktopAgent(false, scope, RoomOutbox(directory.resolve("receipts.json")))
    private val dsh = DesktopAgent(true, scope, RoomOutbox(directory.resolve("local-receipts.json")))
    private val runtime = LocalEngine(scope, directory.resolve("home"), directory.resolve("workspace"))

    @After fun close() = runBlocking {
        withContext(Dispatchers.Main) { hermes.disconnect(); dsh.disconnect(); credentials.close(); runtime.close(); scope.cancel() }
        Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
    private fun focus(node: SemanticsNodeInteraction) { node.performSemanticsAction(SemanticsActions.RequestFocus) { it() } }
    private fun screenshot(name: String) {
        Files.createDirectories(Path.of("build/evidence"))
        check(ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", Path.of("build/evidence/$name.png").toFile()))
    }

    @Test fun pixelActionsShowFocusAndSupportKeyboardActivationWithoutDisabledStops() {
        var activated = 0
        compose.setContent { DesktopTheme { Column {
            PixelButton("First") { activated++ }
            PixelButton("Unavailable", enabled = false) { error("Disabled action ran") }
            PixelButton("Last") { activated += 10 }
        } } }
        val first = compose.onNodeWithText("First")
        focus(first); first.assertIsFocused()
        val pixels = first.captureToImage().toPixelMap()
        val outline = pixels[pixels.width / 2, 0]
        assertTrue("Keyboard focus needs a visible dark outline", outline.red < .25f && outline.green < .2f && outline.blue < .35f)
        screenshot("keyboard-focus")
        first.performKeyInput { pressKey(Key.Enter); pressKey(Key.Tab) }
        compose.onNodeWithText("Last").assertIsFocused().performKeyInput { pressKey(Key.Spacebar) }
        compose.runOnIdle { assertEquals(11, activated) }
    }

    @Test fun keyboardNavigationReportsTheSelectedPageAndEngineWithoutStartingDsh() {
        compose.setContent { DesktopTheme { NiobiumApp(hermes, dsh, runtime, credentials) } }
        compose.onNodeWithTag("nav.Pixels").assertIsSelected()
        focus(compose.onNodeWithTag("nav.Pixels"))
        compose.onNodeWithTag("nav.Pixels").performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithTag("nav.Rooms").assertIsFocused().assertIsNotSelected().performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("nav.Rooms").assertIsSelected()
        compose.onNodeWithTag("engine.Hermes").assertIsSelected()
        focus(compose.onNodeWithTag("engine.Local DSH"))
        compose.onNodeWithTag("engine.Local DSH").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("engine.Local DSH").assertIsSelected()
        compose.runOnIdle { assertEquals("Stopped", runtime.state.value.phase); assertFalse(dsh.state.value.connected) }
    }

    @Test fun permissionLabelsAreSingleKeyboardToggleTargets() {
        compose.setContent { DesktopTheme { PixelCreator(hermes, DesktopArtist(scope, receipt = directory.resolve("image.json")), credentials, false) {} } }
        compose.onNodeWithText("Access", useUnmergedTree = true).performClick()
        val shell = compose.onNode(hasText("Shell commands") and isToggleable())
        shell.assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        focus(shell); shell.performKeyInput { pressKey(Key.Spacebar) }; shell.assertIsOn()
        compose.onAllNodes(hasText("Shell commands") and isToggleable()).assertCountEquals(1)
    }

    @Test fun engineSetupStacksForLargeTextAndItsLowerActionsRemainReachable() {
        compose.setContent { DesktopTextScale(150) { DesktopTheme { Box(Modifier.width(680.dp).height(620.dp)) {
            EnginesPanel(hermes, dsh, runtime, credentials) {}
        } } } }
        compose.onNodeWithTag("engines.stacked").assertExists()
        compose.onNodeWithTag("hermes.address").assertWidthIsAtLeast(400.dp)
        compose.onNodeWithText("Start local DSH").performScrollTo().assertIsDisplayed()
        screenshot("engines-large-text")
        compose.runOnIdle { assertEquals("Stopped", runtime.state.value.phase) }
    }

    @Test fun hiDpiLargeTextKeepsSetupActionsReachable() {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(2f)) {
            DesktopTextScale(150) { DesktopTheme { EnginesPanel(hermes, dsh, runtime, credentials) {} } }
        } }
        compose.onNodeWithTag("engines.stacked").assertExists()
        compose.onNodeWithText("Start local DSH").performScrollTo().assertIsDisplayed()
        screenshot("engines-hidpi")
    }

    @Test fun wideSetupKeepsBothEnginePanelsSideBySide() {
        compose.setContent { DesktopTheme { EnginesPanel(hermes, dsh, runtime, credentials) {} } }
        compose.onNodeWithTag("engines.columns").assertExists()
        compose.onNodeWithText("Start local DSH").assertIsDisplayed()
        compose.onNodeWithTag("hermes.address").assertIsDisplayed()
    }

    @Test fun largeTextWorkshopKeepsAccessAndItsFooterReachable() {
        compose.setContent { DesktopTextScale(150) { DesktopTheme {
            PixelCreator(hermes, DesktopArtist(scope, receipt = directory.resolve("image.json")), credentials, false) {}
        } } }
        compose.onNodeWithText("Access", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNode(hasText("Shell commands") and isToggleable()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Create Pixel").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        val root = compose.onNode(isRoot() and hasAnyDescendant(hasText("Make good company.")))
        Files.createDirectories(Path.of("build/evidence"))
        check(ImageIO.write(root.captureToImage().toAwtImage(), "png", Path.of("build/evidence/workshop-large-text.png").toFile()))
    }

    @Test fun workshopEscapeReturnsToItsKeyboardOpener() {
        compose.setContent { DesktopTheme { NiobiumApp(hermes, dsh, runtime, credentials) } }
        val opener = compose.onNodeWithText("New Pixel")
        focus(opener); opener.performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Make good company.").assertIsDisplayed()
        focus(compose.onNodeWithText("Close"))
        compose.onNodeWithText("Close").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText("Make good company.").assertDoesNotExist()
        opener.assertIsFocused()
        compose.onNodeWithTag("nav.Wardrobe").performClick()
        val look = compose.onNodeWithText("Lilith")
        focus(look); look.performKeyInput { pressKey(Key.Enter) }
        focus(compose.onNodeWithText("Close"))
        compose.onNodeWithText("Close").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("nav.Wardrobe").assertIsSelected()
        look.assertIsFocused()
    }

    @Test fun streamingDoesNotMoveAReaderInALargeHistoryAndLatestRestoresFollowing() {
        var rows by mutableStateOf((0 until 2000).map { ChatMessage("m$it", "assistant", "History $it") })
        compose.setContent { DesktopTheme { ChatTimeline(rows, "Nova", false, false, Modifier.width(680.dp).height(480.dp)) } }
        compose.onNodeWithText("History 1999").assertIsDisplayed()
        compose.onNodeWithTag("chat.history").performScrollToIndex(600)
        compose.onNodeWithText("History 600").assertIsDisplayed()
        compose.runOnIdle { rows = rows.dropLast(1) + rows.last().copy(text = "New streamed reply", streaming = true) }
        compose.onNodeWithText("History 600").assertIsDisplayed()
        compose.onNodeWithTag("chat.latest").assertIsDisplayed().performClick()
        compose.onNodeWithText("New streamed reply").assertIsDisplayed()
        compose.onNodeWithTag("chat.latest").assertDoesNotExist()
        compose.runOnIdle { rows = rows + ChatMessage("new", "assistant", "Followed reply") }
        compose.onNodeWithText("Followed reply").assertIsDisplayed()
        screenshot("large-history-following")
    }

    @Test fun followingReachesTheEndOfAReplyTallerThanTheViewport() {
        var rows by mutableStateOf(listOf(ChatMessage("long", "assistant", (0..100).joinToString("\n") { "Line $it" })))
        compose.setContent { DesktopTheme { ChatTimeline(rows, "Nova", false, false, Modifier.width(680.dp).height(300.dp)) } }
        fun atBottom() = compose.onNodeWithTag("chat.history").assert(SemanticsMatcher("history is at its bottom") {
            val range = it.config[SemanticsProperties.VerticalScrollAxisRange]; range.value() == range.maxValue()
        })
        atBottom()
        compose.runOnIdle { rows = listOf(rows.single().copy(text = rows.single().text + "\nThe newest line")) }
        atBottom()
    }

    @Test fun wideningTheHistoryAtTheBottomKeepsFollowingEnabled() {
        var width by mutableStateOf(360.dp)
        val rows = listOf(ChatMessage("long", "assistant", List(100) { "A longer thought that wraps across the available conversation width." }.joinToString(" ")))
        compose.setContent { DesktopTheme { ChatTimeline(rows, "Nova", false, false, Modifier.width(width).height(300.dp)) } }
        compose.onNodeWithTag("chat.latest").assertDoesNotExist()
        compose.runOnIdle { width = 720.dp }
        compose.onNodeWithTag("chat.latest").assertDoesNotExist()
    }

    @Test fun incomingRepliesDoNotTakeFocusOrReplaceAnUnsentDraft() {
        val bot = BotProfile.parse(JSONObject().put("name", "nova"))!!
        var state by mutableStateOf(DesktopState(connected = true, bot = bot, session = "chat", messages = listOf(ChatMessage("reply", "assistant", "Hello"))))
        compose.setContent { DesktopTheme { ChatPanel(hermes, state, Modifier.width(760.dp).height(600.dp)) } }
        val input = compose.onNodeWithTag("chat.input")
        input.performTextInput("An unfinished thought"); input.assertIsFocused()
        compose.runOnIdle { state = state.copy(messages = listOf(ChatMessage("reply", "assistant", "A longer incoming reply", streaming = true))) }
        input.assertIsFocused().assertTextContains("An unfinished thought")
        input.assertContentDescriptionEquals("Message Nova")
    }
}

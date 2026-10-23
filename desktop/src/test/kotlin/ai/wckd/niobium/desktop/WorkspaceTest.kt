package ai.wckd.niobium.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.toAwtImage
import kotlinx.coroutines.*
import org.junit.*
import java.io.File
import javax.imageio.ImageIO

class WorkspaceTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private fun open() {
        val hermes = DesktopAgent(false, scope); val dsh = DesktopAgent(true, scope); val runtime = LocalEngine(scope)
        compose.setContent { DesktopTheme { NiobiumApp(hermes, dsh, runtime) } }
    }
    @After fun close() { scope.cancel() }
    @Test fun desktopStartsWithoutBootingDshAndNavigatesTheWardrobe() {
        open()
        compose.onNodeWithText("Good company.\nSerious capability.").assertIsDisplayed()
        compose.onNodeWithText("Wardrobe", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Lilith").assertExists()
        compose.onNodeWithTag("wardrobe.filter.Cyber").performClick()
        compose.onNodeWithText("Zero").assertExists()
        compose.onNodeWithText("Lilith").assertDoesNotExist()
        File("build/evidence").mkdirs()
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File("build/evidence/desktop-wardrobe.png"))
    }
    @Test fun workshopHasInstantLooksAndExplicitAiAndAccessControls() {
        open()
        compose.onNodeWithText("New Pixel").performClick()
        compose.onNodeWithText("Make good company.").assertIsDisplayed()
        compose.onNodeWithText("Design with AI").assertExists()
        compose.onNodeWithText("Companion", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Design a creature with AI").assertExists()
        compose.onNodeWithText("Access", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Shell commands").assertExists()
        compose.onNodeWithText("Create Pixel").assertIsNotEnabled()
    }
}

package ai.wckd.niobium.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class LocalProviderEditorTest {
    @get:Rule val compose = createComposeRule()
    private fun provider(id: String, configured: Boolean = false) = LocalProvider(id,
        if (id == "niobium-compatible") "Compatible endpoint" else id.replaceFirstChar { it.uppercase() },
        configured, true, configured, true, id != "niobium-compatible", configured, false, "", 4,
        if (id == "niobium-compatible") emptyList() else listOf(ProviderModel("model-a", "Fixture Swift"), ProviderModel("model-b", "Fixture Reasoner")), "", 8192, 1024)
    private fun editor(row: LocalProvider, defaultConfigured: Boolean = false, onSave: (JSONObject) -> Unit) {
        val catalog = LocalProviders(listOf(row, provider("deepseek-official", defaultConfigured)), "deepseek-official", "model-a", 8, true)
        compose.setContent { DesktopTheme { ProviderEditor(row, catalog, false, null, {}, {}, onSave) } }
    }
    @Test fun firstProviderRequiresAnExplicitModelAndMasksItsKey() {
        var saved: JSONObject? = null
        editor(provider("openai")) { saved = it }
        compose.onNodeWithTag("provider.save").assertIsNotEnabled()
        compose.onNodeWithTag("provider.key").performTextInput("fixture-key-only")
        compose.onNodeWithTag("provider.key").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithTag("provider.save").assertIsNotEnabled()
        compose.onNodeWithTag("provider.model").performClick()
        compose.onNodeWithText("Fixture Swift").performClick()
        compose.onNodeWithTag("provider.save").assertIsEnabled().assertIsDisplayed()
        File("build/evidence").mkdirs()
        val root = compose.onNode(isRoot() and hasAnyDescendant(hasTestTag("provider.save")))
        ImageIO.write(root.captureToImage().toAwtImage(), "png", File("build/evidence/provider-editor.png"))
        compose.onNodeWithTag("provider.save").performClick()
        compose.runOnIdle {
            assertEquals("model-a", saved!!.getString("model")); assertTrue(saved!!.getBoolean("make_default"))
            assertEquals(4, saved!!.getInt("expected_revision")); assertEquals(8, saved!!.getInt("expected_default_revision"))
        }
    }
    @Test fun addingAProviderPreservesAnAlreadyConfiguredDefault() {
        var saved: JSONObject? = null
        editor(provider("anthropic"), true) { saved = it }
        compose.onNodeWithTag("provider.default").assertIsOff()
        compose.onNodeWithTag("provider.key").performTextInput("fixture-key-only")
        compose.onNodeWithTag("provider.save").assertIsEnabled().performClick()
        compose.runOnIdle { assertFalse(saved!!.getBoolean("make_default")); assertFalse(saved!!.has("model")); assertFalse(saved!!.has("expected_default_revision")) }
    }
    @Test fun compatibleSetupHasAnOptionalKeyAndExplicitModelLimits() {
        var saved: JSONObject? = null
        editor(provider("niobium-compatible")) { saved = it }
        compose.onNodeWithTag("provider.endpoint").performTextInput("http://127.0.0.1:9000/v1")
        compose.onNodeWithTag("provider.custom-model").performTextInput("my-local-model")
        compose.onNodeWithTag("provider.save").assertIsEnabled().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertFalse(saved!!.has("key")); assertEquals("my-local-model", saved!!.getString("model"))
            assertEquals(8192, saved!!.getInt("context_window")); assertEquals(1024, saved!!.getInt("max_tokens"))
        }
    }
}

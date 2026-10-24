package io.sovware.niobium

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class AndroidClientTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var activity: MainActivity? = null
    private fun content(body: @Composable () -> Unit) {
        activity = instrumentation.startActivitySync(android.content.Intent(context,MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo",true)) as MainActivity
        instrumentation.runOnMainSync { activity!!.setContent { NiobiumTheme { body() } } }
    }
    @After fun close() { instrumentation.runOnMainSync { activity?.finish() } }

    @Test fun streamedMessagesPreserveOlderReadingAndLatestResumesFollowing() {
        var rows by mutableStateOf((0 until 500).map { it to "History $it" })
        content { ConversationHistory(rows.last().first,rows.size,Modifier.fillMaxSize()) { _ -> items(rows,key={it.first}) { Text(it.second,Modifier.padding(20.dp),color=Nb.Text) } } }
        compose.onNodeWithText("History 499").assertIsDisplayed()
        compose.onNodeWithTag("conversation.history").performScrollToIndex(100)
        compose.onNodeWithText("History 100").assertIsDisplayed()
        compose.runOnIdle { rows=rows+ (500 to "Latest reply") }
        compose.onNodeWithText("History 100").assertIsDisplayed()
        compose.onNodeWithTag("conversation.latest").performClick()
        compose.onNodeWithText("Latest reply").assertIsDisplayed()
        compose.onNodeWithTag("conversation.latest").assertDoesNotExist()
    }
    @Test fun tallRepliesStayAtTheirEndWhenTextGrowsOrWidthChanges() {
        var text by mutableStateOf(List(100){"A thought that wraps in the conversation."}.joinToString(" "))
        var width by mutableStateOf(220.dp)
        content { ConversationHistory("reply",1,Modifier.width(width).height(350.dp)) { _ -> item { Text(text,color=Nb.Text) } } }
        fun bottom() = compose.onNodeWithTag("conversation.history").assert(SemanticsMatcher("history is at the end") {
            val r=it.config[SemanticsProperties.VerticalScrollAxisRange];r.value()==r.maxValue()
        })
        bottom();compose.runOnIdle {text += " A new sentence at the very end."};bottom()
        compose.runOnIdle {width=350.dp};bottom();compose.onNodeWithTag("conversation.latest").assertDoesNotExist()
    }
    @Test fun restoringSavedUiStateKeepsAnOlderReadingPositionAndFollowingPaused() {
        var shown by mutableStateOf(true)
        var registry by mutableStateOf(androidx.compose.runtime.saveable.SaveableStateRegistry(null) {true})
        content {
            if (shown) CompositionLocalProvider(androidx.compose.runtime.saveable.LocalSaveableStateRegistry provides registry) {
                ConversationHistory(499,500,Modifier.fillMaxSize()) { _ ->
                    items((0 until 500).toList(),key={it}) {Text("Saved history $it",Modifier.padding(20.dp),color=Nb.Text)}
                }
            }
        }
        compose.onNodeWithTag("conversation.history").performScrollToIndex(100)
        compose.onNodeWithTag("conversation.latest").assertIsDisplayed()
        val saved=compose.runOnIdle {registry.performSave()}
        compose.runOnIdle {shown=false}
        compose.runOnIdle {registry=androidx.compose.runtime.saveable.SaveableStateRegistry(saved) {true};shown=true}
        compose.onNodeWithText("Saved history 100").assertIsDisplayed()
        compose.onNodeWithTag("conversation.latest").assertIsDisplayed()
        compose.onNodeWithTag("conversation.latest").performClick()
        compose.onNodeWithText("Saved history 499").assertIsDisplayed()
    }
    @Test fun photosAreResizedAndEncodedWithoutRetainingTheLargeBitmap() = runBlocking {
        val file=File(context.cacheDir,"prepared-photo.jpg")
        try {
            val image=Bitmap.createBitmap(4096,2048,Bitmap.Config.ARGB_8888);image.eraseColor(Color.CYAN)
            file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG,95,it) };image.recycle()
            val photo=PhotoAttachments.prepare(context.contentResolver,Uri.fromFile(file))
            assertEquals(2048,photo.width);assertEquals(1024,photo.height);assertEquals("image/jpeg",photo.mime)
            assertTrue(photo.preview.width<=160);assertTrue(photo.preview.height<=160)
            assertTrue(android.util.Base64.decode(photo.data,android.util.Base64.NO_WRAP).size<=PhotoAttachments.MAX_OUTPUT)
            assertFalse(photo.toString().contains(photo.data))
        } finally {file.delete()}
    }
    @Test fun photoPreparationHonorsExifRotationAndTransparency() = runBlocking {
        val file=File(context.cacheDir,"oriented-photo.jpg");val alpha=File(context.cacheDir,"alpha-photo.png")
        try {
            val image=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888)
            file.outputStream().use {image.compress(Bitmap.CompressFormat.JPEG,90,it)}
            val exif=android.media.ExifInterface(file.path);exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes()
            val rotated=PhotoAttachments.prepare(context.contentResolver,Uri.fromFile(file));assertEquals(20,rotated.width);assertEquals(40,rotated.height)
            image.eraseColor(Color.TRANSPARENT);alpha.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            val transparent=PhotoAttachments.prepare(context.contentResolver,Uri.fromFile(alpha));assertEquals("image/png",transparent.mime)
            assertEquals(0,Color.alpha(transparent.preview.getPixel(0,0)))
        } finally {file.delete();alpha.delete()}
    }
    @Test fun invalidPhotosFailWithoutProducingAnAttachment() = runBlocking {
        val file=File(context.cacheDir,"invalid-photo.jpg")
        try {
            file.writeBytes(byteArrayOf(0,1,2,3))
            assertTrue(runCatching {PhotoAttachments.prepare(context.contentResolver,Uri.fromFile(file))}.isFailure)
        } finally {file.delete()}
    }
}

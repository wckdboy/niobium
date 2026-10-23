package io.sovware.niobium

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class PixelArtist private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("niobium.artist", Context.MODE_PRIVATE)
    private val secure = SecurePrefs(context)
    private val wire = PixelArtistWire()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var task: Job? = null
    private var generation = 0
    private var foreground = true
    var job by mutableStateOf(prefs.getString("job", null)?.let { runCatching { PixelArtJob.parse(JSONObject(it)) }.getOrNull() })
        private set
    var hasKey by mutableStateOf(!secure.loadImageKey().isNullOrEmpty())
        private set
    val busy get() = job?.phase in listOf("submitting", "queued", "in_progress")
    init {
        if (job?.phase == "submitting" && job?.responseId == null) {
            job = job?.copy(phase = "unknown", message = "The request was interrupted before its receipt arrived. Check provider usage before starting another design.")
            persist()
        }
    }
    private fun persist() { prefs.edit().putString("job", job?.toJson()?.toString()).apply() }
    fun saveKey(key: String): Boolean {
        val clean = key.trim()
        if (clean.length !in 20..512 || clean.any { it.isWhitespace() }) return false
        return runCatching { secure.saveImageKey(clean); hasKey = true; resume(); true }.getOrDefault(false)
    }
    fun removeKey() { if (!busy) { secure.saveImageKey(null); hasKey = false } }
    fun start(prompt: String, target: String, referencePng: ByteArray?) {
        if (busy || !hasKey || target !in listOf("pixel", "companion") || prompt.trim().length !in 1..2000) return
        task?.cancel(); val epoch = ++generation
        job = PixelArtJob(target, "submitting"); persist()
        task = scope.launch {
            try { consume(wire.request(secure.loadImageKey() ?: "", "POST", "", PixelArtistWire.body(prompt, target, referencePng))) }
            catch (_: CancellationException) { }
            catch (e: Exception) {
                if (epoch == generation) {
                    val code = (e as? ArtistHttpError)?.code ?: 0
                    val known = job?.responseId != null || (code in 400..499 && code != 408)
                    job = job?.copy(phase = if (known) "failed" else "unknown", message = if (known) e.message else "The receipt did not arrive. Check provider usage before starting another design; it may still be running."); persist()
                }
            } finally { if (epoch == generation) { task = null; if (foreground && busy && job?.responseId != null) resume() } }
        }
    }
    fun pausePolling() { foreground = false; if (job?.responseId != null && busy) { generation++; task?.cancel(); task = null } }
    fun resume() {
        foreground = true
        val id = job?.responseId ?: return
        if (task != null || !busy || !hasKey || !PixelArtistWire.validId(id)) return
        val epoch = ++generation
        task = scope.launch {
            try {
                while (isActive && busy) {
                    delay(3000)
                    try { consume(wire.request(secure.loadImageKey() ?: "", "GET", "/$id")) }
                    catch (_: CancellationException) { return@launch }
                    catch (e: Exception) {
                        if (epoch != generation) return@launch
                        job = job?.copy(phase = if (e is ArtistHttpError && e.code < 500 && e.code != 429) "failed" else job!!.phase,
                            message = if (e is ArtistHttpError) e.message else "Waiting for a connection. Your saved design receipt is safe."); persist()
                    }
                }
            } finally { if (epoch == generation) task = null }
        }
    }
    fun cancel() {
        val id = job?.responseId ?: return
        if (!busy || !PixelArtistWire.validId(id)) return
        pausePolling(); val epoch = ++generation
        task = scope.launch {
            try { consume(wire.request(secure.loadImageKey() ?: "", "POST", "/$id/cancel", JSONObject())) }
            catch (_: CancellationException) { }
            catch (_: Exception) { if (epoch == generation) { job = job?.copy(message = "Cancellation wasn’t confirmed. Resume to check the design’s status."); persist() } }
            finally { if (epoch == generation) task = null }
        }
    }
    private suspend fun consume(value: JSONObject) {
        currentCoroutineContext().ensureActive()
        val id = value.s("id") ?: throw ArtistHttpError(0)
        val phase = value.s("status") ?: throw ArtistHttpError(0)
        if (!PixelArtistWire.validId(id) || phase !in listOf("queued", "in_progress", "completed", "failed", "incomplete", "cancelled") || (job?.responseId != null && job?.responseId != id)) throw ArtistHttpError(0)
        job = job?.copy(responseId = id, phase = if (phase == "completed") "in_progress" else phase, message = null); persist()
        if (phase == "completed") {
            val output = value.a("output") ?: throw ArtistHttpError(0)
            val encoded = (0 until output.length()).mapNotNull { output.optJSONObject(it) }.firstOrNull { it.s("type") == "image_generation_call" }?.s("result") ?: throw ArtistHttpError(0)
            if (encoded.length > 28*1024*1024) throw ArtistHttpError(0)
            val size = if (job?.target == "companion") 16 else 48
            val art = withContext(Dispatchers.Default) { PixelArtBaker.bake(Base64.getDecoder().decode(encoded), size) }
            currentCoroutineContext().ensureActive()
            job = job?.copy(phase = "completed", candidate = art); persist()
        } else if (phase in listOf("failed", "incomplete")) { job = job?.copy(message = "The provider couldn’t finish this design. Your current art is unchanged."); persist() }
    }
    companion object {
        @Volatile private var shared: PixelArtist? = null
        fun get(context: Context): PixelArtist = shared ?: synchronized(this) { shared ?: PixelArtist(context.applicationContext).also { shared = it } }
    }
}

internal object PixelArtBaker {
    fun bake(data: ByteArray, size: Int): PixelArt {
        if (size !in listOf(16,48) || data.size > 21*1024*1024 || !data.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) throw ArtistHttpError(0)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        if (options.outWidth !in size..2048 || options.outHeight !in size..2048) throw ArtistHttpError(0)
        val image = BitmapFactory.decodeByteArray(data,0,data.size) ?: throw ArtistHttpError(0)
        val small = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = minOf((size-2).toFloat()/image.width, (size-2).toFloat()/image.height)
        val w = image.width*scale; val h = image.height*scale
        Canvas(small).drawBitmap(image, null, RectF((size-w)/2, (size-h)/2, (size+w)/2, (size+h)/2), Paint(Paint.FILTER_BITMAP_FLAG))
        image.recycle()
        val rgba = IntArray(size*size); small.getPixels(rgba,0,size,0,0,size,size); small.recycle()
        return PixelArtQuantizer.bake(rgba, size)
    }
}

@Composable
fun PixelArtistButton(target: String, reference: Bitmap? = null, onApply: (PixelArt) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PixelButton("Design with AI", PixelGlyph.SPARK, Modifier.fillMaxWidth().testTag("artist.open.$target"), secondary = true) { open = true }
    if (open) PixelArtistSheet(target, reference, onApply = { onApply(it); open = false }, onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PixelArtistSheet(target: String, reference: Bitmap?, onApply: (PixelArt) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val artist = remember { PixelArtist.get(context) }
    var prompt by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var configuring by remember { mutableStateOf(false) }
    var keyError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { artist.resume() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Void, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("AI atelier", color = Nb.Text, style = NbTitle, fontSize = 22.sp)
            Text(if (target == "companion") "A little strange. All yours." else "A look of their own.", color = Nb.Text, style = NbDisplay, fontSize = 25.sp)
            if (!artist.hasKey || configuring) {
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("OpenAI API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                PixelButton("Save key", PixelGlyph.SHIELD, Modifier.fillMaxWidth(), secondary = true, enabled = key.isNotBlank()) {
                    keyError = !artist.saveKey(key); if (!keyError) { key = ""; configuring = false }
                }
                if (keyError) Text("Couldn’t save that key securely.", color = Nb.Rose, fontSize = 12.sp)
            }
            OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth().testTag("artist.prompt"), minLines = 3, maxLines = 6,
                placeholder = { Text(if (target == "companion") "A translucent jelly fox with four eyes…" else "A moonlit corset, silver buckles, violet cloak…") })
            Text("Uses your OpenAI key. The brief and reference art go to the provider; usage charges apply. Designs are stored by the provider so you can return later.", color = Nb.Secondary, fontSize = 11.sp)
            if (artist.busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(18.dp), color = Nb.Ion, strokeWidth = 2.dp); Text(if (artist.job?.phase == "submitting") "Starting design…" else "Designing… You can close this.", color = Nb.Text, fontSize = 13.sp) }
                if (artist.job?.responseId != null) Row {
                    TextButton(onClick = artist::resume) { Text("Check status", color = Nb.Secondary) }
                    TextButton(onClick = artist::cancel) { Text("Cancel design", color = Nb.Rose) }
                }
            } else PixelButton("Generate design", PixelGlyph.SPARK, Modifier.fillMaxWidth().testTag("artist.generate"), enabled = artist.hasKey && prompt.trim().length in 1..2000) {
                val png = reference?.let { bitmap -> java.io.ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it); it.toByteArray() } }
                artist.start(prompt, target, png)
            }
            val art = artist.job?.takeIf { it.target == target }?.candidate
            val preview = remember(art) { art?.let(CharacterEngine::image) }
            if (art != null && preview != null) {
                Image(preview, "Generated design preview", Modifier.fillMaxWidth().height(192.dp).background(Nb.FillSoft, PixelButtonShape), filterQuality = FilterQuality.None)
                PixelButton("Use this design", PixelGlyph.PIXELS, Modifier.fillMaxWidth().testTag("artist.apply")) { onApply(art) }
                Text("Your persona and access stay as reviewed.", color = Nb.Secondary, fontSize = 11.sp)
            }
            artist.job?.message?.let { Text(it, color = Nb.Secondary, fontSize = 12.sp) }
            if (artist.hasKey && !artist.busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { configuring = !configuring }) { Text("Replace key", color = Nb.Secondary) }
                TextButton(onClick = artist::removeKey) { Text("Remove key", color = Nb.Secondary) }
            }
        }
    }
}

@Composable
fun CompanionArtEditor(kind: String, art: PixelArt?, onChange: (PixelArt?) -> Unit) {
    val context = LocalContext.current
    var history by remember(kind) { mutableStateOf(emptyList<PixelArt?>()) }
    val reference = remember(kind, art) {
        art?.let(CharacterEngine::image)?.let { it.asAndroidBitmap() }
            ?: BitmapFactory.decodeResource(context.resources, compRes(kind))
    }
    PixelArtistButton("companion", reference) { history = (history + listOf(art)).takeLast(12); onChange(it) }
    if (history.isNotEmpty()) PixelButton("Undo creature design", PixelGlyph.BACK, Modifier.fillMaxWidth(), secondary = true) {
        onChange(history.last()); history = history.dropLast(1)
    }
}

package ai.wckd.niobium.desktop

import io.sovware.niobium.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import javax.imageio.ImageIO

/** One persisted receipt, no automatic retry of a billable POST. Key stays in memory. */
class DesktopArtist(private val scope: CoroutineScope, private val wire: PixelArtistWire = PixelArtistWire()) {
    private val receipt = AppPaths.data.resolve("image-design.json")
    private val mutable = MutableStateFlow(runCatching { PixelArtJob.parse(JSONObject(Files.readString(receipt))) }.getOrNull()?.let {
        if (it.phase == "submitting" && it.responseId == null) it.copy(phase = "unknown", message = "The request was interrupted before its receipt arrived. Check provider usage before another design.") else it
    })
    val state = mutable.asStateFlow()
    private var key = ""
    private var task: Job? = null
    val busy get() = mutable.value?.phase in listOf("submitting", "queued", "in_progress")
    fun setKey(value: String) { require(value.trim().length in 20..512 && value.trim().none { it.isWhitespace() }); key = value.trim() }
    fun clearUnconfirmed() { if (mutable.value?.phase == "unknown") { mutable.value = null; persist() } }
    fun generate(prompt: String, target: String, reference: ByteArray) {
        if (busy || task?.isActive == true || mutable.value?.phase == "unknown") return
        require(target in listOf("pixel", "companion") && prompt.trim().length in 1..2000 && key.isNotEmpty())
        mutable.value = PixelArtJob(target, "submitting"); persist()
        task = scope.launch {
            try { consume(wire.request(key, "POST", "", PixelArtistWire.body(prompt.trim(), target, reference))); poll() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val known = mutable.value?.responseId != null || ((e as? ArtistHttpError)?.code?.let { it in 400..499 && it != 408 } == true)
                mutable.value = mutable.value?.copy(phase = if (known && mutable.value?.responseId == null) "failed" else if (known) mutable.value!!.phase else "unknown",
                    message = if (known) e.message else "No receipt arrived. Check provider usage before starting another design; it may still be running.")
                persist()
            }
        }
    }
    fun resume() {
        if (task?.isActive == true || key.isEmpty() || mutable.value?.responseId == null) return
        task = scope.launch { try { poll() } catch (e: CancellationException) { throw e } catch (_: Exception) {
            mutable.value = mutable.value?.copy(message = "Could not check the design. Your saved receipt is safe; try again later."); persist()
        } }
    }
    fun cancel() {
        val id = mutable.value?.responseId?.takeIf(PixelArtistWire::validId) ?: return
        task?.cancel()
        task = scope.launch { try { consume(wire.request(key, "POST", "/$id/cancel", JSONObject())) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.value = mutable.value?.copy(message = "Cancellation was not confirmed. Check status before another design."); persist() } }
    }
    private suspend fun poll() {
        val id = mutable.value?.responseId?.takeIf(PixelArtistWire::validId) ?: return
        while (busy) { delay(4000); consume(wire.request(key, "GET", "/$id")) }
    }
    private suspend fun consume(response: JSONObject) {
        val id = response.s("id") ?: throw ArtistHttpError(0)
        val phase = response.s("status") ?: throw ArtistHttpError(0)
        if (!PixelArtistWire.validId(id) || phase !in listOf("queued", "in_progress", "completed", "failed", "incomplete", "cancelled") || mutable.value?.responseId?.let { it != id } == true) throw ArtistHttpError(0)
        mutable.value = mutable.value?.copy(responseId = id, phase = if (phase == "completed") "in_progress" else phase, message = null); persist()
        if (phase == "completed") {
            val encoded = response.objects("output").firstOrNull { it.s("type") == "image_generation_call" }?.s("result") ?: throw ArtistHttpError(0)
            if (encoded.length > 28 * 1024 * 1024) throw ArtistHttpError(0)
            val size = if (mutable.value?.target == "companion") 16 else 48
            val art = withContext(Dispatchers.Default) { DesktopArtBaker.bake(Base64.getDecoder().decode(encoded), size) }
            mutable.value = mutable.value?.copy(phase = "completed", candidate = art); persist()
        } else if (phase in listOf("failed", "incomplete")) { mutable.value = mutable.value?.copy(message = "The provider could not finish this design. Your current art is unchanged."); persist() }
    }
    private fun persist() {
        AppPaths.privateDirectory(AppPaths.data)
        val stage = Files.createTempFile(AppPaths.data, ".design-", ".json", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        try {
            Files.writeString(stage, mutable.value?.toJson()?.toString() ?: "null")
            Files.move(stage, receipt, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(stage) }
    }
}

object DesktopArtBaker {
    fun bake(bytes: ByteArray, size: Int): PixelArt {
        if (size !in listOf(16, 48) || bytes.size > 21 * 1024 * 1024 || !bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) throw ArtistHttpError(0)
        val image = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { source ->
            val readers = ImageIO.getImageReaders(source)
            if (!readers.hasNext()) throw ArtistHttpError(0)
            val reader = readers.next()
            try {
                reader.input = source
                if (reader.getWidth(0) !in size..2048 || reader.getHeight(0) !in size..2048) throw ArtistHttpError(0)
                reader.read(0)
            } finally { reader.dispose() }
        }
        val small = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val scale = minOf((size - 2).toDouble() / image.width, (size - 2).toDouble() / image.height)
        val width = (image.width * scale).toInt().coerceAtLeast(1); val height = (image.height * scale).toInt().coerceAtLeast(1)
        val graphics = small.createGraphics()
        try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); graphics.drawImage(image, (size - width) / 2, (size - height) / 2, width, height, null) }
        finally { graphics.dispose(); image.flush() }
        return PixelArtQuantizer.bake(small.getRGB(0, 0, size, size, null, 0, size), size)
    }
}

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
import java.security.MessageDigest
import javax.imageio.ImageIO

data class ArtistCredentials(val ready: Boolean = false, val receiptMatches: Boolean = true, val legacyReceipt: Boolean = false, val savedId: String? = null)

/** One persisted receipt, no automatic retry of a billable POST. Keys are session scoped. */
class DesktopArtist(private val scope: CoroutineScope, private val wire: PixelArtistWire = PixelArtistWire(),
                    private val receipt: java.nio.file.Path = AppPaths.data.resolve("image-design.json")) {
    private val restored = runCatching { JSONObject(Files.readString(receipt)) }.getOrNull()
    private var receiptAccount = restored?.s("credential_scope")?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
    private val mutable = MutableStateFlow(runCatching { restored?.let(PixelArtJob::parse) }.getOrNull()?.let {
        if (it.phase == "submitting" && it.responseId == null) it.copy(phase = "unknown", message = "The request was interrupted before its receipt arrived. Check provider usage before another design.") else it
    })
    val state = mutable.asStateFlow()
    private val credentialState = MutableStateFlow(ArtistCredentials(legacyReceipt = mutable.value?.responseId != null && receiptAccount == null))
    val credentials = credentialState.asStateFlow()
    private var key = ""
    private var task: Job? = null
    val busy get() = mutable.value?.phase in listOf("submitting", "queued", "in_progress")
    fun setKey(value: String, savedId: String? = null) {
        val next = value.trim()
        require(next.length in 20..512 && next.all { it.code in 33..126 }) { "Enter a valid image API key." }
        check(task?.isActive != true || key == next) { "Clear this image session before switching accounts." }
        key = next
        credentialState.value = ArtistCredentials(true, receiptAccount == null && mutable.value?.responseId == null || receiptAccount == fingerprint(next), mutable.value?.responseId != null && receiptAccount == null, savedId)
    }
    fun clearKey() {
        task?.cancel(); key = ""
        credentialState.value = ArtistCredentials(legacyReceipt = mutable.value?.responseId != null && receiptAccount == null)
        if (mutable.value?.phase == "submitting" && mutable.value?.responseId == null) {
            mutable.value = mutable.value?.copy(phase = "unknown", message = "The image session was cleared before its receipt arrived. Check provider usage before another design.")
            runCatching { persist() }.onFailure { mutable.value = mutable.value?.copy(message = "The session was cleared, but its receipt could not be saved. Check provider usage before another design.") }
        }
    }
    private fun fingerprint(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    fun clearUnconfirmed() { if (mutable.value?.phase == "unknown") {
        val previous = mutable.value; val account = receiptAccount
        mutable.value = null; receiptAccount = null
        try { persist(); credentialState.value = credentialState.value.copy(receiptMatches = true, legacyReceipt = false) }
        catch (_: Exception) { receiptAccount = account; mutable.value = previous?.copy(message = "The saved receipt could not be cleared. Try again when storage is available.") }
    } }
    fun generate(prompt: String, target: String, reference: ByteArray) {
        if (busy || task?.isActive == true || mutable.value?.phase == "unknown") return
        require(target in listOf("pixel", "companion") && prompt.trim().length in 1..2000 && key.isNotEmpty())
        val requestKey = key
        receiptAccount = fingerprint(requestKey)
        credentialState.value = credentialState.value.copy(receiptMatches = true, legacyReceipt = false)
        mutable.value = PixelArtJob(target, "submitting"); persist()
        task = scope.launch {
            try { consume(wire.request(requestKey, "POST", "", PixelArtistWire.body(prompt.trim(), target, reference)), requestKey); poll(requestKey) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val known = mutable.value?.responseId != null || ((e as? ArtistHttpError)?.code?.let { it in 400..499 && it != 408 } == true)
                mutable.value = mutable.value?.copy(phase = if (known && mutable.value?.responseId == null) "failed" else if (known) mutable.value!!.phase else "unknown",
                    message = if (known) e.message else "No receipt arrived. Check provider usage before starting another design; it may still be running.")
                persist()
            }
        }
    }
    fun resume(confirmAccount: Boolean = false) {
        if (task?.isActive == true || key.isEmpty() || mutable.value?.responseId == null) return
        if (!credentialState.value.receiptMatches && !confirmAccount) return
        val requestKey = key
        task = scope.launch { try { poll(requestKey) } catch (e: CancellationException) { throw e } catch (_: Exception) {
            mutable.value = mutable.value?.copy(message = "Could not check the design. Your saved receipt is safe; try again later."); persist()
        } }
    }
    fun cancel() {
        if (!credentialState.value.ready || !credentialState.value.receiptMatches) return
        val id = mutable.value?.responseId?.takeIf(PixelArtistWire::validId) ?: return
        val requestKey = key
        task?.cancel()
        task = scope.launch { try { consume(wire.request(requestKey, "POST", "/$id/cancel", JSONObject()), requestKey) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.value = mutable.value?.copy(message = "Cancellation was not confirmed. Check status before another design."); persist() } }
    }
    private suspend fun poll(requestKey: String) {
        val id = mutable.value?.responseId?.takeIf(PixelArtistWire::validId) ?: return
        while (busy) { delay(4000); consume(wire.request(requestKey, "GET", "/$id"), requestKey) }
    }
    private suspend fun consume(response: JSONObject, requestKey: String) {
        val id = response.s("id") ?: throw ArtistHttpError(0)
        val phase = response.s("status") ?: throw ArtistHttpError(0)
        if (!PixelArtistWire.validId(id) || phase !in listOf("queued", "in_progress", "completed", "failed", "incomplete", "cancelled") || mutable.value?.responseId?.let { it != id } == true) throw ArtistHttpError(0)
        receiptAccount = fingerprint(requestKey)
        credentialState.value = credentialState.value.copy(receiptMatches = true, legacyReceipt = false)
        mutable.value = mutable.value?.copy(responseId = id, phase = if (phase == "completed") "in_progress" else phase, message = null); persist()
        if (phase == "completed") {
            try {
                val encoded = response.objects("output").firstOrNull { it.s("type") == "image_generation_call" }?.s("result") ?: throw ArtistHttpError(0)
                if (encoded.length > 28 * 1024 * 1024) throw ArtistHttpError(0)
                val size = if (mutable.value?.target == "companion") 16 else 48
                val art = withContext(Dispatchers.Default) { DesktopArtBaker.bake(Base64.getDecoder().decode(encoded), size) }
                mutable.value = mutable.value?.copy(phase = "completed", candidate = art); persist()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                mutable.value = mutable.value?.copy(phase = "failed", candidate = null, message = "The provider finished, but its image could not become a transparent sprite. Your current art is unchanged.")
                persist()
            }
        } else if (phase in listOf("failed", "incomplete")) { mutable.value = mutable.value?.copy(message = "The provider could not finish this design. Your current art is unchanged."); persist() }
    }
    private fun persist() {
        AppPaths.privateDirectory(receipt.parent)
        val stage = Files.createTempFile(receipt.parent, ".design-", ".json", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        try {
            Files.writeString(stage, mutable.value?.toJson()?.put("credential_scope", receiptAccount)?.toString() ?: "null")
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

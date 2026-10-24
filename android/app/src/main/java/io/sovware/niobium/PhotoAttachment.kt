package io.sovware.niobium

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class PhotoAttachment(val data: String, val mime: String, val width: Int, val height: Int, val preview: Bitmap) {
    override fun toString() = "PhotoAttachment($width x $height, $mime, [private])"
}

object PhotoAttachments {
    const val MAX_INPUT = 16 * 1024 * 1024
    const val MAX_OUTPUT = 4 * 1024 * 1024
    const val MAX_EDGE = 2048
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private class EncodedOutput : ByteArrayOutputStream() {
        var overflow = false
        override fun write(value: Int) { if (size() == MAX_OUTPUT) overflow = true else if (!overflow) super.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length > MAX_OUTPUT - size()) overflow = true
            if (!overflow) super.write(bytes, offset, length)
        }
    }

    fun readBounded(input: InputStream, limit: Int = MAX_INPUT): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(16384)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(count <= limit - output.size()) { "Choose an image smaller than 16 MB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    suspend fun prepare(resolver: ContentResolver, uri: Uri): PhotoAttachment = withContext(worker) {
        val bytes = resolver.openInputStream(uri)?.use { readBounded(it) } ?: error("This image could not be opened.")
        ensureActive()
        var bitmap: Bitmap? = null
        try {
            bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val size = info.size
                require(size.width > 0 && size.height > 0 && size.width.toLong() * size.height <= 100_000_000) { "Choose a smaller image." }
                val factor = minOf(1.0, MAX_EDGE.toDouble() / maxOf(size.width, size.height))
                decoder.setTargetSize((size.width * factor).roundToInt().coerceAtLeast(1), (size.height * factor).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
            }
            ensureActive()
            var image: Bitmap = checkNotNull(bitmap)
            val mime = if (image.hasAlpha()) "image/png" else "image/jpeg"
            var output: EncodedOutput
            var attempts = 0
            while (true) {
                ensureActive()
                output = EncodedOutput()
                check(image.compress(if (mime == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, output)) { "This image could not be prepared." }
                if (!output.overflow) break
                require(++attempts < 5) { "Choose a smaller image." }
                val smaller = Bitmap.createScaledBitmap(image, (image.width * .75).roundToInt().coerceAtLeast(1), (image.height * .75).roundToInt().coerceAtLeast(1), true)
                image.recycle(); image = smaller; bitmap = smaller
            }
            ensureActive()
            val previewScale = minOf(1.0, 160.0 / maxOf(image.width, image.height))
            val preview = Bitmap.createScaledBitmap(image, (image.width * previewScale).roundToInt().coerceAtLeast(1), (image.height * previewScale).roundToInt().coerceAtLeast(1), true)
            // Keep only a small preview after encoding; never save the photo in preferences.
            val detachedPreview = if (preview === image) image.copy(Bitmap.Config.ARGB_8888, false) else preview
            PhotoAttachment(Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP), mime, image.width, image.height, detachedPreview)
        } finally { bitmap?.recycle() }
    }
}

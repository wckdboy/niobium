package ai.wckd.niobium.desktop

import io.sovware.niobium.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.json.JSONObject
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object PixelImages {
    private val data by lazy { JSONObject(javaClass.getResourceAsStream("/portrait_looks.json")!!.bufferedReader().use { it.readText() }) }
    private val cache = object : LinkedHashMap<PixelLook, ImageBitmap>(96, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PixelLook, ImageBitmap>?) = size > 96
    }
    fun pixels(look: PixelLook): IntArray {
        require(look.valid)
        look.customArt?.let { return pixels(it) }
        val art = data.getJSONObject("looks").getJSONObject(look.portrait)
        val colors = art.getJSONArray("colors"); val indices = art.getJSONArray("pixels")
        val materials = art.getJSONArray("materials"); val shades = art.getJSONArray("shades")
        val accent = data.getJSONObject("accents").optJSONArray(look.accent)
        return IntArray(48 * 48) { i ->
            val index = indices.getInt(i)
            if (index !in 0 until colors.length()) 0 else {
                val shade = shades.getInt(i)
                val color = if (materials.getInt(i) == 1 && accent != null && shade in 0 until accent.length()) accent.getInt(shade) else colors.getInt(index)
                color or (0xff shl 24)
            }
        }
    }
    fun pixels(art: PixelArt): IntArray {
        val indices = requireNotNull(art.indices)
        return IntArray(indices.size) { i -> val slot = indices[i].toInt() and 255; if (slot == 255) 0 else art.palette[slot] or (0xff shl 24) }
    }
    private fun bitmap(size: Int, colors: IntArray): BufferedImage = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, size, size, colors, 0, size) }
    @Synchronized fun portrait(look: PixelLook): ImageBitmap = cache.getOrPut(look) { bitmap(48, pixels(look)).toComposeImageBitmap() }
    @Synchronized fun art(art: PixelArt): ImageBitmap = bitmap(art.size, pixels(art)).toComposeImageBitmap()
    private val companions = mutableMapOf<String, ImageBitmap>()
    @Synchronized fun companion(kind: String): ImageBitmap = companions.getOrPut(kind) {
        require(kind in PixelBuilder.companions)
        ImageIO.read(javaClass.getResourceAsStream("/comp_$kind.png")).toComposeImageBitmap()
    }
    fun companionReference(kind: String, art: PixelArt?): ByteArray = if (art == null) javaClass.getResourceAsStream("/comp_$kind.png")!!.use { it.readBytes() }
        else ByteArrayOutputStream().use { out -> ImageIO.write(bitmap(art.size, pixels(art)), "png", out); out.toByteArray() }
    fun reference(look: PixelLook): ByteArray = ByteArrayOutputStream().use { out -> ImageIO.write(bitmap(48, pixels(look)), "png", out); out.toByteArray() }
}

package ai.wckd.niobium.desktop

import io.sovware.niobium.*
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.ImageIO

class PixelImagesTest {
    @Test fun everyWardrobePortraitMatchesTheSharedRgbaFixtures() {
        val rows = JSONArray(javaClass.getResourceAsStream("/portrait-looks-fixtures.json")!!.bufferedReader().use { it.readText() })
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val look = PixelLook.parse(row.getJSONObject("look"))
            val colors = PixelImages.pixels(look)
            val rgba = ByteArray(colors.size * 4) { n -> val color = colors[n / 4]; (color ushr when (n % 4) { 0 -> 16; 1 -> 8; 2 -> 0; else -> 24 }).toByte() }
            val hash = MessageDigest.getInstance("SHA-256").digest(rgba).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(look.toString(), row.getString("rgba_sha256"), hash)
        }
    }
    @Test fun imageBakerRejectsOpaqueBackplatesAndKeepsAValidTransparentBorder() {
        fun png(image: BufferedImage) = ByteArrayOutputStream().use { ImageIO.write(image, "png", it); it.toByteArray() }
        val image = BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = java.awt.Color.MAGENTA; graphics.fillRect(20, 20, 56, 56); graphics.dispose()
        assertTrue(DesktopArtBaker.bake(png(image), 48).valid)
        assertTrue(DesktopArtBaker.bake(png(image), 16).valid)
        for (y in 0..95) for (x in 0..95) image.setRGB(x, y, 0xff6633aa.toInt())
        assertThrows(ArtistHttpError::class.java) { DesktopArtBaker.bake(png(image), 48) }
        assertThrows(ArtistHttpError::class.java) { DesktopArtBaker.bake(byteArrayOf(0), 48) }
    }
}

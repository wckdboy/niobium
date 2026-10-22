package io.sovware.niobium

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject

object CharacterEngine {
    private var atlas: JSONObject? = null
    private var looks: JSONObject? = null
    private val lookCache = LruCache<PixelLook, ImageBitmap>(96)
    @Synchronized
    fun image(context: Context, look: PixelLook): ImageBitmap? {
        if (!look.valid) return null
        lookCache.get(look)?.let { return it }
        val data = looks ?: runCatching {
            context.applicationContext.assets.open("portrait_looks.json").bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull()?.also { looks = it } ?: return null
        if (data.optInt("version") != 1 || data.optInt("size") != 48) return null
        val art = data.optJSONObject("looks")?.optJSONObject(look.portrait) ?: return null
        val colors = art.getJSONArray("colors")
        val indices = art.getJSONArray("pixels")
        val materials = art.getJSONArray("materials")
        val shades = art.getJSONArray("shades")
        if (listOf(indices, materials, shades).any { it.length() != 2304 }) return null
        val pigments = listOf(null, data.getJSONObject("accents").optJSONArray(look.accent))
        val pixels = IntArray(2304)
        for (i in pixels.indices) {
            val index = indices.getInt(i)
            if (index !in 0 until colors.length()) continue
            val palette = pigments.getOrNull(materials.getInt(i))
            val shade = shades.getInt(i)
            val color = if (palette != null && shade in 0 until palette.length()) palette.getInt(shade) else colors.getInt(index)
            pixels[i] = color or (0xFF shl 24)
        }
        return Bitmap.createBitmap(pixels, 48, 48, Bitmap.Config.ARGB_8888).asImageBitmap().also { lookCache.put(look, it) }
    }
    fun usesAuthored(member: CastMember) = member.recipe == CharacterRecipe.cast[member.id]
    fun authoredImage(context: Context, member: CastMember): ImageBitmap? =
        if (usesAuthored(member)) image(context, PixelLook(member.id)) else null
    private data class RenderKey(val recipe: CharacterRecipe, val blink: Boolean)
    private val cache = LruCache<RenderKey, ImageBitmap>(192)
    @Synchronized
    fun image(context: Context, recipe: CharacterRecipe, blink: Boolean = false): ImageBitmap? {
        val key = RenderKey(recipe, blink)
        cache.get(key)?.let { return it }
        if (!recipe.valid) return null
        val data = atlas ?: runCatching {
            context.applicationContext.assets.open("character_engine.json").bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull()?.also { atlas = it } ?: return null
        if (data.optInt("version") != 3 || data.optInt("size") != 48) return null
        val palette = mutableMapOf<Char, Int>()
        for (colors in listOf(data.optJSONObject("fixed"), data.optJSONObject("palettes")?.optJSONObject(recipe.palette),
            data.optJSONObject("skin_tones")?.optJSONObject(recipe.skin), data.optJSONObject("hair_tones")?.optJSONObject(recipe.hairTone))) {
            colors?.keys()?.forEach { key ->
                runCatching { Color.parseColor(colors.getString(key)) }.getOrNull()?.let { palette[key[0]] = it }
            }
        }
        val hair = data.optJSONObject("hair")?.optJSONObject(recipe.hair)
        val layers = listOf(hair?.optJSONArray("back"),
            data.optJSONObject("bodies")?.optJSONArray(recipe.body),
            data.optJSONObject("outfits")?.optJSONObject(recipe.outfit)?.optJSONArray(recipe.body),
            data.optJSONObject(if (blink) "blink_faces" else "faces")?.optJSONArray(recipe.face),
            data.optJSONObject("details")?.optJSONArray(recipe.detail), hair?.optJSONArray("front"),
            data.optJSONObject("accessories")?.optJSONArray(recipe.accessory))
        val pixels = IntArray(48 * 48)
        for (layer in layers.filterNotNull()) {
            for (y in 0 until minOf(48, layer.length())) {
                layer.optString(y).take(48).forEachIndexed { x, symbol ->
                    palette[symbol]?.let { pixels[y * 48 + x] = it }
                }
            }
        }
        return Bitmap.createBitmap(pixels, 48, 48, Bitmap.Config.ARGB_8888).asImageBitmap().also { cache.put(key, it) }
    }
}

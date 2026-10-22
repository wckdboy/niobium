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
    private var authored: Map<String, ImageBitmap>? = null
    fun usesAuthored(member: CastMember) = member.recipe == CharacterRecipe.cast[member.id]
    @Synchronized
    fun authoredImage(context: Context, member: CastMember): ImageBitmap? {
        if (!usesAuthored(member)) return null
        if (authored == null) {
            val options = android.graphics.BitmapFactory.Options().apply { inScaled = false }
            val sheet = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.pixel_cast_atlas, options) ?: return null
            if (sheet.width != 192 || sheet.height != 96) { sheet.recycle(); return null }
            authored = Cast.all.mapIndexed { index, item ->
                item.id to Bitmap.createBitmap(sheet, index % 4 * 48, index / 4 * 48, 48, 48).asImageBitmap()
            }.toMap()
            sheet.recycle()
        }
        return authored?.get(member.id)
    }
    private val cache = LruCache<CharacterRecipe, ImageBitmap>(192)
    @Synchronized
    fun image(context: Context, recipe: CharacterRecipe): ImageBitmap? {
        cache.get(recipe)?.let { return it }
        if (!recipe.valid) return null
        val data = atlas ?: runCatching {
            context.applicationContext.assets.open("character_engine.json").bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull()?.also { atlas = it } ?: return null
        if (data.optInt("size") != 48) return null
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
            data.optJSONObject("outfits")?.optJSONArray(recipe.outfit),
            data.optJSONObject("faces")?.optJSONArray(recipe.face), data.optJSONObject("details")?.optJSONArray(recipe.detail), hair?.optJSONArray("front"),
            data.optJSONObject("accessories")?.optJSONArray(recipe.accessory))
        val pixels = IntArray(48 * 48)
        for (layer in layers.filterNotNull()) {
            for (y in 0 until minOf(48, layer.length())) {
                layer.optString(y).take(48).forEachIndexed { x, symbol ->
                    palette[symbol]?.let { pixels[y * 48 + x] = it }
                }
            }
        }
        return Bitmap.createBitmap(pixels, 48, 48, Bitmap.Config.ARGB_8888).asImageBitmap().also { cache.put(recipe, it) }
    }
}

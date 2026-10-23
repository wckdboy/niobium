package io.sovware.niobium

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class PixelArt(val size: Int, val palette: List<Int>, val pixels: String, val version: Int = 1) {
    val indices: ByteArray? get() {
        if (version != 1 || size !in listOf(16, 48) || palette.size !in 1..32 || palette.any { it !in 0..0xFFFFFF } || pixels.length > 3072) return null
        val values = runCatching { Base64.getDecoder().decode(pixels) }.getOrNull() ?: return null
        if (values.size != size * size || values.any { (it.toInt() and 255) != 255 && (it.toInt() and 255) !in palette.indices } || values.all { it.toInt() == -1 }) return null
        if ((0 until size).any { values[it].toInt() != -1 || values[(size-1)*size+it].toInt() != -1 || values[it*size].toInt() != -1 || values[it*size+size-1].toInt() != -1 }) return null
        return values
    }
    val valid get() = indices != null
    fun rgba(): ByteArray? = indices?.flatMap { value ->
        val i = value.toInt() and 255
        if (i == 255) listOf<Byte>(0,0,0,0) else palette[i].let { listOf((it shr 16).toByte(), (it shr 8).toByte(), it.toByte(), 255.toByte()) }
    }?.toByteArray()
    fun toJson() = JSONObject().put("version", version).put("size", size).put("palette", JSONArray(palette)).put("pixels", pixels)
    companion object {
        fun parse(j: JSONObject): PixelArt {
            fun integer(value: Any): Int {
                require(value is Number && value.toDouble() == value.toInt().toDouble()) { "Expected integer pixels" }
                return value.toInt()
            }
            val palette = j.getJSONArray("palette")
            require(palette.length() in 1..32)
            return PixelArt(integer(j.get("size")), (0 until palette.length()).map { integer(palette.get(it)) },
                j.get("pixels") as String, integer(j.get("version"))).also { require(it.valid) { "Invalid indexed art" } }
        }
    }
}

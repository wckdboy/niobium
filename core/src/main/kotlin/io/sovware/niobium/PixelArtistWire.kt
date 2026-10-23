package io.sovware.niobium

import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

data class PixelArtJob(val target: String, val phase: String, val responseId: String? = null, val candidate: PixelArt? = null, val message: String? = null) {
    fun toJson() = JSONObject().put("target", target).put("phase", phase).put("response_id", responseId)
        .put("candidate", candidate?.toJson()).put("message", message)
    companion object {
        fun parse(j: JSONObject) = PixelArtJob(j.getString("target"), j.getString("phase"), j.s("response_id"), j.o("candidate")?.let(PixelArt::parse), j.s("message"))
    }
}

class ArtistHttpError(val code: Int) : Exception(when(code) {
    401,403 -> "Check your OpenAI image key and model access."
    404 -> "The saved design is unavailable or expired. Your current art is unchanged."
    429 -> "The image provider reached its usage limit. Check the saved design later."
    0 -> "The provider returned an unusable image design. Your current art is unchanged."
    else -> "The image provider declined the request (HTTP $code). Check model access and usage."
})

class PixelArtistWire(private val http: OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false)
    .followRedirects(false).followSslRedirects(false).callTimeout(60, TimeUnit.SECONDS).build(),
    private val endpoint: String = "https://api.openai.com/v1/responses") {
    suspend fun request(key: String, method: String, suffix: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        require(key.isNotEmpty())
        val request = Request.Builder().url(endpoint + suffix).header("Authorization", "Bearer $key")
            .method(method, if (method == "GET") null else (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ArtistHttpError(response.code)
            val source = response.body.source()
            source.request(32L*1024*1024+1)
            if (source.buffer.size > 32L*1024*1024) throw ArtistHttpError(0)
            runCatching { JSONObject(source.buffer.readUtf8()) }.getOrElse { throw ArtistHttpError(0) }
        }
    }
    companion object {
        fun validId(id: String) = id.matches(Regex("^resp_[A-Za-z0-9_-]{1,160}$"))
        fun body(prompt: String, target: String, referencePng: ByteArray?): JSONObject {
            val subject = if (target == "companion") "Exactly one freaky adorable fantasy/cyborg companion creature, not a human; expressive eyes and a distinct silhouette. Design for a 16 by 16 logical-pixel sprite."
                else "Exactly one unambiguously adult person age 28–45, mature face and adult proportions, waist-up. Attractive fantasy/cyborg design; bold skimpy opaque clothing, stylish cutouts, straps or cropped armor. Intimate areas fully covered; non-explicit, no nudity, sexual activity, youthful or school styling. Design for a 48 by 48 logical-pixel portrait."
            val text = "Generate production game sprite art. $subject Match the reference identity when present; change only the requested clothing/style/features. Transparent background, fully contained with generous empty margins, centered; no text, watermark, frame, background, ground shadow, extra character or interface. Crisp intentional square pixel clusters, limited 24–32 colors, clear eyes and selective high-contrast shading, premium 16-bit RPG art; no smooth painting or blur. Owner’s design brief: $prompt"
            val content = JSONArray().put(JSONObject().put("type", "input_text").put("text", text))
            referencePng?.let { content.put(JSONObject().put("type", "input_image").put("image_url", "data:image/png;base64," + Base64.getEncoder().encodeToString(it))) }
            return JSONObject().put("model", "gpt-6-astra").put("background", true).put("store", true)
                .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .put("tools", JSONArray().put(JSONObject().put("type", "image_generation").put("model", "gpt-image-2.5-sunburst")
                    .put("background", "transparent").put("output_format", "png").put("quality", "medium").put("size", "1024x1024")))
        }
    }
}


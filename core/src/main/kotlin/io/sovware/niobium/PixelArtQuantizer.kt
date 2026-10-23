package io.sovware.niobium

import java.util.Base64
import kotlin.math.sqrt

/** Shared palette reduction after a platform decoder scales transparent PNG art. */
object PixelArtQuantizer {
    fun bake(rgba: IntArray, size: Int): PixelArt {
        require(size in listOf(16, 48) && rgba.size == size * size)
        val corners = listOf(size+1,size+size-2,(size-2)*size+1,(size-2)*size+size-2)
        if (corners.count { (rgba[it] ushr 24) < 128 } < 2) throw ArtistHttpError(0)
        val colors = rgba.mapIndexed { i,c -> if ((c ushr 24) < 128 || i%size == 0 || i%size == size-1 || i/size == 0 || i/size == size-1) null else c and 0xFFFFFF }
        val histogram = colors.filterNotNull().groupingBy { it }.eachCount()
        if (histogram.values.sum() < size*size/12) throw ArtistHttpError(0)
        val samples = histogram.keys.sorted()
        fun distance(a: Int, b: Int) = listOf(16,8,0).sumOf { val d = ((a shr it) and 255) - ((b shr it) and 255); d*d }
        val palette = mutableListOf(samples.maxBy { histogram.getValue(it) })
        fun nearest(c: Int) = palette.indices.minBy { distance(c,palette[it]) }
        while (palette.size < minOf(32,samples.size)) {
            val next = samples.maxBy { distance(it,palette[nearest(it)]) * sqrt(histogram.getValue(it).toDouble()) }
            if (next in palette) break; palette.add(next)
        }
        repeat(4) {
            val sums = Array(palette.size) { IntArray(4) }
            samples.forEach { c -> val i = nearest(c); val n = histogram.getValue(c); sums[i][0] += ((c shr 16) and 255)*n; sums[i][1] += ((c shr 8) and 255)*n; sums[i][2] += (c and 255)*n; sums[i][3] += n }
            palette.indices.filter { sums[it][3] > 0 }.forEach { val s = sums[it]; palette[it] = (s[0]/s[3] shl 16) or (s[1]/s[3] shl 8) or (s[2]/s[3]) }
        }
        return PixelArt(size, palette, Base64.getEncoder().encodeToString(ByteArray(colors.size) { colors[it]?.let { c -> nearest(c).toByte() } ?: 255.toByte() }))
            .also { if (!it.valid) throw ArtistHttpError(0) }
    }
}

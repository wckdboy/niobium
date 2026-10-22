package io.sovware.niobium

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

enum class PixelGlyph { HOME, PIXELS, TERMINAL, ACTIVITY, SETTINGS, PLAY, STOP, BACK, LINK, SPARK, SHIELD }

@Composable
fun PixelIcon(glyph: PixelGlyph, tint: Color = Nb.Ion, size: Dp = 24.dp, description: String? = null) {
    val rectangles = when (glyph) {
        PixelGlyph.HOME -> listOf(intArrayOf(7,1,2,2),intArrayOf(5,3,6,2),intArrayOf(3,5,10,2),
            intArrayOf(1,7,14,2),intArrayOf(3,9,4,6),intArrayOf(9,9,4,6),intArrayOf(7,9,2,2))
        PixelGlyph.PIXELS -> listOf(intArrayOf(1,1,6,6),intArrayOf(9,1,6,6),intArrayOf(1,9,6,6),intArrayOf(9,9,6,6))
        PixelGlyph.TERMINAL -> listOf(intArrayOf(1,2,14,2),intArrayOf(1,12,14,2),intArrayOf(1,4,2,8),
            intArrayOf(13,4,2,8),intArrayOf(4,5,2,2),intArrayOf(6,7,2,2),intArrayOf(4,9,2,2),intArrayOf(9,9,2,2))
        PixelGlyph.ACTIVITY -> listOf(intArrayOf(1,9,3,2),intArrayOf(4,5,2,6),intArrayOf(6,2,2,7),
            intArrayOf(8,7,2,7),intArrayOf(10,9,2,2),intArrayOf(12,6,2,5),intArrayOf(14,8,2,2))
        PixelGlyph.SETTINGS -> listOf(intArrayOf(6,1,4,3),intArrayOf(6,12,4,3),intArrayOf(1,6,3,4),
            intArrayOf(12,6,3,4),intArrayOf(3,3,3,3),intArrayOf(10,3,3,3),intArrayOf(3,10,3,3),
            intArrayOf(10,10,3,3),intArrayOf(4,4,8,2),intArrayOf(4,10,8,2),intArrayOf(4,6,2,4),intArrayOf(10,6,2,4))
        PixelGlyph.PLAY -> (0..5).map { intArrayOf(3+it*2,2+it,2,12-it*2) }
        PixelGlyph.STOP -> listOf(intArrayOf(3,3,10,10))
        PixelGlyph.BACK -> listOf(intArrayOf(7,2,2,2),intArrayOf(5,4,2,2),intArrayOf(3,6,2,4),
            intArrayOf(5,10,2,2),intArrayOf(7,12,2,2),intArrayOf(5,7,10,2))
        PixelGlyph.LINK -> listOf(intArrayOf(2,2,7,2),intArrayOf(2,4,2,5),intArrayOf(4,7,3,2),
            intArrayOf(7,7,2,2),intArrayOf(9,7,3,2),intArrayOf(12,7,2,5),intArrayOf(7,12,7,2),intArrayOf(7,4,2,3))
        PixelGlyph.SPARK -> listOf(intArrayOf(7,1,2,4),intArrayOf(5,5,6,2),intArrayOf(1,7,14,2),
            intArrayOf(5,9,6,2),intArrayOf(7,11,2,4))
        PixelGlyph.SHIELD -> listOf(intArrayOf(6,1,4,2),intArrayOf(2,3,12,2),intArrayOf(2,5,2,5),
            intArrayOf(12,5,2,5),intArrayOf(4,10,2,2),intArrayOf(10,10,2,2),intArrayOf(6,12,4,2))
    }
    Canvas(Modifier.size(size).semantics { if (description != null) contentDescription = description }) {
        val cell = floor(minOf(this.size.width, this.size.height) / 16f).coerceAtLeast(1f)
        val x = floor((this.size.width - cell * 16) / 2)
        val y = floor((this.size.height - cell * 16) / 2)
        rectangles.forEach { r -> drawRect(tint, Offset(x + r[0]*cell, y + r[1]*cell), Size(r[2]*cell, r[3]*cell)) }
    }
}

val PixelButtonShape = GenericShape { size, _ ->
    val step = floor(size.height / 6f)
    moveTo(step, 0f); lineTo(size.width-step, 0f); lineTo(size.width-step, step)
    lineTo(size.width, step); lineTo(size.width, size.height-step); lineTo(size.width-step, size.height-step)
    lineTo(size.width-step, size.height); lineTo(step, size.height); lineTo(step, size.height-step)
    lineTo(0f, size.height-step); lineTo(0f, step); lineTo(step, step); close()
}

@Composable
fun PixelButton(label: String, glyph: PixelGlyph, modifier: Modifier = Modifier,
                secondary: Boolean = false, destructive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (destructive) Nb.Rose else Nb.Ion
    val background = if (secondary) tint.copy(alpha = 0.09f) else tint
    val foreground = if (secondary) tint else if (!Nb.light && !destructive) Color(0xFF1B1326) else Color.White
    Row(modifier.heightIn(min = 50.dp).clip(PixelButtonShape)
        .background(if (enabled) background else Nb.FillStrong)
        .border(1.dp, if (enabled) tint.copy(alpha = 0.32f) else Nb.Line, PixelButtonShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        PixelIcon(glyph, if (enabled) foreground else Nb.Tertiary, 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (enabled) foreground else Nb.Tertiary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

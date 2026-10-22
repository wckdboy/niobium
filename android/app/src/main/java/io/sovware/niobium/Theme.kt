package io.sovware.niobium

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.cos
import kotlin.math.sin

object Nb {
    var appearance by mutableStateOf("system")
    var systemDark by mutableStateOf(false)
    val light: Boolean
        get() = when (appearance) {
            "light" -> true
            "dark" -> false
            else -> !systemDark
        }

    val Void: Color get() = if (light) Color(0xFFF8F7FF) else Color(0xFF07050C)
    val Ink: Color get() = if (light) Color(0xFFFFFFFF) else Color(0xFF0B0812)
    val Surface: Color get() = if (light) Color(0xFFFFFFFF) else Color(0xFF120E1B)
    val Raised: Color get() = if (light) Color(0xFFF2ECFA) else Color(0xFF191324)
    val Hover: Color get() = if (light) Color(0xFFEDE6F8) else Color(0xFF22192F)
    val Line: Color get() = if (light) Color(0x298B6FE0) else Color(0x12FFFFFF)
    val LineStrong: Color get() = if (light) Color(0x3D8B6FE0) else Color(0x21FFFFFF)
    val Fill: Color get() = if (light) Color(0x0C1B1326) else Color(0x0EFFFFFF)
    val FillSoft: Color get() = if (light) Color(0x081B1326) else Color(0x07FFFFFF)
    val FillStrong: Color get() = if (light) Color(0x141B1326) else Color(0x1AFFFFFF)
    val Shade: Color get() = if (light) Color(0x1F2A1745) else Color(0x52000000)
    val Code: Color get() = if (light) Color(0x0C1B1326) else Color(0x42000000)
    val Text: Color get() = if (light) Color(0xFF1B1326) else Color(0xFFF6F2FF)
    val Secondary: Color get() = if (light) Color(0xFF625671) else Color(0xFFADA3BD)
    val Tertiary: Color get() = if (light) Color(0xFF74698D) else Color(0xFF736A82)
    val Ion: Color get() = if (light) Color(0xFF7C3AED) else Color(0xFFA78BFA)
    val IonDeep: Color get() = if (light) Color(0xFF6D28D9) else Color(0xFF7C3AED)
    val Violet: Color get() = if (light) Color(0xFF5B21B6) else Color(0xFFE9D5FF)
    val Mint: Color get() = if (light) Color(0xFF0F9F8A) else Color(0xFF5EEAD4)
    val Amber: Color get() = if (light) Color(0xFFB77900) else Color(0xFFFBBF24)
    val Rose: Color get() = if (light) Color(0xFFD9485F) else Color(0xFFFB7185)
    val OnIon: Color = Color.White
    val OrbPalette: List<Color>
        get() = listOf(Ion, if (light) Color(0xFFC026D3) else Color(0xFFF0ABFC), Mint, Amber,
            if (light) Color(0xFFE11D48) else Color(0xFFFDA4AF),
            if (light) Color(0xFF8B5CF6) else Color(0xFFC4B5FD))

    fun colorFor(name: String?): Color {
        if (name.isNullOrBlank()) return Ion
        val trimmed = name.trim()
        if (trimmed.startsWith("#")) {
            runCatching { return Color(android.graphics.Color.parseColor(trimmed)) }
        }
        when (trimmed.lowercase()) {
            "athena" -> return Ion
            "forge" -> return if (light) Color(0xFFB77900) else Color(0xFFF5C97A)
            "scout" -> return Mint
            "hermes" -> return if (light) Color(0xFF8B5CF6) else Color(0xFFC4B5FD)
        }
        var hash = 41L
        for (c in trimmed) hash = hash * 31 + c.code
        return OrbPalette[((hash and 0x7FFFFFFF) % OrbPalette.size).toInt()]
    }
}

val NbDisplay = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp)
val NbTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
val NbBody = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal)
val NbMono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal)
val NbLabel = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)

@Composable
fun NbBackground(intensity: Float = 1f, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Nb.Void)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Nb.Ion.copy(alpha = (if (Nb.light) 0.12f else 0.22f) * intensity), Color.Transparent),
                    center = Offset(this.size.width * 0.80f, -this.size.height * 0.04f),
                    radius = this.size.width * 0.95f
                ),
                radius = this.size.width * 0.95f,
                center = Offset(this.size.width * 0.80f, -this.size.height * 0.04f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Nb.IonDeep.copy(alpha = (if (Nb.light) 0.08f else 0.16f) * intensity), Color.Transparent),
                    center = Offset(this.size.width * 0.02f, this.size.height * 0.48f),
                    radius = this.size.width * 0.72f
                ),
                radius = this.size.width * 0.72f,
                center = Offset(this.size.width * 0.02f, this.size.height * 0.48f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf((if (Nb.light) Color(0xFF8B5CF6) else Color(0xFF3B0764)).copy(alpha = (if (Nb.light) 0.08f else 0.20f) * intensity), Color.Transparent),
                    center = Offset(this.size.width * 0.55f, this.size.height * 1.02f),
                    radius = this.size.width * 0.85f
                ),
                radius = this.size.width * 0.85f,
                center = Offset(this.size.width * 0.55f, this.size.height * 1.02f)
            )
        }
        content()
    }
}

@Composable
fun AtomicMark(size: Dp = 36.dp, animated: Boolean = false) {
    Image(
        bitmap = ImageBitmap.imageResource(R.drawable.pixel_niobium),
        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        contentDescription = "Niobium",
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .size(size)
            .shadow(size * 0.22f, RoundedCornerShape(size * 0.29f), spotColor = Nb.Ion.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(size * 0.29f))
            .border(0.8.dp, Nb.LineStrong, RoundedCornerShape(size * 0.29f))
    )
}

@Composable
fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AtomicMark(size = 28.dp)
        Spacer(Modifier.width(9.dp))
        Text("niobium", color = Nb.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    }
}

@Composable
fun PageHeader(eyebrow: String, title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(eyebrow.uppercase(), color = Nb.Tertiary, style = NbLabel, fontSize = 9.5.sp)
            Spacer(Modifier.height(7.dp))
            Text(title, color = Nb.Text, style = NbDisplay, fontSize = 36.sp)
        }
        trailing?.invoke()
    }
}

@Composable
fun NbPill(text: String, tint: Color = Nb.Ion, filled: Boolean = false) {
    Box(
        Modifier
            .clip(PixelButtonShape)
            .background(if (filled) tint else tint.copy(alpha = 0.10f))
            .border(1.dp, if (filled) Color.Transparent else tint.copy(alpha = 0.26f), PixelButtonShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = if (filled) Nb.OnIon else tint,
            fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionHeader(title: String, detail: String? = null, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Nb.Text.copy(alpha = 0.90f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        if (detail != null) Text(detail, color = Nb.Tertiary, style = NbMono, fontSize = 9.sp)
        if (action != null && onAction != null) {
            Spacer(Modifier.width(12.dp))
            Text(action, color = Nb.Ion, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickableNoRipple(onAction).padding(4.dp))
        }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable { onClick() }

@Composable
fun PixelFace(accent: Color, pattern: Mascot = Mascots.seeded("niobium"), modifier: Modifier = Modifier) {
    MascotFace(pattern, accent, modifier)
}

@Composable
fun PixelTile(accent: Color, size: Dp, pattern: Mascot? = null) {
    MascotTile(pattern ?: Mascots.seeded("niobium"), accent, size)
}

@Composable
fun BotAvatar(
    title: String,
    accent: Color,
    size: Dp = 48.dp,
    isLive: Boolean = true,
    showStatus: Boolean = true,
    profileName: String = title,
    accentHex: String? = null
) {
    val member = CastStore.get(androidx.compose.ui.platform.LocalContext.current, profileName)
    Box(contentAlignment = Alignment.BottomEnd) {
        CastPortrait(member, size, showCompanion = size >= 48.dp)
        if (showStatus) {
            Box(
                Modifier
                    .size(size * 0.20f)
                    .clip(RoundedCornerShape(size * 0.05f))
                    .background(if (isLive) Nb.Mint else Nb.Tertiary)
                    .border(2.dp, Nb.Ink, RoundedCornerShape(size * 0.05f))
            )
        }
    }
}

@Composable
fun GroupAvatar(members: List<GroupMember>, size: Int = 46) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.size(size.dp)) {
        members.take(3).forEachIndexed { index, member ->
            Box(
                Modifier
                    .align(
                        when (index) {
                            0 -> Alignment.TopStart
                            1 -> Alignment.TopEnd
                            else -> Alignment.BottomEnd
                        }
                    )
                    .border(2.dp, Nb.Ink, RoundedCornerShape((size * 0.58f * 0.28f).dp))
            ) {
                CastPortrait(CastStore.get(context, member.profile), (size * 0.58f).dp, showCompanion = false)
            }
        }
    }
}

@Composable
fun StatChip(value: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$value $label", color = Nb.Secondary, style = NbMono, fontSize = 10.sp)
    }
}

fun formatRelative(epochSec: Long?): String {
    if (epochSec == null || epochSec <= 0) return "ready"
    val delta = (System.currentTimeMillis() / 1000) - epochSec
    return when {
        delta < 60 -> "now"
        delta < 3600 -> "${delta / 60}m"
        delta < 86400 -> "${delta / 3600}h"
        delta < 604800 -> "${delta / 86400}d"
        else -> "${delta / 604800}w"
    }
}

package ai.wckd.niobium.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

object Ink {
    val Paper = Color(0xFFF8F7FC)
    val Panel = Color(0xFFFFFFFF)
    val Text = Color(0xFF211A39)
    val Muted = Color(0xFF796F90)
    val Line = Color(0xFFE7E1F1)
    val Violet = Color(0xFF8350EE)
    val Pale = Color(0xFFF0EAFD)
    val Green = Color(0xFF008C68)
    val Red = Color(0xFFBA365C)
}

val PixelShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cut = with(density) { 4.dp.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2)
        return Outline.Generic(Path().apply {
            moveTo(cut, 0f); lineTo(size.width - cut, 0f); lineTo(size.width - cut, cut); lineTo(size.width, cut)
            lineTo(size.width, size.height - cut); lineTo(size.width - cut, size.height - cut); lineTo(size.width - cut, size.height)
            lineTo(cut, size.height); lineTo(cut, size.height - cut); lineTo(0f, size.height - cut); lineTo(0f, cut); lineTo(cut, cut); close()
        })
    }
}

@Composable fun DesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink.Violet, onPrimary = Color.White, background = Ink.Paper,
        surface = Ink.Panel, onSurface = Ink.Text, onBackground = Ink.Text, outline = Ink.Line, secondary = Ink.Muted), content = content)
}

@Composable fun DesktopTextScale(percent: Int, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * percent / 100f), content = content)
}

/** One keyboard target, semantic role and visible stepped focus ring per action. */
@Composable fun Modifier.pixelAction(enabled: Boolean = true, selected: Boolean? = null,
                                    role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val action = if (selected == null) Modifier.clickable(interaction, LocalIndication.current, enabled, role = role, onClick = onClick)
        else Modifier.selectable(selected, interaction, LocalIndication.current, enabled, role, onClick)
    return this.then(action).border(2.dp, if (focused && enabled) Ink.Text else Color.Transparent, PixelShape)
}

@Composable fun Modifier.pixelToggle(checked: Boolean, enabled: Boolean = true, role: Role = Role.Checkbox,
                                    onChange: (Boolean) -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    return this.toggleable(checked, interaction, LocalIndication.current, enabled, role, onChange)
        .border(2.dp, if (focused && enabled) Ink.Text else Color.Transparent, PixelShape)
}

@Composable fun LabeledCheckbox(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(PixelShape).pixelToggle(checked, enabled, onChange = onChange).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Text(label, color = Ink.Muted, fontSize = 12.sp)
    }
}

@Composable fun PixelButton(label: String, modifier: Modifier = Modifier, secondary: Boolean = false, enabled: Boolean = true, icon: String? = null, onClick: () -> Unit) {
    val color = if (secondary) Ink.Pale else Ink.Violet
    Row(modifier.heightIn(min = 40.dp).clip(PixelShape).background(if (enabled) color else Ink.Line)
        .pixelAction(enabled = enabled, onClick = onClick).padding(horizontal = 15.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Glyph(it, if (secondary) Ink.Violet else Color.White, 15.dp) }
        Text(label, color = if (!enabled) Ink.Muted else if (secondary) Ink.Violet else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(PixelShape).background(Ink.Panel).border(1.dp, Ink.Line, PixelShape).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
@Composable fun Eyebrow(text: String, color: Color = Ink.Muted) { Text(text.uppercase(), color = color, fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
@Composable fun StatusDot(online: Boolean) { Box(Modifier.size(6.dp).background(if (online) Ink.Green else Ink.Muted)) }
@Composable fun Glyph(name: String, color: Color = Ink.Muted, size: Dp = 20.dp) {
    val rows = when (name) {
        "pixels" -> listOf("11011", "11011", "00000", "11011", "11011")
        "rooms" -> listOf("11110", "10010", "11110", "01001", "00111")
        "engine" -> listOf("01110", "11011", "10001", "11011", "01110")
        "spark" -> listOf("00100", "00100", "11111", "00100", "00100")
        "add" -> listOf("00100", "00100", "11111", "00100", "00100")
        "send" -> listOf("10000", "11100", "11111", "11100", "10000")
        "stop" -> listOf("11111", "11111", "11111", "11111", "11111")
        "back" -> listOf("00100", "01000", "11111", "01000", "00100")
        "shield" -> listOf("11111", "10001", "10001", "01010", "00100")
        else -> listOf("11111", "10001", "10101", "10001", "11111")
    }
    Canvas(Modifier.size(size)) {
        val pixel = (this.size.minDimension / 5).toInt().coerceAtLeast(1).toFloat()
        val x0 = (this.size.width - pixel * 5) / 2; val y0 = (this.size.height - pixel * 5) / 2
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, value -> if (value == '1') drawRect(color, androidx.compose.ui.geometry.Offset(x0 + x * pixel, y0 + y * pixel), Size(pixel, pixel)) } }
    }
}

fun DrawScope.pixelDust() {
    val grid = 32.dp.toPx()
    for (x in 0..(size.width / grid).toInt()) for (y in 0..(size.height / grid).toInt()) {
        if ((x * 7 + y * 11) % 9 == 0) drawRect(Ink.Violet.copy(alpha = .08f), androidx.compose.ui.geometry.Offset(x * grid, y * grid), Size(2.dp.toPx(), 2.dp.toPx()))
    }
}

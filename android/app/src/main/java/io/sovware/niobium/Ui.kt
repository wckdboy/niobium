package io.sovware.niobium

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Nb.Surface, Nb.Ink.copy(alpha = 0.88f)))
            )
            .border(1.dp, Brush.linearGradient(listOf(Nb.FillStrong, Color.White.copy(alpha = 0.02f))), RoundedCornerShape(20.dp))
            .padding(padding.dp)
    ) { content() }
}

@Composable
fun NbIconButton(icon: String, onClick: () -> Unit, tint: Color = Nb.Secondary, size: Int = 38) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Nb.Fill)
            .border(1.dp, Nb.Line, CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(icon, color = tint, fontSize = (size * 0.38).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LiveDot(color: Color = Nb.Mint, pulse: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        if (pulse) 1f else 0.01f, if (pulse) 1f else 0.011f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "scale"
    )
    Box(contentAlignment = Alignment.Center) {
        if (pulse) Box(Modifier.size(16.dp).clip(CircleShape).background(color.copy(alpha = 0.18f * scale)))
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
    }
}


@Composable
fun BotRow(bot: BotProfile, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (bot.isLive) Brush.linearGradient(listOf(Nb.Ion.copy(alpha = 0.10f), Nb.FillSoft))
                else Brush.linearGradient(listOf(Nb.FillSoft, Nb.FillSoft))
            )
            .border(1.dp, if (bot.isLive) Nb.Ion.copy(alpha = 0.22f) else Nb.Line, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BotAvatar(bot.title, bot.accent, 50.dp, bot.isLive, profileName = bot.castKey)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bot.title, color = Nb.Text, style = NbTitle, fontSize = 16.sp, maxLines = 1)
                if (bot.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Nb.Ion))
                }
                Spacer(Modifier.weight(1f))
                Text(bot.statusLine, color = if (bot.isLive) Nb.Mint else Nb.Tertiary, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(bot.preview.ifBlank { "Start a conversation" }, color = Nb.Secondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = Nb.Tertiary.copy(alpha = 0.55f), fontSize = 18.sp)
    }
}

@Composable
fun GroupRow(room: GroupRoom, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nb.FillSoft)
            .border(1.dp, Nb.Line, RoundedCornerShape(18.dp)).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupAvatar(room.members, 44)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(room.name, color = Nb.Text, style = NbTitle, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Text("· ${room.members.size} pixels", color = Nb.Tertiary, fontSize = 10.5.sp)
                Spacer(Modifier.weight(1f))
                Text(formatRelative(room.updatedAt), color = Nb.Tertiary, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(room.preview.ifBlank { "Open the room" }, color = Nb.Secondary, fontSize = 12.5.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = Nb.Tertiary.copy(alpha = 0.55f), fontSize = 18.sp)
    }
}


// ── Markdown ─────────────────────────────────────────────────────────────────

private sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Numbered(val number: Int, val text: String) : MdBlock()
    data class Code(val text: String) : MdBlock()
}

private fun parseBlocks(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    var code = mutableListOf<String>()
    var inCode = false
    source.lines().forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("```")) {
            if (inCode) { blocks.add(MdBlock.Code(code.joinToString("\n"))); code = mutableListOf() }
            inCode = !inCode
        } else if (inCode) {
            code.add(raw)
        } else if (line.isNotEmpty()) {
            when {
                line.startsWith("- ") || line.startsWith("• ") -> blocks.add(MdBlock.Bullet(line.drop(2)))
                Regex("^\\d+\\.\\s+").containsMatchIn(line) -> {
                    val dot = line.indexOf('.')
                    blocks.add(MdBlock.Numbered(line.substring(0, dot).toIntOrNull() ?: 1, line.substring(dot + 1).trim()))
                }
                else -> blocks.add(MdBlock.Paragraph(line))
            }
        }
    }
    if (code.isNotEmpty()) blocks.add(MdBlock.Code(code.joinToString("\n")))
    return blocks
}

private fun styledInline(text: String, color: Color = Nb.Text): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Nb.Ion, background = Nb.Ink)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text.startsWith("*", i) || text.startsWith("_", i) -> {
                val marker = text[i]
                val end = text.indexOf(marker, i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color.copy(alpha = 0.92f))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

@Composable
fun Markdown(text: String, color: Color = Nb.Text, fontSize: Int = 15) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parseBlocks(text).forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(styledInline(block.text, color), color = color, fontSize = fontSize.sp, lineHeight = (fontSize + 7).sp)
                is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text("•", color = Nb.Ion, fontSize = fontSize.sp, modifier = Modifier.padding(top = 0.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(styledInline(block.text, color), color = color.copy(alpha = 0.92f), fontSize = (fontSize - 0.5f).sp, lineHeight = (fontSize + 5).sp)
                }
                is MdBlock.Numbered -> Row(Modifier.fillMaxWidth()) {
                    Text("${block.number}.", color = Nb.Ion, style = NbMono, fontSize = 11.sp, modifier = Modifier.width(20.dp).padding(top = 3.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(styledInline(block.text, color), color = color.copy(alpha = 0.92f), fontSize = (fontSize - 0.5f).sp, lineHeight = (fontSize + 5).sp)
                }
                is MdBlock.Code -> Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Nb.Code)
                        .border(1.dp, Nb.Line, RoundedCornerShape(11.dp)).padding(11.dp)
                ) {
                    Text(block.text, color = Color(0xFFC9D6F0), style = NbMono, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun ToolCallCard(message: ChatMessage) {
    var expanded by remember { mutableStateOf(false) }
    val tint = when (message.toolStatus) {
        ToolStatus.RUNNING -> Nb.Amber
        ToolStatus.DONE -> Nb.Mint
        ToolStatus.FAILED -> Nb.Rose
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Nb.Surface.copy(alpha = 0.8f))
            .border(1.dp, tint.copy(alpha = if (message.toolStatus == ToolStatus.RUNNING) 0.35f else 0.14f), RoundedCornerShape(15.dp))
            .animateContentSize()
            .clickable { expanded = !expanded }
    ) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Text(when (message.toolName) {
                    "terminal" -> ">_"
                    "web_search", "web_extract" -> "◎"
                    "read_file", "write_file", "patch" -> "▤"
                    "delegate_task" -> "⑃"
                    else -> "⚒"
                }, color = tint, style = NbMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.toolName ?: "tool", color = Nb.Text, style = NbMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(if (message.toolStatus == ToolStatus.RUNNING) "RUNNING" else "DONE",
                        color = if (message.toolStatus == ToolStatus.RUNNING) Nb.Amber else Nb.Mint,
                        style = NbMono, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
                }
                Spacer(Modifier.height(3.dp))
                Text(message.toolSummary, color = Nb.Secondary, fontSize = 11.5.sp, maxLines = if (expanded) 4 else 1)
            }
            message.toolDuration?.let {
                Text(String.format(java.util.Locale.US, "%.1fs", it), color = Nb.Tertiary, style = NbMono, fontSize = 9.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text(if (expanded) "⌃" else "⌄", color = Nb.Tertiary, fontSize = 12.sp)
        }
        if (expanded) {
            Column(Modifier.padding(start = 11.dp, end = 11.dp, bottom = 11.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Nb.Line))
                Spacer(Modifier.height(9.dp))
                message.toolArgs?.takeIf { it.isNotBlank() }?.let {
                    Text("INPUT", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp, letterSpacing = 1.2.sp)
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Nb.Code).padding(8.dp)) {
                        Text(it.take(2000), color = Color(0xFFB9C6DE), style = NbMono, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                message.toolResult?.takeIf { it.isNotBlank() }?.let {
                    Text("OUTPUT", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp, letterSpacing = 1.2.sp)
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Nb.Code).padding(8.dp)) {
                        Text(it.take(4000), color = Color(0xFFB9C6DE), style = NbMono, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TypingDots(color: Color = Nb.Ion) {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(0.25f, 1f, animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
fun EmptyState(icon: String, title: String, detail: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Nb.Ion.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Text(icon, color = Nb.Ion, fontSize = 23.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = Nb.Text, style = NbTitle, fontSize = 18.sp)
        Spacer(Modifier.height(7.dp))
        Text(detail, color = Nb.Secondary, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.widthIn(max = 300.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.clip(CircleShape).background(Nb.Fill).border(1.dp, Nb.Line, CircleShape)
                .clickable { onAction() }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                Text(action, color = Nb.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun NbToast(text: String, isError: Boolean = false) {
    Row(
        Modifier.padding(horizontal = 18.dp, vertical = 8.dp).clip(CircleShape)
            .background(Nb.Raised.copy(alpha = 0.97f))
            .border(1.dp, if (isError) Nb.Rose.copy(alpha = 0.3f) else Nb.LineStrong, CircleShape)
            .shadow(18.dp, CircleShape, spotColor = Nb.Shade)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (isError) "!" else "✓", color = if (isError) Nb.Rose else Nb.Mint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(9.dp))
        Text(text, color = Nb.Text, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 5)
    }
}

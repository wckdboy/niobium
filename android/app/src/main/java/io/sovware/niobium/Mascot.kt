package io.sovware.niobium

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class Mascot(val patternId: String, val rows: List<String>, val accentHex: String? = null) {
    val valid: Boolean get() = rows.size == 8 && rows.all { it.length == 8 }
}

object Mascots {
    val curated = listOf(
        Mascot("ghost", listOf("..A..A..", ".AAAAAA.", "AAAAAAAA", "AADAADAA", "AAAAAAAA", "AAADDAAA", ".AAAAAA.", "..A..A..")),
        Mascot("cat", listOf("A......A", "AA....AA", "AAAAAAAA", "AALAALAA", "AAAAAAAA", "AAADDAAA", ".AAAAAA.", "..A..A..")),
        Mascot("owl", listOf("..AAAA..", ".AAAAAA.", "AALAALAA", "AADAADAA", "AALAALAA", "AADDDDAA", ".AAAAAA.", "..A..A..")),
        Mascot("robot", listOf("A..AA..A", "AAAAAAAA", "AADDDDAA", "AADDDDAA", "AAAAAAAA", "A.DDDD.A", "..AAAA..", "..A..A..")),
        Mascot("slime", listOf("........", "..AAAA..", ".AAAAAA.", "AAAAAAAA", "AALAALAA", "AADDDDAA", ".AAAAAA.", "..A..A..")),
        Mascot("bloom", listOf("..A..A..", ".AAAAAA.", "AALAAAAL", "AADDDDAA", "AALAAAAL", ".AAAAAA.", "..AAAA..", "...AA...")),
        Mascot("comet", listOf(".....A..", "....AA..", "...AAA..", "..AAAA..", ".AAAA...", "..AA....", ".A......", "........")),
        Mascot("ore", listOf("..AAA...", ".AAAAA..", "AALAAAA.", "AALAADAA", "AAADDAAA", ".AAAAAA.", "..AAAA..", "...AA..."))
    )

    fun seeded(seed: String): Mascot {
        when (seed.lowercase()) {
            "athena", "default" -> return curated[0]
            "forge" -> return curated[3]
            "scout" -> return curated[2]
            "hermes" -> return curated[5]
        }
        var hash = 41L
        for (c in seed) hash = hash * 31 + c.code
        return curated[((hash and 0x7FFFFFFF) % curated.size).toInt()]
    }

    fun random(excluding: String? = null): Mascot =
        curated.filter { it.patternId != excluding }.randomOrNull() ?: curated[0]
}

object MascotStore {
    private const val PREFS = "niobium_mascots"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, profile: String): Mascot {
        val raw = prefs(context).getString(profile.lowercase(), null) ?: return Mascots.seeded(profile.ifBlank { "niobium" })
        return runCatching {
            val json = JSONObject(raw)
            val rows = json.getJSONArray("rows")
            Mascot(json.optString("patternId", "custom"),
                (0 until rows.length()).map { rows.getString(it) },
                json.optString("accent").takeIf { it.isNotBlank() })
        }.getOrNull()?.takeIf { it.valid } ?: Mascots.seeded(profile.ifBlank { "niobium" })
    }

    fun save(context: Context, profile: String, mascot: Mascot) {
        if (!mascot.valid) return
        val json = JSONObject().apply {
            put("patternId", mascot.patternId)
            put("rows", JSONArray(mascot.rows))
            put("accent", mascot.accentHex ?: "")
        }
        prefs(context).edit().putString(profile.lowercase(), json.toString()).apply()
    }

    fun reset(context: Context, profile: String) {
        prefs(context).edit().remove(profile.lowercase()).apply()
    }
}

@Composable
fun MascotFace(pattern: Mascot, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = minOf(size.width, size.height) / 8f
        pattern.rows.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                if (ch == '.') return@forEachIndexed
                val color = when (ch) {
                    'D' -> Color(0xFF170D24)
                    'L' -> Nb.Ink.copy(alpha = 0.92f)
                    else -> if (row >= 6) accent.copy(alpha = 0.78f) else accent
                }
                drawRect(color, topLeft = Offset(col * cell, row * cell), size = Size(cell, cell))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MascotStudioSheet(
    profile: String,
    title: String,
    initialAccent: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(MascotStore.get(context, profile)) }
    var accentHex by remember { mutableStateOf(selected.accentHex ?: initialAccent) }
    var customRows by remember { mutableStateOf(selected.rows.map { it.map { c -> c != '.' } }) }
    var editing by remember { mutableStateOf(false) }
    val accent = Nb.colorFor(accentHex)
    val palette = listOf("#A78BFA", "#F0ABFC", "#5EEAD4", "#F5C97A", "#FDA4AF", "#C4B5FD", "#7C3AED", "#F472B6")

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    MascotTile(selected, accent, 104.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(title, color = Nb.Text, style = NbTitle, fontSize = 19.sp)
                    Text("$profile · chosen, random or drawn 8×8", color = Nb.Tertiary, fontSize = 10.5.sp)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Chosen", "8 × 8", modifier = Modifier.weight(1f))
                    Text("Surprise", color = Nb.Ion, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            selected = Mascots.random(selected.patternId)
                            customRows = selected.rows.map { it.map { c -> c != '.' } }
                            editing = false
                        }.padding(6.dp))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mascots.curated.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { item ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                                        selected = item.copy(accentHex = accentHex)
                                        customRows = item.rows.map { it.map { c -> c != '.' } }
                                        editing = false
                                    }) {
                                        MascotTile(item, accent, 62.dp)
                                    }
                                    Text(item.patternId, color = Nb.Secondary, style = NbMono, fontSize = 8.sp)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item {
                SectionHeader("Ink")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    palette.forEach { hex ->
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Nb.colorFor(hex))
                            .border(if (hex == accentHex) 2.dp else 1.dp,
                                if (hex == accentHex) Nb.Text else Nb.LineStrong, CircleShape)
                            .clickable { accentHex = hex; selected = selected.copy(accentHex = hex) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Custom", if (editing) "tap cells" else null, modifier = Modifier.weight(1f))
                    Text(if (editing) "Done" else "Draw", color = Nb.Ion, fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            if (!editing) customRows = selected.rows.map { it.map { c -> c != '.' } }
                            editing = !editing
                        }.padding(6.dp))
                }
                if (editing) {
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nb.Surface)
                        .border(1.dp, Nb.Line, RoundedCornerShape(16.dp)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (r in 0 until 8) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                for (c in 0 until 8) {
                                    Box(Modifier.weight(1f).aspectRatio(1f)
                                        .background(if (customRows[r][c]) accent else Nb.FillSoft)
                                        .clickable {
                                            customRows = customRows.mapIndexed { ri, rr ->
                                                if (ri == r) rr.mapIndexed { ci, v -> if (ci == c) !v else v } else rr
                                            }
                                            selected = selected.copy(patternId = "custom",
                                                rows = customRows.map { rr -> rr.joinToString("") { if (it) "A" else "." } },
                                                accentHex = accentHex)
                                        })
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Nb.Ion, Nb.IonDeep)))
                        .clickable {
                            MascotStore.save(context, profile, selected.copy(accentHex = accentHex))
                            onDismiss()
                        }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text("Use this mascot", color = Nb.OnIon, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Reset to a name-seeded mascot", color = Nb.Tertiary, fontSize = 10.5.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            MascotStore.reset(context, profile)
                            selected = Mascots.seeded(profile)
                            accentHex = selected.accentHex ?: initialAccent
                            customRows = selected.rows.map { it.map { c -> c != '.' } }
                        }.padding(4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun MascotTile(mascot: Mascot, accent: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), Nb.Ink)))
            .border(1.dp, Nb.LineStrong, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        MascotFace(mascot, accent, Modifier.size(size * 0.78f))
    }
}

package io.sovware.niobium

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CastMember(val id: String, val name: String, val companion: String, val companionName: String, val accentHex: String)

object Cast {
    val all = listOf(
        CastMember("orion", "Orion", "owl", "Strix", "#A78BFA"),
        CastMember("nova", "Nova", "cat", "Miso", "#F0ABFC"),
        CastMember("forge", "Forge", "drone", "Cog", "#F5C97A"),
        CastMember("sage", "Sage", "slime", "Moss", "#5EEAD4"),
        CastMember("nyx", "Nyx", "bat", "Dusk", "#FB7185"),
        CastMember("astra", "Astra", "bunny", "Pip", "#7DD3FC"),
        CastMember("vesper", "Vesper", "moth", "Lumen", "#C4B5FF"),
        CastMember("rook", "Rook", "hawk", "Kite", "#D6A45E")
    )

    fun seeded(profile: String): CastMember {
        val key = profile.lowercase()
        all.firstOrNull { it.id == key || it.name.lowercase() == key }?.let { return it }
        var hash = 41L
        for (c in key) hash = hash * 31 + c.code
        return all[((hash and 0x7FFFFFFF) % all.size).toInt()]
    }
}

object CastStore {
    private const val PREFS = "niobium_cast"
    fun get(context: Context, profile: String): CastMember {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(profile.lowercase(), null)
        return Cast.all.firstOrNull { it.id == id } ?: Cast.seeded(profile)
    }
    fun put(context: Context, profile: String, member: CastMember) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(profile.lowercase(), member.id).apply()
    }
}

@Composable
fun CastPortrait(member: CastMember, size: Dp = 64.dp, showCompanion: Boolean = true) {
    val accent = Nb.colorFor(member.accentHex)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.20f))
        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.24f), Nb.Surface)))
        .border(1.dp, Nb.Line, RoundedCornerShape(size * 0.20f))) {
        Image(
            painter = painterResource(charRes(member.id)),
            contentDescription = member.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size).padding(size * 0.03f)
        )
        if (showCompanion) {
            Box(Modifier.align(Alignment.BottomEnd).size(size * 0.30f).clip(RoundedCornerShape(size * 0.10f))
                .background(Nb.Ink).border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(size * 0.10f))) {
                Image(painterResource(compRes(member.companion)), null,
                    contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.26f).align(Alignment.Center))
            }
        }
    }
}

private fun charRes(id: String): Int = when (id) {
    "orion" -> R.drawable.char_orion
    "nova" -> R.drawable.char_nova
    "forge" -> R.drawable.char_forge
    "sage" -> R.drawable.char_sage
    "nyx" -> R.drawable.char_nyx
    "astra" -> R.drawable.char_astra
    "vesper" -> R.drawable.char_vesper
    "rook" -> R.drawable.char_rook
    else -> R.drawable.char_orion
}

private fun compRes(id: String): Int = when (id) {
    "owl" -> R.drawable.comp_owl
    "cat" -> R.drawable.comp_cat
    "drone" -> R.drawable.comp_drone
    "slime" -> R.drawable.comp_slime
    "bat" -> R.drawable.comp_bat
    "bunny" -> R.drawable.comp_bunny
    "moth" -> R.drawable.comp_moth
    "hawk" -> R.drawable.comp_hawk
    else -> R.drawable.comp_cat
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CastStudioSheet(profile: String, title: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CastStore.get(context, profile)) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            item {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    CastPortrait(selected, 140.dp)
                    androidx.compose.material3.Text(title, color = Nb.Text, style = NbTitle, fontSize = 20.sp)
                    androidx.compose.material3.Text("${selected.companionName} · companion", color = Nb.Tertiary, style = NbMono, fontSize = 9.sp)
                }
            }
            item {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    SectionHeader("The cast", "32 × 32", modifier = androidx.compose.ui.Modifier.weight(1f))
                    androidx.compose.material3.Text("Surprise", color = Nb.Ion, fontSize = 12.sp,
                        modifier = androidx.compose.ui.Modifier.clickable {
                            selected = Cast.all.filter { it.id != selected.id }.random()
                            CastStore.put(context, profile, selected)
                        }.padding(6.dp))
                }
            }
            items(4) { row ->
                androidx.compose.foundation.layout.Row(
                    androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    Cast.all.drop(row * 2).take(2).forEach { member ->
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (selected.id == member.id) Nb.Ion.copy(alpha = 0.10f) else Nb.FillSoft)
                                .border(1.dp, if (selected.id == member.id) Nb.Ion.copy(alpha = 0.35f) else Nb.Line, RoundedCornerShape(14.dp))
                                .clickable { selected = member; CastStore.put(context, profile, member) }
                                .padding(8.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            CastPortrait(member, 62.dp, showCompanion = false)
                            androidx.compose.material3.Text(member.name, color = Nb.Text, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            androidx.compose.material3.Text(member.companionName, color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
                        }
                    }
                }
            }
            item {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxWidth().clip(CircleShape)
                        .background(Nb.Ion)
                        .clickable { CastStore.put(context, profile, selected); onDismiss() }
                        .padding(vertical = 13.dp), contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text("Use this cast", color = Nb.OnIon, fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }
    }
}

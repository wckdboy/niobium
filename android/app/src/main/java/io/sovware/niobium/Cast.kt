package io.sovware.niobium

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

data class CastMember(val id: String, val name: String, val portrait: String, val companion: String, val companionName: String, val accentHex: String, val recipe: CharacterRecipe? = null)

object Cast {
    val all = listOf(
        CastMember("orion", "Orion", "p02", "owl", "Strix", "#A78BFA", CharacterRecipe.cast["orion"]),
        CastMember("nova", "Nova", "p01", "cat", "Miso", "#F0ABFC", CharacterRecipe.cast["nova"]),
        CastMember("forge", "Forge", "p20", "drone", "Cog", "#F5C97A", CharacterRecipe.cast["forge"]),
        CastMember("sage", "Sage", "p04", "slime", "Moss", "#5EEAD4", CharacterRecipe.cast["sage"]),
        CastMember("nyx", "Nyx", "p08", "bat", "Dusk", "#FB7185", CharacterRecipe.cast["nyx"]),
        CastMember("astra", "Astra", "p14", "bunny", "Pip", "#7DD3FC", CharacterRecipe.cast["astra"]),
        CastMember("vesper", "Vesper", "p13", "moth", "Lumen", "#C4B5FF", CharacterRecipe.cast["vesper"]),
        CastMember("rook", "Rook", "p19", "hawk", "Kite", "#D6A45E", CharacterRecipe.cast["rook"])
    )

    val portraitIds: List<String> = (1..24).map { "p%02d".format(it) }

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
        var member = Cast.all.firstOrNull { it.id == id } ?: Cast.seeded(profile)
        val portrait = context.getSharedPreferences("niobium_portraits", Context.MODE_PRIVATE)
            .getString(profile.lowercase(), null)
        if (!portrait.isNullOrBlank()) member = member.copy(portrait = portrait)
        val companion = context.getSharedPreferences("niobium_companions", Context.MODE_PRIVATE)
            .getString(profile.lowercase(), null)
        if (!companion.isNullOrBlank()) {
            val known = Cast.all.firstOrNull { it.companion == companion }
            member = member.copy(companion = companion, companionName = known?.companionName ?: member.companionName)
        }
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        saved.getString("recipe." + profile.lowercase(), null)?.let { raw ->
            runCatching { CharacterRecipe.parse(org.json.JSONObject(raw)) }.getOrNull()?.takeIf { it.valid }?.let {
                member = member.copy(recipe = it)
            }
        }
        saved.getString("companionName." + profile.lowercase(), null)?.let { member = member.copy(companionName = it) }
        return member
    }
    fun restore(context: Context, profile: String, metadata: org.json.JSONObject) {
        val spec = metadata.o("pixel_spec")
        val appearance = metadata.o("appearance")
        val character = spec?.o("character")
        val archetype = appearance?.s("archetype") ?: character?.s("archetype") ?: return
        var member = Cast.all.firstOrNull { it.id == archetype } ?: return
        val recipe = appearance?.o("recipe") ?: character?.o("recipe")
        if (recipe != null) runCatching { CharacterRecipe.parse(recipe) }.getOrNull()?.takeIf { it.valid }?.let { member = member.copy(recipe = it) }
        val companion = appearance?.s("companion") ?: spec?.o("companion")?.s("kind") ?: member.companion
        val name = appearance?.s("companion_name") ?: spec?.o("companion")?.s("name") ?: member.companionName
        if (companion !in PixelBuilder.companions || name.isEmpty() || name.length > 24) return
        member = member.copy(companion = companion, companionName = name)
        if (get(context, profile) != member) put(context, profile, member)
    }
    fun put(context: Context, profile: String, member: CastMember) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("recipe." + profile.lowercase(), member.recipe?.toJson()?.toString())
            .putString("companionName." + profile.lowercase(), member.companionName).apply()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(profile.lowercase(), member.id).apply()
        context.getSharedPreferences("niobium_portraits", Context.MODE_PRIVATE).edit().putString(profile.lowercase(), member.portrait).apply()
        context.getSharedPreferences("niobium_companions", Context.MODE_PRIVATE).edit().putString(profile.lowercase(), member.companion).apply()
    }
}

@Composable
fun CastPortrait(member: CastMember, size: Dp = 64.dp, showCompanion: Boolean = true) {
    val accent = Nb.colorFor(member.accentHex)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.20f))
        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.24f), Nb.Surface)))
        .border(1.dp, Nb.Line, RoundedCornerShape(size * 0.20f))) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val sprite = remember(member.recipe, member.companion) { CharacterEngine.authoredImage(context, member) ?: member.recipe?.let { CharacterEngine.image(context, it) } }
        if (sprite != null) {
            Image(bitmap = sprite, contentDescription = member.name + ", adult pixel character",
                contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                modifier = Modifier.size(size).padding(size * 0.03f))
        } else {
            Image(painterResource(portraitRes(member.portrait)), member.name,
                contentScale = ContentScale.Fit, modifier = Modifier.size(size).padding(size * 0.03f))
        }
        if (showCompanion && !CharacterEngine.usesAuthored(member)) {
            Box(Modifier.align(Alignment.BottomEnd).size(size * 0.30f).clip(RoundedCornerShape(size * 0.10f))
                .background(Nb.Ink).border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(size * 0.10f))) {
                Image(painterResource(compRes(member.companion)), null,
                    contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.26f).align(Alignment.Center))
            }
        }
    }
}

private fun portraitRes(id: String): Int = when (id) {
    "p01" -> R.drawable.portrait_p01
    "p02" -> R.drawable.portrait_p02
    "p03" -> R.drawable.portrait_p03
    "p04" -> R.drawable.portrait_p04
    "p05" -> R.drawable.portrait_p05
    "p06" -> R.drawable.portrait_p06
    "p07" -> R.drawable.portrait_p07
    "p08" -> R.drawable.portrait_p08
    "p09" -> R.drawable.portrait_p09
    "p10" -> R.drawable.portrait_p10
    "p11" -> R.drawable.portrait_p11
    "p12" -> R.drawable.portrait_p12
    "p13" -> R.drawable.portrait_p13
    "p14" -> R.drawable.portrait_p14
    "p15" -> R.drawable.portrait_p15
    "p16" -> R.drawable.portrait_p16
    "p17" -> R.drawable.portrait_p17
    "p18" -> R.drawable.portrait_p18
    "p19" -> R.drawable.portrait_p19
    "p20" -> R.drawable.portrait_p20
    "p21" -> R.drawable.portrait_p21
    "p22" -> R.drawable.portrait_p22
    "p23" -> R.drawable.portrait_p23
    "p24" -> R.drawable.portrait_p24
    else -> R.drawable.portrait_p02
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
fun CastStudioSheet(vm: NiobiumViewModel, profile: String, title: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var saving by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CastStore.get(context, if (vm.state.engine == AgentEngine.DSH) "dsh:" + profile else profile)) }
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
                    SectionHeader("The cast", "48 × 48", modifier = androidx.compose.ui.Modifier.weight(1f))
                    androidx.compose.material3.Text("Surprise", color = Nb.Ion, fontSize = 12.sp,
                        modifier = androidx.compose.ui.Modifier.clickable {
                            selected = Cast.all.filter { it.id != selected.id }.random()
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
                                .clickable { selected = member }
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
                SectionHeader("Make it yours", "48 × 48 layers")
                listOf("palette", "hair", "outfit", "accessory").forEach { field ->
                    var expanded by remember { mutableStateOf(false) }
                    val options = when (field) {
                        "palette" -> CharacterRecipe.cast.keys.sorted()
                        "hair" -> CharacterRecipe.cast.values.map { it.hair }.distinct().sorted()
                        "outfit" -> CharacterRecipe.cast.values.map { it.outfit }.distinct().sorted()
                        else -> listOf("none", "visor", "goggles", "antlers", "cat_ears", "circlet", "veil", "headwrap")
                    }
                    Box {
                        androidx.compose.material3.Text(field.replaceFirstChar { it.uppercase() } + "  ↕",
                            color = Nb.Secondary, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(12.dp))
                        androidx.compose.material3.DropdownMenu(expanded, { expanded = false }) {
                            options.forEach { option ->
                                androidx.compose.material3.DropdownMenuItem(text = { androidx.compose.material3.Text(option.replace('_', ' ')) }, onClick = {
                                    val recipe = selected.recipe ?: CharacterRecipe.cast.getValue(selected.id)
                                    selected = selected.copy(recipe = when (field) {
                                        "palette" -> recipe.copy(palette = option)
                                        "hair" -> recipe.copy(hair = option)
                                        "outfit" -> recipe.copy(outfit = option)
                                        else -> recipe.copy(accessory = option)
                                    })
                                    expanded = false
                                })
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader("Companion")
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(7.dp))
                androidx.compose.foundation.layout.Row(
                    androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)
                ) {
                    Cast.all.map { it.companion }.distinct().forEach { kind ->
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                .background(if (selected.companion == kind) Nb.Ion.copy(alpha = 0.10f) else Nb.FillSoft)
                                .border(1.dp, if (selected.companion == kind) Nb.Ion.copy(alpha = 0.35f) else Nb.Line, RoundedCornerShape(9.dp))
                                .clickable {
                                    selected = selected.copy(companion = kind,
                                        companionName = Cast.all.firstOrNull { it.companion == kind }?.companionName ?: selected.companionName)
                                        }.padding(5.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Image(painterResource(compRes(kind)), null, modifier = androidx.compose.ui.Modifier.size(30.dp))
                            androidx.compose.material3.Text(kind, color = Nb.Tertiary, style = NbMono, fontSize = 6.5.sp)
                        }
                    }
                }
            }
            item {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxWidth().clip(CircleShape)
                        .background(Nb.Ion)
                         .clickable(enabled = !saving) {
                            saving = true
                            vm.saveAppearance(context, selected, profile) { success -> saving = false; if (success) onDismiss() }
                        }
                        .padding(vertical = 13.dp), contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text(if (saving) "Saving…" else "Use this character", color = Nb.OnIon, fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }
    }
}

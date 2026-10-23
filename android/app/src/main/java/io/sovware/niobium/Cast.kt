package io.sovware.niobium

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

data class CastMember(val id: String, val name: String, val portrait: String, val companion: String, val companionName: String, val accentHex: String, val recipe: CharacterRecipe? = null, val look: PixelLook? = null, val companionArt: PixelArt? = null)

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
        saved.getString("look." + profile.lowercase(), null)?.let { raw ->
            runCatching { PixelLook.parse(org.json.JSONObject(raw)) }.getOrNull()?.takeIf { it.valid }?.let { member = member.copy(look = it) }
        }
        saved.getString("companionArt." + profile.lowercase(), null)?.let { raw ->
            runCatching { PixelArt.parse(org.json.JSONObject(raw)) }.getOrNull()?.takeIf { it.size == 16 }?.let { member = member.copy(companionArt = it) }
        }
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
        val look = if (appearance?.has("look") == true) appearance.o("look") else character?.o("look")
        if (look != null) runCatching { PixelLook.parse(look) }.getOrNull()?.takeIf { it.valid }?.let { member = member.copy(look = it) }
        val companion = appearance?.s("companion") ?: spec?.o("companion")?.s("kind") ?: member.companion
        val name = appearance?.s("companion_name") ?: spec?.o("companion")?.s("name") ?: member.companionName
        if (companion !in PixelBuilder.companions || name.isEmpty() || name.length > 24) return
        member = member.copy(companion = companion, companionName = name)
        val art = if (appearance?.has("companion_art") == true) appearance.o("companion_art") else spec?.o("companion")?.o("art")
        art?.let { runCatching { PixelArt.parse(it) }.getOrNull()?.takeIf { it.size == 16 }?.let { parsed -> member = member.copy(companionArt = parsed) } }
        if (get(context, profile) != member) put(context, profile, member)
    }
    fun put(context: Context, profile: String, member: CastMember) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("recipe." + profile.lowercase(), member.recipe?.toJson()?.toString())
            .putString("look." + profile.lowercase(), member.look?.toJson()?.toString())
            .putString("companionArt." + profile.lowercase(), member.companionArt?.toJson()?.toString())
            .putString("companionName." + profile.lowercase(), member.companionName).apply()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(profile.lowercase(), member.id).apply()
        context.getSharedPreferences("niobium_portraits", Context.MODE_PRIVATE).edit().putString(profile.lowercase(), member.portrait).apply()
        context.getSharedPreferences("niobium_companions", Context.MODE_PRIVATE).edit().putString(profile.lowercase(), member.companion).apply()
    }
}

@Composable
fun CastPortrait(member: CastMember, size: Dp = 64.dp, showCompanion: Boolean = true) {
    val accent = Nb.colorFor(member.accentHex)
    val blink = portraitBlink(member.recipe, member.look == null && size >= 96.dp && !CharacterEngine.usesAuthored(member))
    Box(Modifier.size(size).clip(PixelButtonShape).background(accent.copy(alpha = 0.08f))) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val sprite = remember(member.id, member.look, member.recipe, blink) {
            member.look?.let { CharacterEngine.image(context, it) }
                ?: CharacterEngine.authoredImage(context, member) ?: member.recipe?.let { CharacterEngine.image(context, it, blink) }
        }
        val spriteSide = with(androidx.compose.ui.platform.LocalDensity.current) {
            (kotlin.math.floor(size.toPx() / 48f).toInt().coerceAtLeast(1) * 48).toDp()
        }
        if (sprite != null) {
            Image(bitmap = sprite, contentDescription = member.name + ", adult pixel character",
                contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                modifier = Modifier.size(spriteSide).align(Alignment.Center))
        } else {
            Image(painterResource(portraitRes(member.portrait)), member.name,
                contentScale = ContentScale.Fit, modifier = Modifier.size(spriteSide).align(Alignment.Center))
        }
        if (showCompanion) {
            Box(Modifier.align(Alignment.BottomEnd).padding(size * 0.02f).size(size * 0.30f)) {
                Box(Modifier.align(Alignment.Center)) { FamiliarSprite(member.companion, size * 0.26f, member.companionArt) }
            }
        }
    }
}

// Only large previews blink. Composition, lifecycle and system motion scale all
// own cancellation; the renderer caches two exact frames, with no per-frame draw.
@Composable
internal fun portraitBlink(recipe: CharacterRecipe?, enabled: Boolean): Boolean {
    var blink by remember(recipe) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(recipe, enabled, lifecycle) {
        blink = false
        if (!enabled || recipe == null) return@LaunchedEffect
        val motion = currentCoroutineContext()[MotionDurationScale]
        snapshotFlow { motion?.scaleFactor ?: 1f }.collectLatest { scale ->
            blink = false
            if (scale > 0f) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    val offset = recipe.toJson().toString().toByteArray(Charsets.UTF_8).sumOf { it.toInt() and 255 } % 1400
                    while (isActive) {
                        delay(4200L + offset)
                        blink = true
                        delay(140)
                        blink = false
                    }
                } finally { blink = false }
            }
        }
    }
    return blink && enabled
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

internal fun compRes(id: String): Int = when (id) {
    "owl" -> R.drawable.comp_owl
    "cat" -> R.drawable.comp_cat
    "drone" -> R.drawable.comp_drone
    "slime" -> R.drawable.comp_slime
    "bat" -> R.drawable.comp_bat
    "bunny" -> R.drawable.comp_bunny
    "moth" -> R.drawable.comp_moth
    "hawk" -> R.drawable.comp_hawk
    "axolotl" -> R.drawable.comp_axolotl
    "imp" -> R.drawable.comp_imp
    "wisp" -> R.drawable.comp_wisp
    "mimic" -> R.drawable.comp_mimic
    else -> R.drawable.comp_cat
}


@Composable
fun FamiliarSprite(kind: String, size: Dp = 32.dp, art: PixelArt? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resource = compRes(kind)
    val bitmap = remember(resource) { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, resource) }
    val side = with(androidx.compose.ui.platform.LocalDensity.current) {
        (kotlin.math.floor(size.toPx()/16f).toInt().coerceAtLeast(1)*16).toDp()
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val custom = remember(art) { art?.let(CharacterEngine::image) }
    if (custom != null) Image(bitmap = custom, contentDescription = null, contentScale = ContentScale.Fit,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.None, modifier = Modifier.size(side))
    else Image(bitmap, null, filterQuality = androidx.compose.ui.graphics.FilterQuality.None, modifier = Modifier.size(side))
    }
}

internal fun characterFieldLabel(field: String) = mapOf(
    "body" to "Build", "palette" to "Colors", "skin" to "Skin tone", "hair_tone" to "Hair color",
    "hair" to "Hair style", "face" to "Expression", "outfit" to "Outfit", "accessory" to "Accessory", "detail" to "Detail"
)[field] ?: field.replaceFirstChar { it.uppercase() }

internal fun characterValueLabel(value: String, field: String): String {
    if (value == "auto") return "Palette default"
    if (field == "body") return if (value == "man") "Masculine" else "Feminine"
    if (field == "hair") mapOf("silver_swept" to "Side sweep", "violet_bob" to "Bob", "cat_black" to "Long layers",
        "white_pilot" to "Spiked crop", "silver_bob" to "Soft bob")[value]?.let { return it }
    return value.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelLookEditor(look: PixelLook, history: List<PixelLook>, enabled: Boolean = true,
                    onHistoryChange: (List<PixelLook>) -> Unit, onChange: (PixelLook) -> Unit) {
    var browsing by remember { mutableStateOf(false) }
    var family by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    fun choose(next: PixelLook) {
        if (next != look) { onHistoryChange((history + look).takeLast(12)); onChange(next) }
    }
    @Composable fun tiles(entries: List<PixelLookEntry>, close: Boolean = false) {
        entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { entry ->
                    WardrobeTile(entry, look.portrait == entry.id, enabled, Modifier.weight(1f)) {
                        choose(look.copy(portrait = entry.id, customArt = null)); if (close) browsing = false
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles(PixelWardrobe.entries.take(8))
        PixelButton("Browse 32 looks · " + look.entry.label, PixelGlyph.PIXELS, Modifier.fillMaxWidth().testTag("look.browse"),
            secondary = true, enabled = enabled) { browsing = true }
        val artContext = androidx.compose.ui.platform.LocalContext.current
        PixelArtistButton("pixel", CharacterEngine.image(artContext, look)?.asAndroidBitmap()) { art -> choose(look.copy(accent = "auto", customArt = art)) }
        if (look.customArt == null) LookColorMenu("Outfit", listOf("auto") + PixelLook.accents, look.accent, Modifier.fillMaxWidth(), enabled) { choose(look.copy(accent = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelButton("Surprise", PixelGlyph.SPARK, Modifier.weight(1f).testTag("look.surprise"), secondary = true, enabled = enabled) {
                val choices = PixelLook.portraits.filter { it != look.portrait }
                choose(PixelLook(choices[CharacterRandom(java.util.UUID.randomUUID().toString()).index(choices.size)]))
            }
            PixelButton("Remix", PixelGlyph.SPARK, Modifier.weight(1f).testTag("look.remix"), secondary = true, enabled = enabled && look.customArt == null) {
                choose(look.remixed(java.util.UUID.randomUUID().toString()))
            }
            PixelButton("Undo", PixelGlyph.BACK, Modifier.weight(1f).testTag("look.undo"), secondary = true, enabled = enabled && history.isNotEmpty()) {
                history.lastOrNull()?.let { onChange(it); onHistoryChange(history.dropLast(1)) }
            }
        }
    }
    if (browsing) ModalBottomSheet(onDismissRequest = { browsing = false }, containerColor = Nb.Void,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().testTag("wardrobe"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Wardrobe", color = Nb.Text, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            item { OutlinedTextField(search, { search = it.take(80) }, Modifier.fillMaxWidth(), placeholder = { Text("Try vampire, cyborg, frost…") }, singleLine = true) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "Arcane", "Cyber", "Wild", "Dark").forEach { item ->
                    FilterChip(family == item, { family = item }, label = { Text(item) }, shape = PixelButtonShape,
                        modifier = Modifier.heightIn(min = 44.dp).testTag("wardrobe.family." + item.lowercase()))
                }
            } }
            val filtered = PixelWardrobe.entries.filter {
                (family == "All" || it.family == family) && (search.isBlank() || (listOf(it.label, it.family) + it.tags).joinToString(" ").contains(search.trim(), ignoreCase = true))
            }
            filtered.chunked(4).forEach { row -> item(key = row.first().id) { tiles(row, close = true) } }
            if (filtered.isEmpty()) item { Text("No matching looks", color = Nb.Secondary, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun WardrobeTile(entry: PixelLookEntry, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val base = Cast.all.firstOrNull { it.id == entry.archetype } ?: Cast.all.first()
    Column(modifier.clip(PixelButtonShape).background(if (selected) Nb.Ion.copy(alpha = 0.12f) else Nb.FillSoft)
        .border(1.dp, if (selected) Nb.Ion else androidx.compose.ui.graphics.Color.Transparent, PixelButtonShape)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = entry.label + " look" }.testTag("look." + entry.id).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CastPortrait(base.copy(name = entry.label, look = PixelLook(entry.id)), 58.dp, showCompanion = false)
        Text(entry.label, color = Nb.Text, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun LookColorMenu(title: String, options: List<String>, value: String, modifier: Modifier, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(PixelButtonShape).background(Nb.FillSoft)
            .clickable(enabled, role = Role.Button) { expanded = true }.semantics { contentDescription = title + " color"; stateDescription = value }
            .testTag("look.color." + title.lowercase()).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Nb.Secondary, fontSize = 11.sp)
            Text(if (value == "auto") "Original" else value.replaceFirstChar { it.uppercase() }, color = Nb.Text, fontSize = 11.sp, maxLines = 1)
        }
        DropdownMenu(expanded && enabled, { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(if (option == "auto") "Original" else option.replaceFirstChar { it.uppercase() }) },
                    onClick = { onChange(option); expanded = false })
            }
        }
    }
}

@Composable
fun CharacterCustomizer(recipe: CharacterRecipe, archetype: String, pinned: Set<String>, enabled: Boolean = true,
                        onPinsChange: (Set<String>) -> Unit, onChange: (CharacterRecipe) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelButton("Remix look", PixelGlyph.SPARK, Modifier.fillMaxWidth().testTag("character.remix"),
            secondary = true, enabled = enabled && pinned.size < CharacterRecipe.fields.size) {
            onChange(recipe.remixed(archetype, java.util.UUID.randomUUID().toString(), pinned))
        }
        Text("Pin your favorites, then remix the rest. Pinning a palette-default color also keeps its palette.",
            color = Nb.Tertiary, fontSize = 11.sp, lineHeight = 16.sp)
        CharacterRecipe.fields.forEach { field ->
            var expanded by remember(field) { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp))
                        .background(Nb.FillSoft).clickable(enabled = enabled) { expanded = true }
                        .testTag("character." + field).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(characterFieldLabel(field), color = Nb.Secondary, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(characterValueLabel(recipe.value(field), field), color = Nb.Text, fontSize = 12.sp, maxLines = 1)
                        Box(Modifier.rotate(-90f)) { PixelIcon(PixelGlyph.BACK, Nb.Tertiary, 12.dp) }
                    }
                    DropdownMenu(expanded && enabled, { expanded = false }) {
                        CharacterRecipe.options(field).forEach { option ->
                            DropdownMenuItem(text = { Text(characterValueLabel(option, field)) }, onClick = {
                                onChange(recipe.withValue(field, option)); expanded = false
                            })
                        }
                    }
                }
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (field in pinned) Nb.Ion.copy(alpha = 0.12f) else Nb.FillSoft)
                    .clickable(enabled = enabled, role = Role.Button) {
                        onPinsChange(if (field in pinned) pinned - field else pinned + field)
                    }.semantics {
                        contentDescription = (if (field in pinned) "Unpin " else "Pin ") + characterFieldLabel(field).lowercase()
                        stateDescription = if (field in pinned) "Pinned" else "Unpinned"
                    }.testTag("character.pin." + field), contentAlignment = Alignment.Center) {
                    PixelIcon(PixelGlyph.PIN, if (field in pinned) Nb.Ion else Nb.Tertiary, 20.dp)
                }
            }
        }
        TextButton(onClick = { onChange(CharacterRecipe.cast[archetype] ?: CharacterRecipe.cast.getValue("orion")) },
            enabled = enabled, modifier = Modifier.testTag("character.restore")) {
            Text("Restore original cast look", color = Nb.Secondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun FamiliarPicker(kind: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelBuilder.companions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (kind == option) Nb.Ion.copy(alpha = 0.12f) else Nb.FillSoft)
                        .border(1.dp, if (kind == option) Nb.Ion.copy(alpha = 0.5f) else Nb.Line, RoundedCornerShape(12.dp))
                        .selectable(selected = kind == option, enabled = enabled, role = Role.RadioButton) { onChange(option) }
                        .semantics { contentDescription = option.replaceFirstChar { it.uppercase() } + " companion" }
                        .testTag("familiar." + option).padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FamiliarSprite(option, 32.dp)
                        Text(option.replaceFirstChar { it.uppercase() }, color = if (kind == option) Nb.Text else Nb.Secondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastStudioSheet(vm: NiobiumViewModel, profile: String, title: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var saving by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(CastStore.get(context, if (vm.state.engine == AgentEngine.DSH) "dsh:" + profile else profile)) }
    var pinned by remember { mutableStateOf(setOf("body", "skin")) }
    var lookHistory by remember { mutableStateOf(emptyList<PixelLook>()) }
    var originalControls by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { !saving || it != SheetValue.Hidden })) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CastPortrait(selected, 160.dp)
                    Text(title, color = Nb.Text, style = NbTitle, fontSize = 22.sp)
                    Text(selected.companionName + " · " + selected.companion.replaceFirstChar { it.uppercase() }, color = Nb.Secondary, fontSize = 11.sp)
                }
            }
            item {
                SectionHeader("Make it yours", "48 × 48")
                Spacer(Modifier.height(10.dp))
                if (selected.look != null) {
                    PixelLookEditor(selected.look!!, lookHistory, !saving, { lookHistory = it }) { selected = selected.copy(look = it) }
                } else {
                    PixelButton("Choose a new portrait", PixelGlyph.SPARK, Modifier.fillMaxWidth(), secondary = true, enabled = !saving) {
                        selected = selected.copy(look = PixelLook(selected.id))
                    }
                    TextButton(onClick = { originalControls = !originalControls }) { Text("Original controls", color = Nb.Secondary) }
                    if (originalControls) CharacterCustomizer(selected.recipe ?: CharacterRecipe.cast.getValue(selected.id), selected.id, pinned,
                        enabled = !saving, onPinsChange = { pinned = it }) { selected = selected.copy(recipe = it) }
                }
            }
            item {
                SectionHeader("Creature")
                Spacer(Modifier.height(10.dp))
                FamiliarPicker(selected.companion, !saving) { selected = selected.copy(companion = it, companionArt = null) }
                if (!saving) CompanionArtEditor(selected.companion, selected.companionArt) { selected = selected.copy(companionArt = it) }
            }
            item { SectionHeader("The cast", "eight starting points") }
            items(4) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Cast.all.drop(row * 2).take(2).forEach { member ->
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (selected.id == member.id) Nb.Ion.copy(alpha = 0.1f) else Nb.FillSoft)
                            .border(1.dp, if (selected.id == member.id) Nb.Ion.copy(alpha = 0.4f) else Nb.Line, RoundedCornerShape(14.dp))
                            .clickable(enabled = !saving) { selected = member.copy(companion = selected.companion, companionName = selected.companionName) }
                            .padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CastPortrait(member, 54.dp, showCompanion = false)
                            Text(member.name, color = Nb.Text, style = NbTitle, fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                PixelButton(if (saving) "Saving…" else "Use this character", PixelGlyph.PIXELS, Modifier.fillMaxWidth(), enabled = !saving) {
                    saving = true
                    val appearance = selected
                    vm.saveAppearance(context, appearance, profile) { success -> saving = false; if (success) onDismiss() }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

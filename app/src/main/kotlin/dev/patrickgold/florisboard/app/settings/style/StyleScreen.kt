/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.style

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.rememberMicButtonOn
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.florisboard.dictate.DictateFloatingButtonDesign
import dev.patrickgold.florisboard.dictate.DictateMicCharm
import dev.patrickgold.florisboard.dictate.DictateMicCollection
import dev.patrickgold.florisboard.dictate.DictateMicColors
import dev.patrickgold.florisboard.dictate.DictateMicMove
import dev.patrickgold.florisboard.dictate.MicCharmPainter
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes

/**
 * The Style tab: what the floating mic button looks like. A live preview up top, the looks on their
 * collection shelves, then what the chosen look allows: a colour only when it is one colour at heart (a
 * sunflower or an "On Air" sign keeps its own), and whether it moves. Each look moves its own way, so
 * there is no choice of movement to get wrong, only on or off.
 */
@Composable
fun StyleScreen() = FlorisScreen {
    title = stringRes(R.string.nav__style)
    navigationIconVisible = false
    previewFieldVisible = false

    val navController = LocalNavController.current

    content {
        val charm by prefs.dictate.floatingButtonCharm.collectAsState()
        val charmColor by prefs.dictate.floatingButtonCharmColor.collectAsState()
        val classicColor by prefs.dictate.floatingButtonColor.collectAsState()
        val animated by prefs.dictate.floatingButtonCharmAnimated.collectAsState()
        val buttonOn = rememberMicButtonOn()
        val classic = charm == DictateMicCharm.CLASSIC
        // The colour each look is painted in, the same way the floating button resolves it.
        val shownColor = { look: DictateMicCharm ->
            if (look == DictateMicCharm.CLASSIC) classicColor.toArgb() else look.resolveColor(charmColor.toArgb())
        }

        Text(
            stringRes(R.string.style__subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        )

        StylePreview(charm = charm, color = shownColor(charm), moving = animated || classic)

        if (!buttonOn) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringRes(R.string.style__button_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { navController.navigate(Routes.Settings.DictateFloatingButton) }) {
                    Text(stringRes(R.string.home__turn_on))
                }
            }
        }

        DictateMicCollection.entries.forEach { collection ->
            CollectionShelf(
                collection = collection,
                selected = charm,
                colorOf = shownColor,
                onPick = { prefs.dictate.floatingButtonCharm.set(it) },
            )
        }

        SectionHeading(stringRes(R.string.style__color))
        when {
            classic -> {
                // The classic button still comes in the designs it always had, in any colour.
                ListPreference(
                    prefs.dictate.floatingButtonDesign,
                    icon = Icons.Default.Brush,
                    modifier = Modifier.settingsSearchAnchor("dictate__floating_button_design_title"),
                    title = stringRes(R.string.dictate__floating_button_design_title),
                    entries = listPrefEntries {
                        entry(
                            DictateFloatingButtonDesign.PILL,
                            stringRes(R.string.dictate__floating_button_design_pill),
                            stringRes(R.string.dictate__floating_button_design_pill_summary),
                        )
                        entry(
                            DictateFloatingButtonDesign.RING,
                            stringRes(R.string.dictate__floating_button_design_ring),
                            stringRes(R.string.dictate__floating_button_design_ring_summary),
                        )
                        entry(
                            DictateFloatingButtonDesign.ORB,
                            stringRes(R.string.dictate__floating_button_design_orb),
                            stringRes(R.string.dictate__floating_button_design_orb_summary),
                        )
                        entry(
                            DictateFloatingButtonDesign.CLOUD,
                            stringRes(R.string.dictate__floating_button_design_cloud),
                            stringRes(R.string.dictate__floating_button_design_cloud_summary),
                        )
                        entry(
                            DictateFloatingButtonDesign.AURORA,
                            stringRes(R.string.dictate__floating_button_design_aurora),
                            stringRes(R.string.dictate__floating_button_design_aurora_summary),
                        )
                        entry(
                            DictateFloatingButtonDesign.LATTICE,
                            stringRes(R.string.dictate__floating_button_design_lattice),
                            stringRes(R.string.dictate__floating_button_design_lattice_summary),
                        )
                    },
                )
                SwatchRow(
                    original = null,
                    picked = classicColor.toArgb(),
                    onPick = { prefs.dictate.floatingButtonColor.set(Color(it)) },
                )
                ColorPickerPreference(
                    prefs.dictate.floatingButtonColor,
                    icon = Icons.Default.ColorLens,
                    modifier = Modifier.settingsSearchAnchor("dictate__floating_button_color_title"),
                    title = stringRes(R.string.style__more_colors),
                    defaultValueLabel = stringRes(R.string.action__default),
                    showAlphaSlider = false,
                    defaultColors = ColorMappings.colors,
                    enableAdvancedLayout = true,
                )
            }
            charm.tintable -> {
                SwatchRow(
                    original = charm,
                    picked = charmColor.toArgb(),
                    onPick = { prefs.dictate.floatingButtonCharmColor.set(Color(it)) },
                )
                // "Default" here is the look's own colours again.
                ColorPickerPreference(
                    prefs.dictate.floatingButtonCharmColor,
                    icon = Icons.Default.ColorLens,
                    title = stringRes(R.string.style__more_colors),
                    defaultValueLabel = stringRes(R.string.style__color_original),
                    showAlphaSlider = false,
                    defaultColors = ColorMappings.colors,
                    enableAdvancedLayout = true,
                )
            }
            else -> Text(
                stringRes(R.string.style__color_fixed, "charm" to charmLabel(charm)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        // The classic designs bring their own movement; every other look gets its own, on or off.
        if (!classic) {
            SectionHeading(stringRes(R.string.style__motion))
            SwitchPreference(
                prefs.dictate.floatingButtonCharmAnimated,
                icon = Icons.Default.Animation,
                title = stringRes(R.string.style__move_title),
                summaryOn = moveLabel(charm.move),
                summaryOff = stringRes(R.string.style__move_off),
            )
        }
        Box(modifier = Modifier.height(24.dp))
    }
}

/** The name a look goes by on screen. */
@Composable
internal fun charmLabel(charm: DictateMicCharm): String = stringRes(
    when (charm) {
        DictateMicCharm.CLASSIC -> R.string.style__charm_classic
        DictateMicCharm.DAISY -> R.string.style__charm_daisy
        DictateMicCharm.BUTTERFLY -> R.string.style__charm_butterfly
        DictateMicCharm.SUNFLOWER -> R.string.style__charm_sunflower
        DictateMicCharm.BLOSSOM -> R.string.style__charm_blossom
        DictateMicCharm.HEART -> R.string.style__charm_heart
        DictateMicCharm.CLOVER -> R.string.style__charm_clover
        DictateMicCharm.ON_AIR -> R.string.style__charm_on_air
        DictateMicCharm.GRAMOPHONE -> R.string.style__charm_gramophone
        DictateMicCharm.CROONER -> R.string.style__charm_crooner
        DictateMicCharm.MIXTAPE -> R.string.style__charm_mixtape
        DictateMicCharm.MOON -> R.string.style__charm_moon
        DictateMicCharm.PLANET -> R.string.style__charm_planet
        DictateMicCharm.SHOOTING_STAR -> R.string.style__charm_shooting_star
        DictateMicCharm.DISCO_BALL -> R.string.style__charm_disco_ball
        DictateMicCharm.BIG_RED_BUTTON -> R.string.style__charm_big_red_button
        DictateMicCharm.PIXEL_HEART -> R.string.style__charm_pixel_heart
        DictateMicCharm.NEON -> R.string.style__charm_neon
        DictateMicCharm.STUDIO_MIC -> R.string.style__charm_studio_mic
    },
)

@Composable
private fun moveLabel(move: DictateMicMove): String = stringRes(
    when (move) {
        DictateMicMove.BLOOM -> R.string.style__move_bloom
        DictateMicMove.FLUTTER -> R.string.style__move_flutter
        DictateMicMove.TURN -> R.string.style__move_turn
        DictateMicMove.SWAY -> R.string.style__move_sway
        DictateMicMove.GLOW -> R.string.style__move_glow
        DictateMicMove.WOBBLE -> R.string.style__move_wobble
        DictateMicMove.PULSE -> R.string.style__move_pulse
        DictateMicMove.SPIN -> R.string.style__move_spin
        DictateMicMove.TILT -> R.string.style__move_tilt
        DictateMicMove.TWINKLE -> R.string.style__move_twinkle
        DictateMicMove.PRESS -> R.string.style__move_press
        DictateMicMove.BEAT -> R.string.style__move_beat
        DictateMicMove.FLICKER -> R.string.style__move_flicker
    },
)

private class Shelf(val name: Int, val tagline: Int, val tile: Color, val label: Color)

private fun shelfOf(collection: DictateMicCollection): Shelf = when (collection) {
    DictateMicCollection.GARDEN -> Shelf(
        R.string.style__collection_garden, R.string.style__collection_garden_tagline,
        Color(0xFFEBDCFF), Color(0xFF1E1726),
    )
    DictateMicCollection.RETRO -> Shelf(
        R.string.style__collection_retro, R.string.style__collection_retro_tagline,
        Color(0xFFFFF0D6), Color(0xFF1E1726),
    )
    DictateMicCollection.NIGHT_SKY -> Shelf(
        R.string.style__collection_night_sky, R.string.style__collection_night_sky_tagline,
        Color(0xFF1D2246), Color(0xFFEEF0FF),
    )
    DictateMicCollection.ARCADE -> Shelf(
        R.string.style__collection_arcade, R.string.style__collection_arcade_tagline,
        Color(0xFF221A2E), Color(0xFFF3EEFB),
    )
    DictateMicCollection.SIMPLE -> Shelf(
        R.string.style__collection_simple, R.string.style__collection_simple_tagline,
        Color(0xFFF1E8FB), Color(0xFF1E1726),
    )
}

/**
 * A look drawn in Compose, with the same shapes and movement the floating button paints: [MicCharmPainter]
 * draws both. [t] is how far through one loop of the look's movement; 0 is at rest.
 */
@Composable
internal fun MicCharm(
    charm: DictateMicCharm,
    color: Int,
    modifier: Modifier = Modifier,
    signRes: Int? = R.drawable.ic_dictate_overlay_mic,
    t: Float = 0f,
) {
    val context = LocalContext.current
    val sign = remember(signRes) { signRes?.let { ContextCompat.getDrawable(context, it)?.mutate() } }
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            MicCharmPainter.draw(
                canvas.nativeCanvas, charm, color, size.minDimension, sign,
                signIsMic = signRes == R.drawable.ic_dictate_overlay_mic, t = t,
            )
        }
    }
}

/** [MicCharm], looping through its own movement; [slowdown] stretches the loop for a calmer idle. */
@Composable
internal fun MovingMicCharm(
    charm: DictateMicCharm,
    color: Int,
    moving: Boolean,
    modifier: Modifier = Modifier,
    signRes: Int? = R.drawable.ic_dictate_overlay_mic,
    slowdown: Int = 1,
) {
    if (!moving) {
        MicCharm(charm, color, modifier, signRes)
        return
    }
    // A new clock per look, since a running one keeps the timing it started with.
    key(charm) {
        val t by rememberInfiniteTransition(label = "charm").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween((charm.move.periodMs * slowdown).toInt(), easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "t",
        )
        MicCharm(charm, color, modifier, signRes, t)
    }
}

/**
 * The button as it looks while it listens, beside a sample chat. It keeps moving here so the choice can be
 * judged; on screen the button only moves while it records.
 */
@Composable
private fun StylePreview(charm: DictateMicCharm, color: Int, moving: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(188.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Surface(
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.widthIn(max = 220.dp),
                ) {
                    Text(
                        stringRes(R.string.style__preview_message),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
                Text(
                    stringRes(R.string.style__preview_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(end = 90.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    stringRes(R.string.style__preview_field),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd).size(80.dp), contentAlignment = Alignment.Center) {
                Ripple(modifier = Modifier.fillMaxSize())
                MovingMicCharm(
                    charm = charm,
                    color = color,
                    moving = moving,
                    signRes = R.drawable.ic_dictate_overlay_stop,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

/** The recording ripple the floating button spreads behind itself. */
@Composable
private fun Ripple(modifier: Modifier) {
    val ripple by rememberInfiniteTransition(label = "ripple").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple",
    )
    val haloColor = colorResource(R.color.dictate_overlay_recording)
    Canvas(modifier = modifier) {
        drawCircle(
            color = haloColor.copy(alpha = (1f - ripple) * 0.45f),
            radius = size.minDimension / 2f * (0.7f + 0.3f * ripple),
        )
    }
}

/** One collection: its name and mood, and its looks on a shelf that scrolls sideways. */
@Composable
private fun CollectionShelf(
    collection: DictateMicCollection,
    selected: DictateMicCharm,
    colorOf: (DictateMicCharm) -> Int,
    onPick: (DictateMicCharm) -> Unit,
) {
    val shelf = shelfOf(collection)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringRes(shelf.name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringRes(shelf.tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 16.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DictateMicCharm.entries.filter { it.collection == collection }.forEach { look ->
            LookTile(
                charm = look,
                color = colorOf(look),
                shelf = shelf,
                selected = look == selected,
                onClick = { onPick(look) },
            )
        }
    }
}

@Composable
private fun LookTile(charm: DictateMicCharm, color: Int, shelf: Shelf, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .width(92.dp)
            .height(104.dp)
            .clip(shape)
            .background(shelf.tile)
            .border(
                width = 3.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        // Only the chosen look moves, so the shelf stays calm and the choice stands out.
        MovingMicCharm(charm = charm, color = color, moving = selected, modifier = Modifier.size(52.dp))
        Text(
            charmLabel(charm),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = shelf.label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/**
 * The colour swatches. With an [original] look, the first swatch is that look in its own colours, which
 * is also what a transparent pick means; without one (the classic button) there are only the colours.
 */
@Composable
private fun SwatchRow(original: DictateMicCharm?, picked: Int, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (original != null) {
            Swatch(
                name = stringRes(R.string.style__color_original),
                fill = MaterialTheme.colorScheme.surfaceContainerHigh,
                selected = (picked ushr 24) == 0,
                onClick = { onPick(0) },
            ) {
                MicCharm(original, original.defaultColor, Modifier.size(26.dp), signRes = null)
            }
        }
        DictateMicColors.SWATCHES.forEachIndexed { index, swatch ->
            Swatch(
                name = stringRes(SWATCH_NAMES[index]),
                fill = Color(swatch),
                selected = picked == swatch,
                onClick = { onPick(swatch) },
            )
        }
    }
}

private val SWATCH_NAMES = listOf(
    R.string.style__color_cloud,
    R.string.style__color_blossom,
    R.string.style__color_lilac,
    R.string.style__color_butter,
    R.string.style__color_mint,
    R.string.style__color_sky,
)

@Composable
private fun Swatch(
    name: String,
    fill: Color,
    selected: Boolean,
    onClick: () -> Unit,
    inside: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        when {
            inside != null -> inside()
            selected -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color(MicCharmPainter.INK),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

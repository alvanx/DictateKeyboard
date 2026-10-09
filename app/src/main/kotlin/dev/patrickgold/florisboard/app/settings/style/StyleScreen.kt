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

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import dev.patrickgold.florisboard.dictate.DictateMicColors
import dev.patrickgold.florisboard.dictate.DictateMicMotion
import dev.patrickgold.florisboard.dictate.MicCharmPainter
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes

/**
 * The Style tab: what the floating mic button looks like. A live preview up top, then the look, its
 * colour and how it moves while it listens. Everything here is the button's look and nothing else, so the
 * look settings that used to sit in the floating-button screen (design, colour) live here now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleScreen() = FlorisScreen {
    title = stringRes(R.string.nav__style)
    navigationIconVisible = false
    previewFieldVisible = false

    val navController = LocalNavController.current

    content {
        val charm by prefs.dictate.floatingButtonCharm.collectAsState()
        val color by prefs.dictate.floatingButtonColor.collectAsState()
        val motion by prefs.dictate.floatingButtonMotion.collectAsState()
        val buttonOn = rememberMicButtonOn()

        Text(
            stringRes(R.string.style__subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        )

        StylePreview(charm = charm, color = color, motion = motion)

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

        SectionHeading(stringRes(R.string.style__mic_button))
        CHARMS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { option ->
                    CharmTile(
                        charm = option,
                        color = color,
                        selected = option == charm,
                        onClick = { prefs.dictate.floatingButtonCharm.set(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // The classic button still comes in the designs it always had; those are its "shape".
        if (charm == DictateMicCharm.CLASSIC) {
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
        }

        SectionHeading(stringRes(R.string.style__color))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DictateMicColors.SWATCHES.forEachIndexed { index, swatch ->
                Swatch(
                    color = Color(swatch),
                    name = stringRes(SWATCH_NAMES[index]),
                    selected = color.toArgb() == swatch,
                    onClick = { prefs.dictate.floatingButtonColor.set(Color(swatch)) },
                )
            }
        }
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

        // Only the charms take a movement; the classic designs bring their own.
        if (charm != DictateMicCharm.CLASSIC) {
            SectionHeading(stringRes(R.string.style__motion))
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DictateMicMotion.entries.forEach { option ->
                    FilterChip(
                        selected = option == motion,
                        onClick = { prefs.dictate.floatingButtonMotion.set(option) },
                        label = { Text(motionLabel(option), maxLines = 1) },
                    )
                }
            }
        }
        Box(modifier = Modifier.height(24.dp))
    }
}

private val CHARMS = listOf(
    DictateMicCharm.DAISY,
    DictateMicCharm.BUTTERFLY,
    DictateMicCharm.HEART,
    DictateMicCharm.CLOVER,
    DictateMicCharm.MOON,
    DictateMicCharm.CLASSIC,
)

private val SWATCH_NAMES = listOf(
    R.string.style__color_cloud,
    R.string.style__color_blossom,
    R.string.style__color_lilac,
    R.string.style__color_butter,
    R.string.style__color_mint,
    R.string.style__color_sky,
)

/** The name a look goes by on screen. */
@Composable
internal fun charmLabel(charm: DictateMicCharm): String = stringRes(
    when (charm) {
        DictateMicCharm.CLASSIC -> R.string.style__charm_classic
        DictateMicCharm.DAISY -> R.string.style__charm_daisy
        DictateMicCharm.BUTTERFLY -> R.string.style__charm_butterfly
        DictateMicCharm.HEART -> R.string.style__charm_heart
        DictateMicCharm.CLOVER -> R.string.style__charm_clover
        DictateMicCharm.MOON -> R.string.style__charm_moon
    },
)

@Composable
private fun motionLabel(motion: DictateMicMotion): String = stringRes(
    when (motion) {
        DictateMicMotion.BLOOM -> R.string.style__motion_bloom
        DictateMicMotion.FLUTTER -> R.string.style__motion_flutter
        DictateMicMotion.PULSE -> R.string.style__motion_pulse
        DictateMicMotion.STILL -> R.string.style__motion_still
    },
)

/**
 * A look drawn in Compose, with the same shapes the floating button paints: [MicCharmPainter] draws both.
 * [glyphRes] is the sign on it, the mic unless the caller wants another state shown; null for none.
 */
@Composable
internal fun MicCharm(
    charm: DictateMicCharm,
    color: Color,
    modifier: Modifier = Modifier,
    glyphRes: Int? = R.drawable.ic_dictate_overlay_mic,
) {
    val context = LocalContext.current
    val glyph = remember(glyphRes) { glyphRes?.let { ContextCompat.getDrawable(context, it)?.mutate() } }
    val argb = color.toArgb()
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            MicCharmPainter.draw(canvas.nativeCanvas, charm, argb, size.minDimension, glyph)
        }
    }
}

/**
 * The button as it looks while it listens, beside a sample chat. It keeps moving here so the choice can be
 * judged; on screen the button only moves while it records.
 */
@Composable
private fun StylePreview(charm: DictateMicCharm, color: Color, motion: DictateMicMotion) {
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
            // A new transition per look and movement, since a running one keeps the timing it started with.
            key(charm, motion) {
                ListeningCharm(
                    charm = charm,
                    color = color,
                    motion = motion,
                    modifier = Modifier.align(Alignment.BottomEnd).size(76.dp),
                )
            }
        }
    }
}

/** The look moving the way it will while recording, with the ripple behind it. */
@Composable
private fun ListeningCharm(charm: DictateMicCharm, color: Color, motion: DictateMicMotion, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "listening")
    // The classic designs have their own movement on screen; here they just pulse.
    val shownMotion = if (charm == DictateMicCharm.CLASSIC) DictateMicMotion.PULSE else motion
    val period = when (shownMotion) {
        DictateMicMotion.BLOOM -> 1200
        DictateMicMotion.FLUTTER -> 250
        DictateMicMotion.PULSE -> 600
        DictateMicMotion.STILL -> 1000
    }
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (shownMotion == DictateMicMotion.STILL) 0f else 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "move",
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple",
    )
    val haloColor = colorResource(R.color.dictate_overlay_recording)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2f
            drawCircle(
                color = haloColor.copy(alpha = (1f - ripple) * 0.45f),
                radius = maxR * (0.7f + 0.3f * ripple),
            )
        }
        MicCharm(
            charm = charm,
            color = color,
            glyphRes = R.drawable.ic_dictate_overlay_stop,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    val grow = when (shownMotion) {
                        DictateMicMotion.BLOOM -> 0.94f + 0.13f * t
                        DictateMicMotion.PULSE -> 1f + 0.09f * t
                        else -> 1f
                    }
                    val flap = if (shownMotion == DictateMicMotion.FLUTTER) 1f - 0.42f * t else 1f
                    scaleX = grow * flap
                    scaleY = grow
                    rotationZ = if (shownMotion == DictateMicMotion.BLOOM) 14f * t else 0f
                },
        )
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

@Composable
private fun CharmTile(
    charm: DictateMicCharm,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .height(96.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .border(
                width = 2.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        MicCharm(charm = charm, color = color, modifier = Modifier.size(48.dp))
        Text(
            charmLabel(charm),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Swatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color(MicCharmPainter.INK), modifier = Modifier.size(20.dp))
        }
    }
}

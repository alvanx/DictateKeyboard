/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.DictateFloatingButtonDesign

/**
 * The classic floating button at rest, in the [design] picked on the Style tab, so the preview there
 * changes with the choice. Sized like the real button at medium: a 64dp footprint around a 48dp body.
 *
 * The cloud, aurora and lattice designs are the same views the floating button draws with; the flat
 * ones (pill, ring, orb) are simple enough to redraw here, and are kept to the same measurements.
 */
@Composable
internal fun DesignPreview(design: DictateFloatingButtonDesign, color: Int, modifier: Modifier = Modifier) {
    val accent = Color(color)
    // Same rule as the floating button: white glyph unless the colour is very light.
    val glyph = if (accent.copy(alpha = 1f).luminance() > 0.7f) Color.Black else Color.White
    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        // A fresh view per design and colour, since the drawn ones keep their own clocks.
        key(design, color) {
            when (design) {
                // At rest the pill is a plain circle; it only opens into a pill while recording.
                DictateFloatingButtonDesign.PILL -> Disc(accent, glyph, size = 48.dp, glyphSize = 24.dp)
                DictateFloatingButtonDesign.ORB -> Disc(accent, glyph, size = 48.dp, glyphSize = 26.dp)
                DictateFloatingButtonDesign.RING -> {
                    Canvas(modifier = Modifier.size(64.dp)) {
                        drawCircle(accent, radius = 25.dp.toPx(), style = Stroke(width = 3.dp.toPx()))
                    }
                    Disc(accent, glyph, size = 40.dp, glyphSize = 20.dp)
                }
                DictateFloatingButtonDesign.CLOUD -> {
                    AndroidView(
                        factory = { ctx ->
                            AudioReactiveCloudOrbView(ctx).apply { setMode(AudioReactiveCloudOrbView.Mode.IDLE) }
                        },
                        modifier = Modifier.size(64.dp),
                    )
                    Icon(
                        painterResource(R.drawable.ic_dictate_overlay_mic),
                        contentDescription = null,
                        tint = Color(CLOUD_GLYPH_COLOR).copy(alpha = 0.9f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                DictateFloatingButtonDesign.AURORA -> AndroidView(
                    factory = { ctx ->
                        DictateAuroraOrbView(ctx).apply {
                            bodyRadius = 24.dp.value * ctx.resources.displayMetrics.density
                            setMood(DictateAuroraOrbView.Mood.IDLE, color)
                        }
                    },
                    modifier = Modifier.size(64.dp),
                )
                DictateFloatingButtonDesign.LATTICE -> AndroidView(
                    factory = { ctx ->
                        DictateLatticeSphereView(ctx).apply {
                            bodyDiameter = 48.dp.value * ctx.resources.displayMetrics.density
                            setMode(DictateLatticeSphereView.Mode.WEB, color)
                        }
                    },
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

@Composable
private fun Disc(accent: Color, glyph: Color, size: Dp, glyphSize: Dp) {
    Box(
        modifier = Modifier.size(size).background(accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_dictate_overlay_mic),
            contentDescription = null,
            tint = glyph,
            modifier = Modifier.size(glyphSize),
        )
    }
}

/** The cloud's own dark glyph colour, as the floating button paints it. */
private const val CLOUD_GLYPH_COLOR = 0xFF343B8F.toInt()

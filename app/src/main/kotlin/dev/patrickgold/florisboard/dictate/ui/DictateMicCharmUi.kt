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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictateMicCharm
import dev.patrickgold.florisboard.dictate.MicCharmPainter
import dev.patrickgold.jetpref.datastore.model.collectAsState as collectPrefAsState
import kotlin.math.PI
import kotlin.math.cos

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

/** The recording ripple the floating button spreads behind itself. */
@Composable
internal fun MicCharmRipple(modifier: Modifier) {
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

/**
 * The keyboard's own mic, in the look picked on the Style tab, so it matches the floating button: the
 * same drawing, the same colour, the same movement. Only drawn for a look other than the classic one —
 * the classic mic keeps the themed accent circle the keyboard has always had.
 *
 * It behaves as the floating button does: still at rest, since it sits there all day; while it listens it
 * wears the stop sign (tapping it again finishes, there is no separate done button), spreads the
 * recording ripple behind itself and moves its own way, but only with "Move while listening" on. While
 * the dictation is worked on it keeps the stop sign, because a tap then stops that work, and breathes
 * slowly so it still says "busy".
 */
@Composable
internal fun KeyboardMicCharm(charm: DictateMicCharm, modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val charmColor by prefs.dictate.floatingButtonCharmColor.collectPrefAsState()
    val animated by prefs.dictate.floatingButtonCharmAnimated.collectPrefAsState()
    val state by DictateController.state.collectAsState()
    val recording = state as? DictateController.UiState.Recording
    val listening = recording != null && !recording.paused
    val busy = state is DictateController.UiState.Transcribing || state is DictateController.UiState.Rewording
    val signRes = if (recording != null || busy) R.drawable.ic_dictate_overlay_stop else R.drawable.ic_dictate_overlay_mic

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (listening) MicCharmRipple(Modifier.fillMaxSize())
        val breathe = if (busy) {
            val t by rememberInfiniteTransition(label = "charmBusy").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
                label = "t",
            )
            1f + 0.05f * ((1f - cos(2f * PI.toFloat() * t)) / 2f)
        } else {
            1f
        }
        MovingMicCharm(
            charm = charm,
            color = charm.resolveColor(charmColor.toArgb()),
            moving = listening && animated,
            signRes = signRes,
            // A little inside the key, so a look that blooms or sways is never cut off by the bar.
            modifier = Modifier
                .fillMaxSize(CHARM_FILL)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                },
        )
    }
}

/** How much of the mic key the look takes: about what the floating button gives it inside its window. */
private const val CHARM_FILL = 0.86f

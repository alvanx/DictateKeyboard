/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.cos

/** The shelves the Style tab groups the looks on. */
enum class DictateMicCollection {
    GARDEN,
    RETRO,
    NIGHT_SKY,
    ARCADE,
    SIMPLE,
}

/**
 * How a look moves while it listens. Every look has exactly one, its own: a daisy blooms, a mixtape spins
 * its reels, a neon sign flickers. A generic choice of movements read well on a flower and badly on a
 * cassette, so the only choice left to the user is whether it moves at all.
 */
enum class DictateMicMove(val periodMs: Long) {
    BLOOM(2400),
    FLUTTER(550),
    TURN(4000),
    SWAY(2600),
    GLOW(1400),
    WOBBLE(1800),
    PULSE(1300),
    SPIN(1800),
    TILT(3000),
    TWINKLE(1400),
    PRESS(1400),
    BEAT(1200),
    FLICKER(2200),
}

/**
 * What the floating mic button looks like, picked on the Style tab.
 *
 * [CLASSIC] keeps the plain round button in whichever design the user chose (pill, ring, orb…); every
 * other value replaces it with a drawn look. A look that is one colour at heart is [tintable] and can be
 * recoloured, starting from its [defaultColor]; the rest (a sunflower, an "On Air" sign) keep their own.
 */
enum class DictateMicCharm(
    val collection: DictateMicCollection,
    val move: DictateMicMove,
    val tintable: Boolean,
    val defaultColor: Int,
) {
    DAISY(DictateMicCollection.GARDEN, DictateMicMove.BLOOM, true, 0xFFFFFFFF.toInt()),
    BUTTERFLY(DictateMicCollection.GARDEN, DictateMicMove.FLUTTER, true, 0xFFC3A8FF.toInt()),
    SUNFLOWER(DictateMicCollection.GARDEN, DictateMicMove.TURN, false, 0xFFFFC233.toInt()),
    BLOSSOM(DictateMicCollection.GARDEN, DictateMicMove.SWAY, true, 0xFFFFC4D6.toInt()),
    HEART(DictateMicCollection.GARDEN, DictateMicMove.BEAT, true, 0xFFFF9EBB.toInt()),
    CLOVER(DictateMicCollection.GARDEN, DictateMicMove.SWAY, true, 0xFF8FE0C6.toInt()),
    ON_AIR(DictateMicCollection.RETRO, DictateMicMove.GLOW, false, 0xFFFF3B30.toInt()),
    GRAMOPHONE(DictateMicCollection.RETRO, DictateMicMove.WOBBLE, false, 0xFFE8B04A.toInt()),
    CROONER(DictateMicCollection.RETRO, DictateMicMove.PULSE, false, 0xFFE3E6EC.toInt()),
    MIXTAPE(DictateMicCollection.RETRO, DictateMicMove.SPIN, true, 0xFFFF9EBB.toInt()),
    MOON(DictateMicCollection.NIGHT_SKY, DictateMicMove.GLOW, true, 0xFFFFE39A.toInt()),
    PLANET(DictateMicCollection.NIGHT_SKY, DictateMicMove.TILT, true, 0xFFC3A8FF.toInt()),
    SHOOTING_STAR(DictateMicCollection.NIGHT_SKY, DictateMicMove.TWINKLE, false, 0xFFFFD45C.toInt()),
    DISCO_BALL(DictateMicCollection.NIGHT_SKY, DictateMicMove.SWAY, false, 0xFFCFD6E4.toInt()),
    BIG_RED_BUTTON(DictateMicCollection.ARCADE, DictateMicMove.PRESS, false, 0xFFFF3B5C.toInt()),
    PIXEL_HEART(DictateMicCollection.ARCADE, DictateMicMove.BEAT, true, 0xFFFF3B5C.toInt()),
    NEON(DictateMicCollection.ARCADE, DictateMicMove.FLICKER, true, 0xFFFF6FD8.toInt()),
    STUDIO_MIC(DictateMicCollection.ARCADE, DictateMicMove.PULSE, false, 0xFFD7D2E0.toInt()),
    CLASSIC(DictateMicCollection.SIMPLE, DictateMicMove.PULSE, true, 0xFF30B7E6.toInt());

    /** The colour to paint with: the user's pick when the look takes one, otherwise its own. */
    fun resolveColor(picked: Int): Int =
        if (!tintable || (picked ushr 24) == 0) defaultColor else picked or 0xFF000000.toInt()
}

/** The colours offered for a tintable look, light enough that the dark mic reads on all of them. */
object DictateMicColors {
    val SWATCHES: List<Int> = listOf(
        0xFFFFFFFF.toInt(), // cloud
        0xFFFF9EBB.toInt(), // blossom
        0xFFC3A8FF.toInt(), // lilac
        0xFFFFD45C.toInt(), // butter
        0xFF8FE0C6.toInt(), // mint
        0xFF9CCBFF.toInt(), // sky
    )
}

/**
 * Draws a [DictateMicCharm] onto a plain Android canvas, so the floating button (a View in another app's
 * window) and the Style tab (Compose) paint the exact same shapes and the same movement from one place.
 *
 * Every look is laid out on a 100 × 100 grid and scaled to the size asked for. Movement is a function of
 * [draw]'s `t`, the position in one loop of the look's [DictateMicMove] from 0 to 1, so whoever runs the
 * clock only has to count; nothing here keeps time.
 */
object MicCharmPainter {
    /** The dark line and glyph colour the looks are drawn with. */
    const val INK: Int = 0xFF2B2340.toInt()

    // Stand-ins for the look's colour, resolved when painting: the colour itself, and a lighter and a
    // darker shade of it for the parts that go with it. Fully transparent, so no real colour collides.
    private const val TINT = 0x00000001
    private const val TINT_LIGHT = 0x00000002
    private const val TINT_DARK = 0x00000003

    private const val GOLD = 0xFFFFC233.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** How a part of a look moves on its own, inside the look's overall movement. */
    private enum class Part { BODY, TWINKLE, REEL, PRESS, UNDER_SIGN }

    private class Shape(
        val path: Path,
        val fill: Int?,
        val stroke: Int?,
        val width: Float,
        val part: Part,
        val alpha: Float,
        val pivotX: Float,
        val pivotY: Float,
    ) {
        val bounds = RectF().also { path.computeBounds(it, true) }
    }

    /**
     * One look: its shapes, back to front; where the mic sits on it ([signBox], null when the look is
     * already obviously a microphone or has no room for one, and then a state is shown as a badge);
     * whether that sign is white; the colour it glows in, if it glows; and the point it sways around.
     */
    private class Art(
        val shapes: List<Shape>,
        val signBox: RectF?,
        val whiteSign: Boolean?,
        val glow: Int?,
        val swayPivotX: Float = 50f,
        val swayPivotY: Float = 50f,
        val label: String? = null,
    )

    private class ArtBuilder {
        val shapes = mutableListOf<Shape>()

        fun add(
            path: Path,
            fill: Int? = null,
            stroke: Int? = null,
            width: Float = 0f,
            part: Part = Part.BODY,
            alpha: Float = 1f,
            pivotX: Float = 50f,
            pivotY: Float = 50f,
        ) {
            shapes += Shape(path, fill, stroke, width, part, alpha, pivotX, pivotY)
        }
    }

    private fun art(
        signBox: RectF?,
        whiteSign: Boolean? = null,
        glow: Int? = null,
        swayPivotX: Float = 50f,
        swayPivotY: Float = 50f,
        label: String? = null,
        build: ArtBuilder.() -> Unit,
    ): Art = Art(ArtBuilder().apply(build).shapes, signBox, whiteSign, glow, swayPivotX, swayPivotY, label)

    private fun svg(d: String): Path = PathParser.createPathFromPathData(d)

    private fun circle(cx: Float, cy: Float, r: Float): Path =
        Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }

    private fun oval(cx: Float, cy: Float, rx: Float, ry: Float, rotate: Float = 0f, px: Float = 50f, py: Float = 50f) =
        Path().apply {
            addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW)
            if (rotate != 0f) transform(Matrix().apply { setRotate(rotate, px, py) })
        }

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f): Path =
        Path().apply { addRoundRect(x, y, x + w, y + h, r, r, Path.Direction.CW) }

    private fun c(argb: Long): Int = argb.toInt()

    private val arts: Map<DictateMicCharm, Art> by lazy {
        mapOf(
            DictateMicCharm.DAISY to art(RectF(36f, 36f, 64f, 64f), whiteSign = false) {
                for (i in 0 until 12) add(oval(50f, 21f, 9f, 19f, i * 30f), TINT, c(0xFFD9C2F0), 1.6f)
                add(circle(50f, 50f, 16f), GOLD)
            },
            DictateMicCharm.BUTTERFLY to art(RectF(39f, 43f, 61f, 65f), whiteSign = true) {
                listOf(
                    "M48 46C40 26 22 16 12 22S10 46 22 52c8 4 18 2 26-6z",
                    "M52 46c8-20 26-30 36-24s2 24-10 30c-8 4-18 2-26-6z",
                    "M47 52c-10 2-22 8-22 20 0 8 8 12 14 8 5-3 8-14 8-28z",
                    "M53 52c10 2 22 8 22 20 0 8-8 12-14 8-5-3-8-14-8-28z",
                ).forEach { add(svg(it), TINT, INK, 2.2f) }
                add(circle(27f, 33f, 4f), WHITE, INK, 1.6f)
                add(circle(73f, 33f, 4f), WHITE, INK, 1.6f)
                add(svg("M48 35c-3-8-8-12-12-13M52 35c3-8 8-12 12-13"), stroke = INK, width = 2.2f)
                add(rect(46.5f, 34f, 7f, 40f, 3.5f), INK)
                // The body is too thin to hold a sign, so the mic sits on a dark disc across it.
                add(circle(50f, 54f, 13f), INK)
            },
            DictateMicCharm.SUNFLOWER to art(RectF(38f, 38f, 62f, 62f), whiteSign = true) {
                for (i in 0 until 16) add(oval(50f, 19f, 7f, 17f, i * 22.5f), GOLD, c(0xFFE59A00), 1f)
                add(circle(50f, 50f, 17f), c(0xFF7A4A24))
                for ((x, y) in listOf(44f to 45f, 56f to 45f, 50f to 51f, 43f to 56f, 57f to 56f)) {
                    add(circle(x, y, 2.2f), c(0xFFB07A45), part = Part.UNDER_SIGN)
                }
            },
            DictateMicCharm.BLOSSOM to art(null, swayPivotY = 90f) {
                for (i in 0 until 5) add(oval(50f, 28f, 15f, 22f, i * 72f), TINT, c(0xFFFF8FB1), 2f)
                add(circle(50f, 50f, 9f), c(0xFFFF6F91))
                add(svg("M50 50V37M50 50l12-6M50 50l-12-6"), stroke = c(0xFFC2185B), width = 2f)
            },
            DictateMicCharm.HEART to art(RectF(34f, 32f, 66f, 64f)) {
                val heart = svg("M50 88C18 66 6 46 14 30c7-14 26-16 36-2 10-14 29-12 36 2 8 16-4 36-36 58z")
                add(heart, TINT, INK, 2.2f)
            },
            DictateMicCharm.CLOVER to art(RectF(39f, 39f, 61f, 61f), whiteSign = true, swayPivotY = 90f) {
                for ((x, y) in listOf(34f to 34f, 66f to 34f, 34f to 66f, 66f to 66f)) {
                    add(circle(x, y, 18f), TINT, INK, 2.2f)
                }
                add(circle(50f, 50f, 14f), INK)
            },
            DictateMicCharm.ON_AIR to art(null, glow = c(0xFFFF3B30), label = "ON AIR") {
                add(rect(4f, 27f, 92f, 46f, 12f), c(0xFF3A0D0D))
                add(rect(10f, 33f, 80f, 34f, 8f), c(0xFFFF3B30))
            },
            DictateMicCharm.GRAMOPHONE to art(null) {
                add(rect(8f, 38f, 24f, 24f, 4f), c(0xFF7A4A24), c(0xFF3E2410), 2.5f)
                add(svg("M30 46 60 40Q74 30 86 14V86Q74 70 60 60L30 54Z"), c(0xFFE8B04A), c(0xFF8A5A14), 2.5f)
                add(oval(86f, 50f, 7f, 36f), c(0xFFFFD88A), c(0xFF8A5A14), 2.5f)
            },
            DictateMicCharm.CROONER to art(null) {
                add(rect(30f, 8f, 40f, 58f, 20f), c(0xFFE3E6EC), INK, 2.5f)
                add(svg("M36 22h28M34 30h32M34 38h32M34 46h32M36 54h28"), stroke = c(0xFF8A90A0), width = 2f)
                add(rect(46f, 66f, 8f, 16f), INK)
                add(oval(50f, 86f, 22f, 6f), INK)
            },
            DictateMicCharm.MIXTAPE to art(null) {
                add(rect(8f, 22f, 84f, 56f, 8f), TINT, INK, 2.5f)
                add(rect(16f, 28f, 68f, 14f, 3f), WHITE)
                add(svg("M26 78l6-12h36l6 12"), TINT_LIGHT, INK, 2.5f)
                for (x in listOf(36f, 64f)) {
                    add(circle(x, 54f, 8f), INK, part = Part.REEL, pivotX = x, pivotY = 54f)
                    add(svg("M${x} 48v12M${x - 6} 54h12"), stroke = WHITE, width = 2f, part = Part.REEL, pivotX = x, pivotY = 54f)
                }
            },
            DictateMicCharm.MOON to art(RectF(26f, 36f, 58f, 68f), glow = c(0xFFFFE39A)) {
                add(svg("M60 10a40 40 0 1 0 30 58A33 33 0 0 1 60 10z"), TINT, INK, 2.2f)
                add(svg("M78 16l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"), GOLD, part = Part.TWINKLE)
                add(svg("M88 40l1.4 4 4 1.4-4 1.4-1.4 4-1.4-4-4-1.4 4-1.4z"), c(0xFFC3A8FF), part = Part.TWINKLE)
            },
            DictateMicCharm.PLANET to art(RectF(40f, 38f, 60f, 58f)) {
                add(circle(50f, 50f, 24f), TINT)
                add(svg("M29 44c12-4 30-3 43 3M28 55c14 2 28 2 44-2"), stroke = TINT_DARK, width = 3.5f)
                add(oval(50f, 52f, 44f, 11f, -18f, 50f, 52f), stroke = c(0xFFFFD45C), width = 5f)
            },
            DictateMicCharm.SHOOTING_STAR to art(null) {
                add(svg("M10 82 50 48"), stroke = c(0xFFFF9EBB), width = 6f)
                add(svg("M22 92 56 60"), stroke = c(0xFFC3A8FF), width = 4f)
                add(svg("M6 66 42 42"), stroke = c(0xFF8FE0C6), width = 3f)
                add(
                    svg("M66 12L71.6 26.3 86.9 27.2 75 36.9 78.9 51.8 66 43.5 53.1 51.8 57 36.9 45.1 27.2 60.4 26.3Z"),
                    c(0xFFFFD45C), c(0xFFE5A800), 2f, part = Part.TWINKLE,
                )
            },
            DictateMicCharm.DISCO_BALL to art(null, swayPivotY = 4f) {
                add(svg("M50 4v16"), stroke = c(0xFF9AA3C7), width = 2f)
                add(circle(50f, 54f, 32f), c(0xFFCFD6E4), c(0xFF8A90A0), 2f)
                add(svg("M21 42h58M18 54h64M21 66h58"), stroke = c(0xFF8A90A0), width = 1.4f)
                add(oval(50f, 54f, 12f, 32f), stroke = c(0xFF8A90A0), width = 1.4f)
                add(oval(50f, 54f, 24f, 32f), stroke = c(0xFF8A90A0), width = 1.4f)
                add(rect(33f, 33f, 8f, 8f), WHITE)
                add(rect(58f, 57f, 8f, 8f), WHITE, alpha = 0.7f)
                add(svg("M86 22l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"), c(0xFFFFD45C), part = Part.TWINKLE)
                add(svg("M14 80l1.6 4.4 4.4 1.6-4.4 1.6-1.6 4.4-1.6-4.4-4.4-1.6 4.4-1.6z"), c(0xFFFF9EBB), part = Part.TWINKLE)
            },
            DictateMicCharm.BIG_RED_BUTTON to art(null) {
                add(oval(50f, 76f, 40f, 13f), c(0xFF0E0A14))
                add(oval(50f, 70f, 40f, 13f), c(0xFF463A5C))
                add(svg("M20 64A30 26 0 0 1 80 64V68A30 10 0 0 1 20 68Z"), c(0xFFFF3B5C), part = Part.PRESS)
                add(oval(40f, 50f, 8f, 4f), WHITE, alpha = 0.6f, part = Part.PRESS)
            },
            DictateMicCharm.PIXEL_HEART to art(RectF(36f, 28f, 64f, 56f)) {
                listOf(
                    floatArrayOf(15f, 12f, 20f), floatArrayOf(65f, 12f, 20f),
                    floatArrayOf(5f, 22f, 40f), floatArrayOf(55f, 22f, 40f),
                    floatArrayOf(5f, 32f, 90f), floatArrayOf(5f, 42f, 90f),
                    floatArrayOf(15f, 52f, 70f), floatArrayOf(25f, 62f, 50f),
                    floatArrayOf(35f, 72f, 30f), floatArrayOf(45f, 82f, 10f),
                ).forEach { (x, y, w) -> add(rect(x, y, w, 10f), TINT) }
                add(rect(15f, 22f, 10f, 10f), WHITE)
            },
            DictateMicCharm.NEON to art(null, glow = TINT) {
                // A dark backing, or the tubes would vanish over a white page.
                add(circle(50f, 50f, 42f), c(0xFF221A2E))
                add(circle(50f, 50f, 36f), stroke = TINT, width = 6f)
                add(rect(43f, 28f, 14f, 26f, 7f), stroke = c(0xFF7FE7FF), width = 4f)
                add(svg("M35 48a15 15 0 0 0 30 0M50 63v8"), stroke = c(0xFF7FE7FF), width = 4f)
            },
            DictateMicCharm.STUDIO_MIC to art(null) {
                add(rect(36f, 8f, 28f, 52f, 14f), c(0xFFD7D2E0), INK, 1.5f)
                add(svg("M40 20h20M40 28h20M40 36h20M40 44h20"), stroke = c(0xFF8A7F96), width = 2f)
                add(svg("M28 34v10a22 22 0 0 0 44 0V34"), stroke = c(0xFF9A90AD), width = 4f)
                add(rect(47f, 66f, 6f, 16f), c(0xFF9A90AD))
                add(rect(32f, 82f, 36f, 6f, 3f), c(0xFF9A90AD))
            },
            DictateMicCharm.CLASSIC to art(RectF(24f, 24f, 76f, 76f)) {
                add(circle(50f, 50f, 40f), TINT, INK, 2.2f)
            },
        )
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c(0xFFFFF6E8)
        textAlign = Paint.Align.CENTER
        textSize = 17f
        letterSpacing = 0.09f
        typeface = Typeface.DEFAULT_BOLD
    }

    /** Whether the look carries the mic on it; when not, a state other than idle is shown as a badge. */
    fun hasSign(charm: DictateMicCharm): Boolean = arts.getValue(charm).signBox != null

    /**
     * Paints [charm] into a square of [size] px at the canvas origin.
     *
     * @param color the look's colour (see [DictateMicCharm.resolveColor]); ignored by looks that keep theirs.
     * @param sign the mic, or the stop / error / check sign the button shows in other states.
     * @param signIsMic whether [sign] is the plain mic: a look without room for a sign skips that one,
     *   since it is a microphone button anyway, but wears every other sign as a badge.
     * @param t how far through one loop of the look's movement, 0 to 1. 0 is the look at rest.
     */
    fun draw(
        canvas: Canvas,
        charm: DictateMicCharm,
        color: Int,
        size: Float,
        sign: Drawable?,
        signIsMic: Boolean = true,
        t: Float = 0f,
    ) {
        val art = arts.getValue(charm)
        val tint = color or 0xFF000000.toInt()
        val move = charm.move
        // 0 → 1 → 0 over the loop, smooth at both ends: most movements are a swing out and back.
        val wave = ((1 - cos(2 * PI * t)) / 2).toFloat()

        canvas.save()
        canvas.scale(size / 100f, size / 100f)

        var alpha = 1f
        var glow = 0f
        when (move) {
            DictateMicMove.BLOOM -> {
                canvas.rotate(14f * wave, 50f, 50f)
                canvas.scale(0.94f + 0.13f * wave, 0.94f + 0.13f * wave, 50f, 50f)
            }
            DictateMicMove.FLUTTER -> canvas.scale(1f - 0.42f * wave, 1f, 50f, 50f)
            DictateMicMove.TURN -> canvas.rotate(360f * t, 50f, 50f)
            DictateMicMove.SWAY -> canvas.rotate(-8f + 16f * wave, art.swayPivotX, art.swayPivotY)
            DictateMicMove.GLOW -> glow = wave
            DictateMicMove.WOBBLE -> {
                canvas.rotate(-5f + 10f * wave, 50f, 50f)
                canvas.scale(1f + 0.05f * wave, 1f + 0.05f * wave, 50f, 50f)
            }
            DictateMicMove.PULSE -> canvas.scale(1f + 0.09f * wave, 1f + 0.09f * wave, 50f, 50f)
            DictateMicMove.TILT -> canvas.rotate(-10f + 20f * wave, 50f, 50f)
            DictateMicMove.BEAT -> {
                val s = beat(t)
                canvas.scale(s, s, 50f, 50f)
            }
            DictateMicMove.FLICKER -> {
                // Steady, one stutter, then a brighter swell, like a tube warming.
                alpha = if (t in 0.18f..0.22f) 0.55f else 1f
                glow = if (t in 0.18f..0.22f) 0f else 0.5f + 0.5f * wave
            }
            DictateMicMove.SPIN, DictateMicMove.TWINKLE, DictateMicMove.PRESS -> Unit // parts move instead
        }
        // A glowing look still glows a little at rest, so it reads as lit.
        val glowColor = art.glow?.let { resolve(it, tint) }
        if (glowColor != null) {
            val strength = 0.25f + 0.6f * glow
            glowPaint.shader = RadialGradient(
                50f, 50f, 50f,
                intArrayOf(ColorUtils.setAlphaComponent(glowColor, (strength * 200).toInt()), glowColor and 0x00FFFFFF),
                floatArrayOf(0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(50f, 50f, 50f, glowPaint)
        }

        val signInBox = sign != null && art.signBox != null
        for (shape in art.shapes) {
            if (shape.part == Part.UNDER_SIGN && signInBox) continue
            canvas.save()
            var partAlpha = 1f
            when (shape.part) {
                Part.REEL -> if (move == DictateMicMove.SPIN) canvas.rotate(360f * t, shape.pivotX, shape.pivotY)
                Part.TWINKLE -> {
                    // A twinkle runs on the look's own clock, whatever its main movement.
                    val tw = if (move == DictateMicMove.TWINKLE) wave else ((1 - cos(4 * PI * t)) / 2).toFloat()
                    val s = 1f - 0.2f * tw
                    canvas.scale(s, s, shape.bounds.centerX(), shape.bounds.centerY())
                    partAlpha = 1f - 0.55f * tw
                }
                Part.PRESS -> if (move == DictateMicMove.PRESS) canvas.translate(0f, press(t))
                Part.BODY, Part.UNDER_SIGN -> Unit
            }
            val a = alpha * partAlpha * shape.alpha
            shape.fill?.let { canvas.drawPath(shape.path, paint(fill, resolve(it, tint), a)) }
            shape.stroke?.let {
                stroke.strokeWidth = shape.width
                canvas.drawPath(shape.path, paint(stroke, resolve(it, tint), a))
            }
            canvas.restore()
        }
        art.label?.let {
            labelPaint.alpha = (alpha * 255).toInt()
            canvas.drawText(it, 50f, 56f, labelPaint)
        }

        if (signInBox) {
            val box = art.signBox!!
            val white = art.whiteSign ?: (ColorUtils.calculateLuminance(tint) <= 0.4)
            drawSign(canvas, sign!!, box, if (white) WHITE else INK)
        }
        canvas.restore()

        // A look without a place for the sign wears it as a steady badge in the corner, out of the movement.
        if (sign != null && art.signBox == null && !signIsMic) {
            canvas.save()
            canvas.scale(size / 100f, size / 100f)
            canvas.drawCircle(80f, 80f, 17f, paint(fill, INK, 1f))
            stroke.strokeWidth = 2.5f
            canvas.drawCircle(80f, 80f, 17f, paint(stroke, WHITE, 1f))
            drawSign(canvas, sign, RectF(69f, 69f, 91f, 91f), WHITE)
            canvas.restore()
        }
    }

    /** Draws [sign] into [box], given on the 100 × 100 grid the canvas is scaled to. */
    private fun drawSign(canvas: Canvas, sign: Drawable, box: RectF, color: Int) {
        sign.setBounds(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt())
        sign.setTint(color)
        sign.draw(canvas)
    }

    /** The double thump of a heartbeat, then a rest. */
    private fun beat(t: Float): Float = when {
        t < 0.1f -> 1f + 1.4f * t
        t < 0.2f -> 1.14f - 1.2f * (t - 0.1f)
        t < 0.3f -> 1.02f + 1.2f * (t - 0.2f)
        t < 0.4f -> 1.14f - 1.4f * (t - 0.3f)
        else -> 1f
    }

    /** Down and back up once a loop, like a thumb on an arcade button. */
    private fun press(t: Float): Float = when {
        t < 0.55f -> 0f
        t < 0.65f -> 60f * (t - 0.55f)
        t < 0.8f -> 6f - 40f * (t - 0.65f)
        else -> 0f
    }

    private fun resolve(color: Int, tint: Int): Int = when (color) {
        TINT -> tint
        TINT_LIGHT -> ColorUtils.blendARGB(tint, WHITE, 0.45f)
        TINT_DARK -> ColorUtils.blendARGB(tint, Color.BLACK, 0.2f)
        else -> color
    }

    private fun paint(paint: Paint, color: Int, alpha: Float): Paint = paint.apply {
        this.color = color
        this.alpha = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
    }
}

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
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser

/**
 * What the floating mic button looks like, picked on the Style tab.
 *
 * [CLASSIC] keeps the plain round button in whichever design the floating-button settings chose (pill,
 * ring, orb…); every other value replaces it with a drawn charm that the mic sits on.
 */
enum class DictateMicCharm {
    CLASSIC,
    DAISY,
    BUTTERFLY,
    HEART,
    CLOVER,
    MOON;
}

/** How a charm moves while it listens. Only charms move; the classic designs keep their own animation. */
enum class DictateMicMotion {
    BLOOM,
    FLUTTER,
    PULSE,
    STILL;
}

/** The charm colours offered on the Style tab, light enough that the dark mic reads on all of them. */
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
 * window) and the Style tab (Compose) paint the exact same shapes from one set of numbers.
 *
 * Every shape is laid out on a 100 × 100 grid and scaled to the size asked for.
 */
object MicCharmPainter {
    /** The dark line and glyph colour the charms are drawn with. */
    const val INK: Int = 0xFF2B2340.toInt()
    private const val GOLD: Int = 0xFFFFC233.toInt()
    private const val DAISY_EDGE: Int = 0xFFD9C2F0.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    private val wings: List<Path> by lazy {
        listOf(
            "M48 46C40 26 22 16 12 22S10 46 22 52c8 4 18 2 26-6z",
            "M52 46c8-20 26-30 36-24s2 24-10 30c-8 4-18 2-26-6z",
            "M47 52c-10 2-22 8-22 20 0 8 8 12 14 8 5-3 8-14 8-28z",
            "M53 52c10 2 22 8 22 20 0 8-8 12-14 8-5-3-8-14-8-28z",
        ).map { PathParser.createPathFromPathData(it) }
    }
    private val antennae: Path by lazy {
        PathParser.createPathFromPathData("M48 35c-3-8-8-12-12-13M52 35c3-8 8-12 12-13")
    }
    private val heart: Path by lazy {
        PathParser.createPathFromPathData("M50 88C18 66 6 46 14 30c7-14 26-16 36-2 10-14 29-12 36 2 8 16-4 36-36 58z")
    }
    private val moon: Path by lazy {
        PathParser.createPathFromPathData("M60 10a40 40 0 1 0 30 58A33 33 0 0 1 60 10z")
    }
    private val sparkle: Path by lazy {
        PathParser.createPathFromPathData("M76 18l2 5 5 2-5 2-2 5-2-5-5-2 5-2z")
    }

    /**
     * Paints [charm] in [color] into a square of [size] px at the canvas origin, with [glyph] (the mic, or
     * the stop / error / check sign the button shows in other states) where the charm keeps it.
     */
    fun draw(canvas: Canvas, charm: DictateMicCharm, color: Int, size: Float, glyph: Drawable?) {
        val petal = color or 0xFF000000.toInt()
        canvas.save()
        canvas.scale(size / 100f, size / 100f)
        when (charm) {
            DictateMicCharm.DAISY -> {
                for (i in 0 until 12) {
                    canvas.save()
                    canvas.rotate(i * 30f, 50f, 50f)
                    oval.set(41f, 2f, 59f, 40f)
                    canvas.drawOval(oval, fill(petal))
                    canvas.drawOval(oval, stroke(DAISY_EDGE, 1.6f))
                    canvas.restore()
                }
                canvas.drawCircle(50f, 50f, 16f, fill(GOLD))
            }
            DictateMicCharm.BUTTERFLY -> {
                for (wing in wings) {
                    canvas.drawPath(wing, fill(petal))
                    canvas.drawPath(wing, stroke(INK, 2.2f))
                }
                canvas.drawCircle(27f, 33f, 4f, fill(Color.WHITE))
                canvas.drawCircle(73f, 33f, 4f, fill(Color.WHITE))
                canvas.drawPath(antennae, stroke(INK, 2.2f))
                oval.set(46.5f, 34f, 53.5f, 74f)
                canvas.drawRoundRect(oval, 3.5f, 3.5f, fill(INK))
                // The body is too thin to hold a sign, so the mic sits on a dark disc across it.
                canvas.drawCircle(50f, 54f, 13f, fill(INK))
            }
            DictateMicCharm.HEART -> {
                canvas.drawPath(heart, fill(petal))
                canvas.drawPath(heart, stroke(INK, 2.2f))
            }
            DictateMicCharm.CLOVER -> {
                for ((x, y) in listOf(34f to 34f, 66f to 34f, 34f to 66f, 66f to 66f)) {
                    canvas.drawCircle(x, y, 18f, fill(petal))
                    canvas.drawCircle(x, y, 18f, stroke(INK, 2.2f))
                }
                canvas.drawCircle(50f, 50f, 14f, fill(INK))
            }
            DictateMicCharm.MOON -> {
                canvas.drawPath(moon, fill(petal))
                canvas.drawPath(moon, stroke(INK, 2.2f))
                canvas.drawPath(sparkle, fill(GOLD))
            }
            DictateMicCharm.CLASSIC -> {
                canvas.drawCircle(50f, 50f, 40f, fill(petal))
                canvas.drawCircle(50f, 50f, 40f, stroke(INK, 2.2f))
            }
        }
        canvas.restore()

        if (glyph != null) {
            val box = glyphBox(charm)
            val unit = size / 100f
            glyph.setBounds(
                ((box.centerX() - box.width() / 2f) * unit).toInt(),
                ((box.centerY() - box.height() / 2f) * unit).toInt(),
                ((box.centerX() + box.width() / 2f) * unit).toInt(),
                ((box.centerY() + box.height() / 2f) * unit).toInt(),
            )
            glyph.setTint(glyphColor(charm, petal))
            glyph.draw(canvas)
        }
    }

    /** Where the sign goes, on the 100 × 100 grid. */
    private fun glyphBox(charm: DictateMicCharm): RectF = when (charm) {
        DictateMicCharm.DAISY -> RectF(36f, 36f, 64f, 64f)
        DictateMicCharm.BUTTERFLY -> RectF(39f, 43f, 61f, 65f)
        DictateMicCharm.HEART -> RectF(34f, 32f, 66f, 64f)
        DictateMicCharm.CLOVER -> RectF(39f, 39f, 61f, 61f)
        DictateMicCharm.MOON -> RectF(26f, 36f, 58f, 68f)
        DictateMicCharm.CLASSIC -> RectF(24f, 24f, 76f, 76f)
    }

    /**
     * The sign is dark on the charm, or white where it sits on a dark disc. A charm in a dark custom colour
     * (carried over from the old colour setting) gets a white sign too, so it never disappears.
     */
    private fun glyphColor(charm: DictateMicCharm, petal: Int): Int = when (charm) {
        DictateMicCharm.BUTTERFLY, DictateMicCharm.CLOVER -> Color.WHITE
        DictateMicCharm.DAISY -> INK
        else -> if (ColorUtils.calculateLuminance(petal) > 0.4) INK else Color.WHITE
    }

    private fun fill(color: Int): Paint = fill.apply { this.color = color }

    private fun stroke(color: Int, width: Float): Paint = stroke.apply {
        this.color = color
        strokeWidth = width
    }
}

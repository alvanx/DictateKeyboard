/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.core

import dev.patrickgold.florisboard.lib.FlorisLocale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which keyboard a system language gets, against the presets the app actually ships.
 *
 * An install without subtypes is seeded from the system language list, and until then a German phone
 * typed on English QWERTY. Getting a match wrong here is the same complaint with a different layout, so
 * the tests read the real localization extension rather than a hand-made list that could drift from it.
 */
class SubtypePresetMatcherTest {

    /** A preset reduced to what the matching looks at, plus its layout to tell duplicates apart. */
    private data class Preset(val tag: String, val locale: FlorisLocale, val layout: String)

    private val assetsDir: File by lazy {
        // Run from the repo root or from app/, depending on how Gradle was invoked.
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val suffix = "src/main/assets/ime"
        while (dir != null) {
            File(dir, "app/$suffix").takeIf { it.isDirectory }?.let { return@lazy it }
            File(dir, suffix).takeIf { it.isDirectory }?.let { return@lazy it }
            dir = dir.parentFile
        }
        error("could not locate app/src/main/assets/ime")
    }

    /** The shipped presets in file order, which is the order the seeding sees them in. */
    private val presets: List<Preset> by lazy {
        val ext = Json.parseToJsonElement(
            File(assetsDir, "keyboard/org.florisboard.localization/extension.json").readText(),
        ).jsonObject
        ext["subtypePresets"]!!.jsonArray.map { it.jsonObject }.map {
            val tag = it["languageTag"]!!.jsonPrimitive.content
            val layout = it["preferred"]!!.jsonObject["characters"]!!.jsonPrimitive.content.substringAfter(':')
            Preset(tag, FlorisLocale.fromTag(tag), layout)
        }
    }

    private fun best(tag: String): Preset? =
        SubtypePresetMatcher.bestMatch(FlorisLocale.fromTag(tag), presets) { it.locale }

    private fun seed(vararg tags: String): List<String> =
        SubtypePresetMatcher.seedFor(tags.map { FlorisLocale.fromTag(it) }, presets) { it.locale }.map { it.tag }

    @Test
    fun `german variants get their own layout`() {
        assertEquals("de-DE", best("de-DE")?.tag, "the Neo layout must not win over plain German")
        assertEquals("qwertz", best("de-DE")?.layout)
        assertEquals("de-AT", best("de-AT")?.tag)
        assertEquals("swiss_german", best("de-CH")?.layout)
    }

    @Test
    fun `a region without a preset falls back to the language's home region`() {
        // Luxembourg has no preset; Austria or Neo would both be German, but neither is the obvious one.
        assertEquals("de-DE", best("de-LU")?.tag)
        assertEquals("fr-FR", best("fr-BE")?.tag)
        assertEquals("azerty", best("fr-BE")?.layout)
        assertEquals("pt-PT", best("pt-AO")?.tag)
    }

    @Test
    fun `system spellings the presets do not use still match`() {
        assertEquals("en-UK", best("en-GB")?.tag, "the British preset is tagged en-UK")
        assertEquals("nb-NO", best("no-NO")?.tag, "older devices report Bokmål as no")
    }

    @Test
    fun `a region match finds the preset that only adds a variant`() {
        assertEquals("zh-CN-pinyin", best("zh-CN")?.tag)
        assertEquals("ja-JP-jis", best("ja-JP")?.tag)
    }

    @Test
    fun `script and extensions on the system locale are ignored`() {
        val zhHans = FlorisLocale.from(java.util.Locale.forLanguageTag("zh-Hans-CN"))
        assertEquals("zh-CN-pinyin", SubtypePresetMatcher.bestMatch(zhHans, presets) { it.locale }?.tag)
    }

    @Test
    fun `a language with several presets gets the first one`() {
        assertEquals("jcuken_russian", best("ru-RU")?.layout)
        assertEquals("hindi_varnamala", best("hi-IN")?.layout)
    }

    @Test
    fun `a language without a preset gets nothing`() {
        assertNull(best("sw-KE"))
        assertNull(best(""))
    }

    @Test
    fun `seeding keeps the system order with the primary language first`() {
        assertEquals(listOf("de-DE", "en-US"), seed("de-DE", "en-US"))
        assertEquals(listOf("en-US", "de-DE"), seed("en-US", "de-DE"))
    }

    @Test
    fun `seeding skips languages without a preset`() {
        assertEquals(listOf("de-CH", "fr-CH"), seed("sw-KE", "de-CH", "fr-CH"))
        assertEquals(emptyList<String>(), seed("sw-KE"))
        assertEquals(emptyList<String>(), seed())
    }

    @Test
    fun `seeding adds one keyboard per language`() {
        // de-AT behind de-DE would be the same keyboard twice on the language-switch key.
        assertEquals(listOf("de-DE", "en-US"), seed("de-DE", "de-AT", "de-LU", "en-US", "en-GB"))
    }

    @Test
    fun `seeding stops at the limit`() {
        assertEquals(3, SubtypePresetMatcher.SEED_LIMIT)
        assertEquals(listOf("de-DE", "fr-FR", "it-IT"), seed("de-DE", "fr-FR", "it-IT", "es-ES", "en-US"))
    }
}

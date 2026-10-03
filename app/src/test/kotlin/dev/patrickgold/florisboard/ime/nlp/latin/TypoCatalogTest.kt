/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The typo lists ([TypoCatalog]): how a file is read, how a fix takes the shape of what was typed, and —
 * the part that actually breaks — whether the shipped lists still agree with the shipped dictionaries.
 *
 * A typo the dictionary holds is a typo the keyboard never corrects, because a known word is never
 * corrected; a fix the dictionary does not hold is one the spell checker would underline the moment it
 * went in. Both have to stay true through every regeneration of a word list, which is why they are
 * checked here against the real files and not only by `tools/glide-dict/generate.py`.
 */
class TypoCatalogTest {

    // ── Parsing ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `comments, blank lines and malformed lines are skipped`() {
        val parsed = TypoCatalog.parse(
            listOf(
                "# a comment\twith a tab in it",
                "teh\tthe",
                "",
                "no tab here",
                "\tmissing typo",
                "empty-fix\t",
                "same\tsame",
                "alot\ta lot",
            ).joinToString("\n"),
        )
        assertEquals(mapOf("teh" to "the", "alot" to "a lot"), parsed)
    }

    @Test
    fun `typos are keyed lowercased and corrections keep their own case`() {
        val parsed = TypoCatalog.parse("Standart\tStandard\nTEH\tthe\n")
        assertEquals("Standard", parsed["standart"])
        assertEquals("the", parsed["teh"])
    }

    @Test
    fun `windows line endings do not leak into a correction`() {
        assertEquals(mapOf("teh" to "the"), TypoCatalog.parse("teh\tthe\r\n"))
    }

    @Test
    fun `the first entry for a typo wins`() {
        assertEquals("the", TypoCatalog.parse("teh\tthe\nteh\ttea\n")["teh"])
    }

    // ── Case ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the fix is written the way the typo was`() {
        assertEquals("the", TypoCatalog.matchCase("teh", "the"))
        assertEquals("The", TypoCatalog.matchCase("Teh", "the"))
        assertEquals("THE", TypoCatalog.matchCase("TEH", "the"))
    }

    @Test
    fun `a fix of more than one word is cased as a whole`() {
        assertEquals("a lot", TypoCatalog.matchCase("alot", "a lot"))
        assertEquals("A lot", TypoCatalog.matchCase("Alot", "a lot"))
        assertEquals("A LOT", TypoCatalog.matchCase("ALOT", "a lot"))
    }

    @Test
    fun `a German noun keeps its capital when the typo had none`() {
        assertEquals("Standard", TypoCatalog.matchCase("standart", "Standard"))
        assertEquals("Standard", TypoCatalog.matchCase("Standart", "Standard"))
        assertEquals("STANDARD", TypoCatalog.matchCase("STANDART", "Standard"))
        assertEquals("aus Versehen", TypoCatalog.matchCase("ausversehen", "aus Versehen"))
    }

    @Test
    fun `a capital in the middle is not all caps`() {
        assertEquals("the", TypoCatalog.matchCase("tEH", "the"))
        assertEquals("The", TypoCatalog.matchCase("TEh", "the"))
    }

    // ── Loading ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a language without a file has no list`() {
        val catalog = TypoCatalog { null }
        assertTrue(catalog.forLang("xx").isEmpty())
        assertNull(catalog.correctionFor("xx", "teh"))
    }

    @Test
    fun `a file that cannot be read is no list rather than a crash`() {
        val catalog = TypoCatalog { throw java.io.IOException("gone") }
        assertNull(catalog.correctionFor("en", "teh"))
    }

    @Test
    fun `each language is read once and looked up by its own path`() {
        val reads = mutableListOf<String>()
        val catalog = TypoCatalog { path ->
            reads.add(path)
            if (path == TypoCatalog.pathFor("en")) "teh\tthe\n" else null
        }
        assertEquals("The", catalog.correctionFor("en", "Teh"))
        assertEquals("the", catalog.correctionFor("en", "teh"))
        assertNull(catalog.correctionFor("en", "the"))
        assertNull(catalog.correctionFor("fr", "teh"))
        assertNull(catalog.correctionFor("fr", "teh"))
        assertEquals(listOf("ime/dict/en_typos.txt", "ime/dict/fr_typos.txt"), reads)
    }

    // ── The shipped lists against the shipped dictionaries ───────────────────────────────────────

    private fun shippedList(lang: String): Map<String, String> =
        TypoCatalog.parse(EvalKeyboard.dictFile("${lang}_typos.txt").readText())

    @Test
    fun `every line of a shipped list is an entry or a comment`() {
        // parse() skips a malformed line without a word, which is right at runtime and wrong here: a
        // line with a space where the tab should be would quietly stop correcting anything.
        for (lang in listOf("en", "de")) {
            val lines = EvalKeyboard.dictFile("${lang}_typos.txt").readLines()
            val entries = lines.filter { it.isNotBlank() && !it.startsWith("#") }
            for (line in entries) {
                val parts = line.split('\t')
                assertTrue(
                    parts.size == 2 && parts.all { it.isNotBlank() && it == it.trim() },
                    "$lang: malformed line '$line'",
                )
            }
            assertEquals(entries.size, shippedList(lang).size, "$lang: a typo is listed twice")
        }
    }

    @Test
    fun `the English list is sizeable and holds the famous ones`() {
        val list = shippedList("en")
        assertTrue(list.size >= 300, "only ${list.size} English typos")
        assertEquals("the", list["teh"])
        assertEquals("receive", list["recieve"])
        assertEquals("definitely", list["definately"])
        assertEquals("a lot", list["alot"])
    }

    @Test
    fun `no English typo is a dictionary word, and every fix is`() {
        val dict = EvalKeyboard.readDict("en.json")
        val list = shippedList("en")
        val known = list.keys.filter { it in dict }
        assertTrue(known.isEmpty(), "in en.json, so never corrected: $known")
        val missing = list.values.flatMap { it.split(' ') }.filter { it !in dict }.distinct()
        assertTrue(missing.isEmpty(), "fixes en.json does not hold: $missing")
    }

    @Test
    fun `no German typo is a dictionary word, and every fix is`() {
        // de.json carries the noun capital (`Standard`) while a typo is matched lowercased, so both sides
        // are compared without case: `Versehen` in `aus Versehen` is stored as the verb `versehen`.
        val dict = EvalKeyboard.readDict("de.json").keys.mapTo(HashSet()) { it.lowercase() }
        val list = shippedList("de")
        val known = list.keys.filter { it in dict }
        assertTrue(known.isEmpty(), "in de.json, so never corrected: $known")
        val missing = list.values.flatMap { it.split(' ') }.filter { it.lowercase() !in dict }.distinct()
        assertTrue(missing.isEmpty(), "fixes de.json does not hold: $missing")
    }

    @Test
    fun `no list rewrites an apostrophe form`() {
        // Contractions belong to the apostrophe restoration, which decides from corpus evidence; a second
        // rule for the same words here would be two places to keep in agreement.
        for (lang in listOf("en", "de")) {
            val list = shippedList(lang)
            val apostrophes = list.filter { (typo, fix) -> '\'' in typo || '\'' in fix || '’' in fix }
            assertTrue(apostrophes.isEmpty(), "$lang: $apostrophes")
        }
    }
}

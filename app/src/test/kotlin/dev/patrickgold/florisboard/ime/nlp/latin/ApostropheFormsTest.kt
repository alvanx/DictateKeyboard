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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * English apostrophe restoration (issue #212, English follow-up): which forms reach the strip, in what
 * order, and which ones Space may take.
 *
 * The first half pins the rules on their own; the second runs them over the shipped English dictionary,
 * because the reason they exist at all is a fact about that file — `its` and `it's` stored at the same
 * frequency — and a regenerated dictionary is exactly what could quietly undo them.
 */
class ApostropheFormsTest {

    private val en = ApostropheForms.ENGLISH

    // --- which forms are offered -----------------------------------------------------------------

    @Test
    fun `an English form as common as the typed word is offered`() {
        // its and it's, both stored at 253. The strict rule this replaced offered nothing here.
        assertTrue(ApostropheForms.isOffered(en, formFreq = 253, typedFreq = 253))
    }

    @Test
    fun `an English form a little rarer is still offered, a clearly rarer one is not`() {
        val typed = 211
        assertTrue(ApostropheForms.isOffered(en, typed - ApostropheForms.CLOSE_MARGIN, typed))
        assertFalse(ApostropheForms.isOffered(en, typed - ApostropheForms.CLOSE_MARGIN - 1, typed))
        // well (252) against we'll (231): far apart, so a writer typing "well" is not offered "we'll".
        assertFalse(ApostropheForms.isOffered(en, formFreq = 231, typedFreq = 252))
    }

    @Test
    fun `every other language keeps the strict rule`() {
        // The French elision measurement assumes it: a second form admitted on a tie would make the
        // reading ambiguous and switch off a restoration the corpus would have taken.
        for (lang in listOf("fr", "de", "es", "it")) {
            assertFalse(ApostropheForms.isOffered(lang, formFreq = 200, typedFreq = 200), lang)
            assertTrue(ApostropheForms.isOffered(lang, formFreq = 201, typedFreq = 200), lang)
        }
    }

    @Test
    fun `two letters are enough in English and nowhere else`() {
        assertEquals(2, ApostropheForms.minTypedLength(en))
        for (lang in listOf("fr", "de", "es", "it", "nl")) {
            assertEquals(3, ApostropheForms.minTypedLength(lang), lang)
        }
    }

    @Test
    fun `a two-letter English word only ever becomes an I contraction`() {
        assertTrue(ApostropheForms.admits(2, en, "i'm"))
        assertTrue(ApostropheForms.admits(2, en, "i'd"))
        // Letters spoken of as nouns; the dictionary holds all of them.
        for (form in listOf("a's", "o's", "v's", "n't")) assertFalse(ApostropheForms.admits(2, en, form), form)
        // Not in another language, whatever the form.
        assertFalse(ApostropheForms.admits(2, "fr", "i'm"))
        // Three letters and up are left to frequency, as before.
        assertTrue(ApostropheForms.admits(3, en, "it's"))
        assertTrue(ApostropheForms.admits(4, "fr", "c'est"))
    }

    // --- which one comes first -------------------------------------------------------------------

    @Test
    fun `without context, the more common spelling leads and a tie goes to what was typed`() {
        assertFalse(ApostropheForms.formLeads(formFreq = 253, typedFreq = 253, formContext = 0, typedContext = 0))
        assertTrue(ApostropheForms.formLeads(formFreq = 244, typedFreq = 229, formContext = 0, typedContext = 0))
        assertFalse(ApostropheForms.formLeads(formFreq = 208, typedFreq = 211, formContext = 0, typedContext = 0))
    }

    @Test
    fun `the previous word overrules frequency in either direction`() {
        // "think it's" — the tie is broken towards the contraction.
        assertTrue(ApostropheForms.formLeads(formFreq = 253, typedFreq = 253, formContext = 255, typedContext = 0))
        // "of its" — and the other way, even against a more common form.
        assertFalse(ApostropheForms.formLeads(formFreq = 253, typedFreq = 240, formContext = 0, typedContext = 4151))
    }

    // --- what Space may take ---------------------------------------------------------------------

    @Test
    fun `contractions nobody types bare on purpose are taken`() {
        val expected = mapOf(
            "dont" to "don't", "im" to "i'm", "didnt" to "didn't", "doesnt" to "doesn't", "isnt" to "isn't",
            "wasnt" to "wasn't", "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't",
            "havent" to "haven't", "hasnt" to "hasn't", "youre" to "you're", "theyre" to "they're",
            "thats" to "that's", "whats" to "what's", "ive" to "i've", "youve" to "you've",
            "theyve" to "they've", "weve" to "we've", "youll" to "you'll", "theyll" to "they'll",
            "itll" to "it'll",
        )
        for ((bare, contraction) in expected) {
            assertEquals(contraction, ApostropheForms.englishAutoCommitFor(bare), bare)
        }
    }

    @Test
    fun `a bare spelling that is a word someone meant is never taken`() {
        // Each of these is an ordinary English word on its own. Adding one to the list should mean
        // deleting it from here on purpose.
        val words = listOf("its", "well", "were", "wed", "shed", "hell", "ill", "cant", "wont", "lets", "shell", "id")
        for (bare in words) {
            assertNull(ApostropheForms.englishAutoCommitFor(bare), "$bare must stay the writer's word")
        }
    }

    @Test
    fun `every entry is its bare spelling with exactly one apostrophe put back`() {
        for (bare in allBare) {
            val contraction = ApostropheForms.englishAutoCommitFor(bare)!!
            assertEquals(1, contraction.count { it == '\'' }, contraction)
            assertEquals(bare, contraction.replace("'", ""), contraction)
            assertEquals(contraction.lowercase(), contraction, "keys are folded, so values must be too")
        }
    }

    @Test
    fun `the lookup takes the folded spelling only`() {
        // The provider folds before asking; a capitalised or apostrophe-carrying spelling is not a key.
        assertNull(ApostropheForms.englishAutoCommitFor("Dont"))
        assertNull(ApostropheForms.englishAutoCommitFor("don't"))
    }

    // --- against the shipped dictionary ----------------------------------------------------------

    /** The forms the provider offers for [typed] over [dict], best first — its own loop, minus the strip. */
    private fun formsFor(typed: String, dict: Map<String, Int>): List<String> {
        if (typed.length < ApostropheForms.minTypedLength(en)) return emptyList()
        val typedFreq = dict[typed] ?: 0
        return (1 until typed.length)
            .map { typed.substring(0, it) + "'" + typed.substring(it) }
            .mapNotNull { v -> dict[v]?.let { f -> v to f } }
            .filter { (v, f) ->
                ApostropheForms.admits(typed.length, en, v) && ApostropheForms.isOffered(en, f, typedFreq)
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private val shipped by lazy {
        // Folded the way the English index is: the highest frequency wins a shared lowercase key.
        val out = HashMap<String, Int>()
        for ((word, f) in EvalKeyboard.readDict("en.json")) {
            val k = word.lowercase()
            if ((out[k] ?: -1) < f) out[k] = f
        }
        out
    }

    @Test
    fun `its offers it's on the shipped dictionary`() {
        assertEquals(listOf("it's"), formsFor("its", shipped))
    }

    @Test
    fun `im and id offer I'm and I'd on the shipped dictionary`() {
        assertEquals(listOf("i'm"), formsFor("im", shipped))
        assertEquals(listOf("i'd"), formsFor("id", shipped))
    }

    @Test
    fun `no other two-letter word grows an apostrophe on the shipped dictionary`() {
        val twoLetter = shipped.keys.filter { it.length == 2 && it.all { c -> c in 'a'..'z' } }
        val offered = twoLetter.filter { formsFor(it, shipped).isNotEmpty() }.toSet()
        assertEquals(setOf("im", "id"), offered)
    }

    @Test
    fun `every contraction Space may take is in the shipped dictionary and offered for its bare spelling`() {
        // The provider only takes a form it is offering: an entry the dictionary does not hold would be
        // dead, and that should be noticed rather than silently lose its correction.
        for (bare in allBare) {
            val contraction = ApostropheForms.englishAutoCommitFor(bare)!!
            assertTrue(contraction in shipped, "$contraction is missing from en.json")
            assertTrue(contraction in formsFor(bare, shipped), "$bare does not offer $contraction")
        }
    }

    private companion object {
        /** Every bare spelling on the list. One the list drops fails the round-trip test below. */
        val allBare = listOf(
            "dont", "didnt", "doesnt", "isnt", "arent", "wasnt", "werent", "hasnt", "havent", "hadnt",
            "couldnt", "wouldnt", "shouldnt", "mustnt", "aint",
            "im", "ive", "youve", "weve", "theyve", "youll", "theyll", "itll", "youre", "theyre",
            "youd", "theyd", "wouldve", "couldve", "shouldve",
            "thats", "whats", "theres", "heres", "wheres", "whos", "hes", "shes",
        )
    }
}

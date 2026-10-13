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
import kotlin.test.assertTrue

/**
 * Bilingual typing: which keyboard language a sentence is being written in, and how the other one's
 * completions are worked into the strip (see [LanguageGuess]).
 *
 * The guess is measured against the shipped English and German dictionaries on hand-written sentences —
 * everyday messages in both languages, a switch from one to the other, and German sentences full of the
 * English words German borrows, which is the case that makes this hard: en.json holds `und` at 206 and
 * `das` at 197, de.json `online` at 175. Every position of every sentence is guessed twice, once with each
 * language as the one the keyboard is switched to, because a rule that only works when the keyboard is
 * already right is no rule at all.
 */
class LanguageGuessTest {

    private companion object {
        val LANGUAGES = listOf("en", "de")

        /** Fold key → highest frequency, the way the provider's index answers. */
        val FREQUENCIES: List<Map<String, Int>> = LANGUAGES.map { lang ->
            val out = HashMap<String, Int>()
            for ((word, f) in EvalKeyboard.readDict("$lang.json")) {
                val key = DictFold.foldKey(lang, word)
                if ((out[key] ?: -1) < f) out[key] = f
            }
            out
        }

        val ENGLISH = listOf(
            "I think we should meet tomorrow morning before the meeting starts",
            "Can you send me the file when you get a chance",
            "Thanks so much for the lovely dinner last night",
            "The weather is really nice today so I will go for a walk",
            "Do you want to grab a coffee later this week",
            "My flight was delayed by two hours because of the storm",
            "Please let me know if anything changes",
            "I am going to the store do you need anything",
            "She said the kids were already asleep when she got home",
            "We should probably book the hotel before prices go up",
            "Happy birthday hope you have a wonderful day",
            "Sorry I missed your call I was driving",
            "The report is almost done but I still need the numbers from finance",
            "Let us talk about it over lunch",
            "Did you see the game last night it was amazing",
        )

        val GERMAN = listOf(
            "Ich glaube wir sollten uns morgen früh vor dem Treffen sehen",
            "Kannst du mir die Datei schicken wenn du Zeit hast",
            "Vielen Dank für das schöne Abendessen gestern",
            "Das Wetter ist heute wirklich schön also gehe ich spazieren",
            "Hast du Lust diese Woche einen Kaffee zu trinken",
            "Mein Flug hatte wegen des Sturms zwei Stunden Verspätung",
            "Bitte sag mir Bescheid wenn sich etwas ändert",
            "Ich gehe gleich einkaufen brauchst du noch etwas",
            "Sie sagte die Kinder haben schon geschlafen als sie nach Hause kam",
            "Wir sollten das Hotel buchen bevor die Preise steigen",
            "Alles Gute zum Geburtstag ich wünsche dir einen schönen Tag",
            "Tut mir leid ich habe deinen Anruf verpasst ich war am Fahren",
            "Der Bericht ist fast fertig aber mir fehlen noch die Zahlen",
            "Lass uns beim Mittagessen darüber reden",
            "Hast du gestern das Spiel gesehen es war unglaublich",
        )

        /** German as it is actually written at work and online: English nouns everywhere. */
        val GERMAN_WITH_LOANWORDS = listOf(
            "Ich habe gleich ein Meeting mit dem Team und danach einen Call",
            "Das Update ist jetzt online und das Feedback war super",
            "Wir machen heute Homeoffice weil das Wetter so schlecht ist",
            "Kannst du mir den Link zu dem Event schicken",
            "Der neue Computer ist schnell aber die Software hat Bugs",
            "Ich war am Wochenende beim Shopping und habe ein Handy gekauft",
            "Okay dann sehen wir uns später im Office",
        )

        fun guess(words: List<String>, active: String): String {
            val g = LanguageGuess.guess(words, LANGUAGES.size, LANGUAGES.indexOf(active)) { language, word ->
                FREQUENCIES[language][DictFold.foldKey(LANGUAGES[language], word)]
            }
            return LANGUAGES[g.index]
        }

        /** Every prefix of every sentence, guessed with each language active: the share guessed right. */
        fun accuracy(sentences: List<String>, lang: String): Double {
            var total = 0
            var right = 0
            for (active in LANGUAGES) for (sentence in sentences) {
                val words = sentence.split(' ')
                for (n in 1..words.size) {
                    total++
                    if (guess(words.subList(0, n).takeLast(LanguageGuess.CONTEXT_WORDS), active) == lang) right++
                }
            }
            return right.toDouble() / total
        }
    }

    @Test
    fun `reads the words before the cursor, oldest first, across punctuation`() {
        assertEquals(listOf("Thanks", "Bis", "morgen"), LanguageGuess.recentWords("Thanks! Bis morgen, "))
        assertEquals(listOf("don't", "worry"), LanguageGuess.recentWords("'don't' worry"))
        assertEquals(listOf("c", "d"), LanguageGuess.recentWords("a b c d", max = 2))
        assertEquals(emptyList(), LanguageGuess.recentWords("  12 ... "))
        assertEquals(emptyList(), LanguageGuess.recentWords(""))
    }

    @Test
    fun `everyday English and German are recognised whichever keyboard is active`() {
        val english = accuracy(ENGLISH, "en")
        val german = accuracy(GERMAN, "de")
        println("English positions guessed right: ${"%.3f".format(english)}, German: ${"%.3f".format(german)}")
        assertTrue(english >= 0.97, "English: $english")
        assertTrue(german >= 0.97, "German: $german")
    }

    @Test
    fun `a few borrowed English words do not turn a German sentence English`() {
        val german = accuracy(GERMAN_WITH_LOANWORDS, "de")
        println("Loanword-heavy German positions guessed right: ${"%.3f".format(german)}")
        assertTrue(german >= 0.9, "German with loanwords: $german")
    }

    @Test
    fun `switching language mid-message is followed within a few words`() {
        val needed = ArrayList<Int>()
        for ((from, to, lang) in listOf(Triple(GERMAN, ENGLISH, "en"), Triple(ENGLISH, GERMAN, "de"))) {
            for ((first, second) in from.zip(to)) for (active in LANGUAGES) {
                val before = first.split(' ')
                val after = second.split(' ')
                val n = (1..after.size).firstOrNull { n ->
                    guess((before + after.take(n)).takeLast(LanguageGuess.CONTEXT_WORDS), active) == lang
                } ?: Int.MAX_VALUE
                needed.add(n)
            }
        }
        val mean = needed.average()
        println("Words of the new language needed to follow a switch: mean ${"%.2f".format(mean)}, worst ${needed.max()}")
        assertTrue(mean <= 4.0, "mean $mean")
        assertTrue(needed.max() <= 7, "worst ${needed.max()}")
    }

    @Test
    fun `with nothing to go on the active keyboard language stays`() {
        assertEquals("de", guess(emptyList(), active = "de"))
        assertEquals("en", guess(emptyList(), active = "en"))
        // A name says nothing about either language.
        assertEquals("de", guess(listOf("Jannis"), active = "de"))
    }

    @Test
    fun `one word only the other language has is enough to switch`() {
        assertEquals("en", guess(listOf("the"), active = "de"))
        assertEquals("de", guess(listOf("nicht"), active = "en"))
    }

    @Test
    fun `the other language has to work harder the surer the guess is`() {
        assertEquals(LanguageGuess.HANDICAP_BASE, LanguageGuess.handicapFor(0.0))
        assertEquals(LanguageGuess.HANDICAP_MAX, LanguageGuess.handicapFor(100.0))
        assertTrue(LanguageGuess.handicapFor(0.5) < LanguageGuess.handicapFor(1.5))
    }

    private data class C(val text: String, val rank: Int?)

    private fun blend(primary: List<C>, secondary: List<C>, handicap: Int, max: Int = 8) =
        LanguageGuess.blend(primary, secondary, { it.rank }, handicap, max).map { it.text }

    @Test
    fun `a much commoner word from the other language goes ahead, a slightly commoner one does not`() {
        val german = listOf(C("Whisky", 170), C("Whistleblower", 140))
        val english = listOf(C("what", 254), C("when", 254), C("whole", 210))
        assertEquals(
            listOf("what", "when", "Whisky", "whole", "Whistleblower"),
            blend(german, english, handicap = 60),
        )
        val sch = listOf(C("schon", 240), C("schön", 235), C("schnell", 230))
        val school = listOf(C("school", 245))
        assertEquals(listOf("schon", "schön", "schnell", "school"), blend(sch, school, handicap = 60))
        assertEquals(listOf("school", "schon", "schön", "schnell"), blend(sch, school, handicap = 0))
    }

    @Test
    fun `the typed word, a fix and what Space takes keep their places`() {
        val primary = listOf(C("helo", null), C("hello", null), C("help", 150))
        val other = listOf(C("Hellas", 250))
        assertEquals(listOf("helo", "hello", "Hellas", "help"), blend(primary, other, handicap = 10))
    }

    @Test
    fun `the strip never grows past its size`() {
        val primary = listOf(C("a", 200), C("b", 190), C("c", 180))
        val other = listOf(C("x", 255), C("y", 130), C("z", 129))
        assertEquals(listOf("x", "a", "b", "c"), blend(primary, other, handicap = 10, max = 4))
        assertEquals(listOf("a", "b"), blend(primary, other, handicap = 80, max = 2))
        assertEquals(listOf("x", "y"), blend(emptyList(), other, handicap = 60, max = 2))
    }
}

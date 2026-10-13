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

/**
 * Which of the writer's keyboard languages the text before the cursor is written in, and how a strip
 * built in that language makes room for the others — the half of Gboard's bilingual typing that
 * multilingual typing (issue #190) did not have.
 *
 * Until now #190 only *accepted* a second language: its words were no longer underlined or corrected
 * away, but every completion, fix and prediction still came from whichever language the keyboard happened
 * to be switched to. Someone writing English with German selected got German completions for every
 * English word, German umlauts restored into English ones (`fur` → `für`) and German next-word guesses
 * after English sentences. The guess here is what lets all of that follow the writing instead — glide
 * typing included ([glideFrequency]).
 *
 * Pure, so the rule is measured on real sentences rather than argued — see `LanguageGuessTest`.
 */
internal object LanguageGuess {

    /** How many words back the guess reads. Further back they would weigh next to nothing anyway. */
    const val CONTEXT_WORDS = 10

    /**
     * How much each word further back still counts, against the one just written.
     *
     * 0.8 follows a switch of language within about three words, and keeps a borrowed word or two
     * ("Meeting", "online", "Feedback") from flipping a German sentence to English. Measured on
     * hand-written English, German and loanword-heavy German sentences: 0.6–0.7 switch a word sooner but
     * misread twice as many positions in the loanword sentences, 0.9 needs a word more to switch.
     */
    const val RECENCY = 0.8

    /**
     * The head start the language the keyboard is switched to gets. Enough to settle a tie — a name, an
     * "Okay" both languages share — and never enough to outweigh a single word only the other one has.
     */
    const val ACTIVE_PRIOR = 0.25

    /**
     * How much more frequent (on the dictionaries' 128..255 scale, where about ten points are a factor
     * of e) a word from another language must be before it goes ahead of one from the guessed language:
     * [HANDICAP_BASE] plus [HANDICAP_PER_MARGIN] for every point the guess won by, up to [HANDICAP_MAX].
     *
     * So the two languages compete almost evenly while there is nothing to go on — the start of a
     * message, where "goes with whichever language I start typing in" is decided — and the other one
     * only gets through with its very common words once a sentence has made its language plain. The cap
     * is set by the strip it produces in a settled German sentence: at 30, `fr` offered `from` ahead of
     * `Frau` and `ki` offered `kids` ahead of `Kinder`; at 60 neither does, while `wh` and `beca`, which
     * German has nothing common for, still offer `what` and `because`.
     */
    const val HANDICAP_BASE = 10
    const val HANDICAP_PER_MARGIN = 20
    const val HANDICAP_MAX = 60

    /**
     * The guess: [index] into the languages that were offered, and [margin], how far it is ahead of the
     * runner-up — the confidence [handicapFor] turns into how hard the other languages have to work.
     */
    data class Guess(val index: Int, val margin: Double)

    /** Whether [c] belongs to a word as [recentWords] reads them: letters, combining marks, apostrophes. */
    private fun isWordPart(c: Char): Boolean = DictFold.isWordChar(c) || c == '\'' || c == '’'

    /**
     * The last [max] words of [text], oldest first.
     *
     * Unlike the context the corpus tables are read with, this walks straight across punctuation and
     * sentence ends: a language does not change at a full stop, and the sentence just finished is the
     * best evidence there is for the one being started.
     */
    fun recentWords(text: CharSequence, max: Int = CONTEXT_WORDS): List<String> {
        val out = ArrayList<String>(max)
        var end = text.length
        while (out.size < max) {
            while (end > 0 && !isWordPart(text[end - 1])) end--
            var start = end
            while (start > 0 && isWordPart(text[start - 1])) start--
            if (start == end) break
            val word = text.subSequence(start, end).toString().trim('\'', '’')
            if (word.isNotEmpty()) out.add(word)
            end = start
        }
        return out.asReversed()
    }

    /**
     * What one sighting of a word says for a language it has [frequency] in: nothing at the bottom of the
     * scale, where every dictionary keeps the stray foreign words its corpus happened to contain (en.json
     * has `nicht` at 153), up to 1 for the language's commonest words. A word a language does not know
     * says nothing for it either.
     *
     * Linear in the stored frequency, which is itself a logarithm of the corpus count — so the sum over a
     * sentence is a (tempered) log-likelihood, and a word much commoner in one language counts for it even
     * when the other knows it too: `die` and `der` lean German, `was` and `also` English.
     */
    private fun evidenceOf(frequency: Int): Double = (frequency - 128).coerceIn(0, 127) / 127.0

    /**
     * Which of [languageCount] languages [words] (oldest first) are most likely written in.
     *
     * [frequencyOf] answers a word's dictionary frequency in one of them, or null when it does not know
     * the word. [activeIndex] is the language the keyboard is switched to, -1 when it is not among them.
     */
    fun guess(
        words: List<String>,
        languageCount: Int,
        activeIndex: Int,
        frequencyOf: (language: Int, word: String) -> Int?,
    ): Guess {
        if (languageCount <= 1) return Guess(0, 0.0)
        val scores = DoubleArray(languageCount)
        if (activeIndex in 0 until languageCount) scores[activeIndex] += ACTIVE_PRIOR
        var weight = 1.0
        for (i in words.indices.reversed()) {
            for (language in 0 until languageCount) {
                val frequency = frequencyOf(language, words[i]) ?: continue
                scores[language] += weight * evidenceOf(frequency)
            }
            weight *= RECENCY
        }
        var best = 0
        for (language in 1 until languageCount) if (scores[language] > scores[best]) best = language
        var runnerUp = Double.NEGATIVE_INFINITY
        for (language in 0 until languageCount) {
            if (language != best && scores[language] > runnerUp) runnerUp = scores[language]
        }
        return Guess(best, scores[best] - runnerUp)
    }

    /** How much more frequent a word from another language must be to go ahead — see [HANDICAP_BASE]. */
    fun handicapFor(margin: Double): Int =
        (HANDICAP_BASE + HANDICAP_PER_MARGIN * margin.coerceAtLeast(0.0)).toInt().coerceAtMost(HANDICAP_MAX)

    /**
     * The guessed language's strip, [primary], with the other languages' completions, [secondary]
     * (most frequent first), worked in — at most [max] in all.
     *
     * A word from [secondary] goes ahead of one from [primary] only when it is more frequent by
     * [handicap]. [rankOf] gives each candidate's frequency, or null for one that must keep its place: the
     * typed word kept tappable for bypassing a fix (issue #150), a listed fix, and above all the candidate
     * Space is going to take. Nothing from [secondary] ever goes ahead of those, so what Space does and
     * what the strip draws bold stay exactly what the guessed language decided.
     *
     * Interleaving rather than appending, because appending would hide the other language behind five
     * rare words of the guessed one: `wh` in a German sentence has nothing common to offer, and `what` is
     * what was being typed.
     */
    fun <T> blend(
        primary: List<T>,
        secondary: List<T>,
        rankOf: (T) -> Int?,
        handicap: Int,
        max: Int,
    ): List<T> {
        val out = ArrayList<T>(max)
        var next = 0
        for (candidate in primary) {
            val rank = rankOf(candidate)
            if (rank != null) {
                while (next < secondary.size && out.size < max) {
                    val otherRank = rankOf(secondary[next]) ?: break
                    if (otherRank - handicap <= rank) break
                    out.add(secondary[next++])
                }
            }
            if (out.size >= max) break
            out.add(candidate)
        }
        while (next < secondary.size && out.size < max) out.add(secondary[next++])
        return out
    }

    /**
     * What glide typing weighs [word] by: its frequency in [guessed], the word data of the language being
     * written, or in one of [others], the other keyboard languages', less [handicap] — whichever is higher.
     * Null when none of them knows the word.
     *
     * A swipe has no typed letters to tell the languages apart, only a shape, and words that trace the same
     * path — `fur` and `für`, `team` and `Team` — are decided by this alone. So it follows the strip's rule:
     * another language needs [handicap] more to go ahead.
     *
     * With one exception: a spelling the language being written does not use, where it has the same word in
     * another case, never goes ahead of that one. German writes `Not`, `Team` and `Kind`, and a swipe in a
     * German sentence is the German word however much commoner English `not` is — the handicap alone, even
     * at its largest, left `not` ahead of `Not` (194 to 180) and with it one in seven of such pairs among
     * the 20,000 commonest words of each language. An English sentence keeps its `team` the same way.
     */
    fun glideFrequency(word: String, guessed: Map<String, Int>, others: List<Map<String, Int>>, handicap: Int): Int? {
        val own = guessed[word]
        var foreign: Int? = null
        for (data in others) {
            val frequency = data[word] ?: continue
            if (foreign == null || frequency - handicap > foreign) foreign = frequency - handicap
        }
        if (foreign == null) return own
        if (own != null) return maxOf(own, foreign)
        val ownSpelling = otherCaseIn(guessed, word) ?: return foreign
        return minOf(foreign, ownSpelling - 1)
    }

    /** The highest frequency [data] has [word] at in another case — lower, capitalised or all caps. */
    private fun otherCaseIn(data: Map<String, Int>, word: String): Int? {
        val lower = word.lowercase()
        var best: Int? = null
        for (form in arrayOf(lower, lower.replaceFirstChar { it.titlecase() }, word.uppercase())) {
            if (form == word) continue
            val frequency = data[form] ?: continue
            if (best == null || frequency > best) best = frequency
        }
        return best
    }
}

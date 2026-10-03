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
 * Which apostrophe forms of a word typed without one reach the strip, in what order, and which English
 * contraction Space may take on its own (issue #212, English follow-up).
 *
 * The restoration block in [LatinLanguageProvider.suggest] inserts an apostrophe at every position of
 * the typed word and keeps the spellings the dictionary knows. Three things were wrong with it for
 * English, and all three are decisions rather than lookups, which is why they live here:
 *
 *  - **A tie offered nothing.** A form was kept only when it was strictly more frequent than what was
 *    typed, and `en.json` stores `its` and `it's` at exactly 253 — so `it's`, one of the commonest words
 *    people mean when they type `its`, was never in the strip. See [CLOSE_MARGIN].
 *  - **Two letters were never looked at.** `im` and `id` are the commonest misses of all, and the block
 *    began at three. See [minTypedLength].
 *  - **Nothing was ever taken.** Every English contraction stayed a tap, even `dont`, which no one types
 *    on purpose. See [englishAutoCommitFor].
 *
 * French is left exactly as it was. Its forms are rebuilt and judged by a corpus ([ElisionEvidence]),
 * and everything below is keyed to English so that measurement cannot move.
 *
 * Pure functions with no Android dependency, so the rules are unit-testable without a device — same
 * reason [AutoCommitGate] and [ElisionEvidence] live on their own.
 */
object ApostropheForms {

    /** The one language these rules are written for. */
    const val ENGLISH = "en"

    /**
     * How many steps of the dictionary's 128..255 scale a form may sit *below* the typed spelling and
     * still be offered, in English.
     *
     * The scale is logarithmic, so a step is a ratio rather than a count: `generate.py` spreads the whole
     * 50,000-word list over 127 steps, which makes one step roughly a tenth in raw corpus count. Two
     * words that close are a coin toss the dictionary cannot call, and the strip is exactly the place to
     * leave such a call to the writer. Three steps — about a third — admits `its`/`it's` (a tie) and
     * `shed`/`she'd` (three below), and on the shipped list nothing else but possessives of words that
     * are themselves plurals (`gods`/`god's`), which are as fair an offer as any.
     *
     * Never a reason to *replace* anything: only [englishAutoCommitFor] decides that.
     */
    const val CLOSE_MARGIN = 3

    /**
     * Whether a form stored at [formFreq] is worth offering for a word typed at [typedFreq] (0 when the
     * typed spelling is not in the dictionary at all).
     *
     * Every language but English keeps the strict rule it always had: the French measurement in
     * `FrenchElisionEvalTest` assumes it, and a form admitted on a tie would turn a single reading into
     * two and quietly switch off the elision that [ElisionEvidence.mayReplace] would have taken.
     */
    fun isOffered(lang: String, formFreq: Int, typedFreq: Int): Boolean =
        if (lang == ENGLISH) formFreq >= typedFreq - CLOSE_MARGIN else formFreq > typedFreq

    /**
     * The shortest typed word the restoration looks at in [lang].
     *
     * Two in English, because `I'm` and `I'd` are the most-written contractions there are and both are
     * three characters with the apostrophe. Three everywhere else, so French in particular does not start
     * rebuilding elisions out of every two-letter word.
     */
    fun minTypedLength(lang: String): Int = if (lang == ENGLISH) 2 else 3

    /**
     * Whether [form] may be offered for a word of [typedLength] letters at all, before frequency is asked.
     *
     * A two-letter English word is only ever the pronoun missing its apostrophe. Without this, `as`, `is`
     * and `os` would each be one frequency tweak away from offering `a's`, `i's` and `o's` — letters
     * spoken of as nouns, which the dictionary holds and nobody means.
     */
    fun admits(typedLength: Int, lang: String, form: String): Boolean = when {
        typedLength >= 3 -> true
        typedLength == 2 && lang == ENGLISH -> form.startsWith("i'", ignoreCase = true)
        else -> false
    }

    /**
     * Whether a form should stand ahead of the typed word in the strip — which, with the strip's best
     * word in the middle slot, is the question of which one the writer sees first.
     *
     * The words before the cursor decide when they say anything: "of its" is written thousands of times
     * and "of it's" never, "think it's" the other way round. [formContext] and [typedContext] are the
     * bigram counts of each after the previous word, both 0 when there is no previous word or no table.
     * Otherwise frequency decides, and a tie goes to what was typed: it is a real word, and a coin toss
     * is no reason to push it aside.
     */
    fun formLeads(formFreq: Int, typedFreq: Int, formContext: Long, typedContext: Long): Boolean =
        if (formContext != typedContext) formContext > typedContext else formFreq > typedFreq

    /**
     * English contractions people type without the apostrophe, mapped from the folded bare spelling to
     * the folded contraction (issue #212).
     *
     * A list rather than a rule, and that is the safety of it. "Take the contraction when the bare
     * spelling is not a word" cannot be asked of `en.json`: it was built from subtitles, where the
     * apostrophe goes missing all the time, so it holds `dont` at 228 and `im` at 226 — as "known" as
     * `well`. A list says exactly which rewrites exist, and a wrong one is one line to delete.
     *
     * What is deliberately **not** here, because the bare spelling is a word someone meant: `its`, `well`,
     * `were`, `wed`, `shed`, `hell`, `ill`, `cant`, `wont`, `lets`, `shell`, `id` (ID) — and `hed`, `itd`
     * and `whens`, too rare to be worth a rule. Those are still offered in the strip; Space leaves them
     * alone.
     */
    private val ENGLISH_AUTO_COMMIT: Map<String, String> = mapOf(
        // n't
        "dont" to "don't",
        "didnt" to "didn't",
        "doesnt" to "doesn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "hasnt" to "hasn't",
        "havent" to "haven't",
        "hadnt" to "hadn't",
        "couldnt" to "couldn't",
        "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't",
        "mustnt" to "mustn't",
        "aint" to "ain't",
        // 'm 've 'll 're 'd — the pronoun forms, never the ones that spell another word
        "im" to "i'm",
        "ive" to "i've",
        "youve" to "you've",
        "weve" to "we've",
        "theyve" to "they've",
        "youll" to "you'll",
        "theyll" to "they'll",
        "itll" to "it'll",
        "youre" to "you're",
        "theyre" to "they're",
        "youd" to "you'd",
        "theyd" to "they'd",
        "wouldve" to "would've",
        "couldve" to "could've",
        "shouldve" to "should've",
        // 's
        "thats" to "that's",
        "whats" to "what's",
        "theres" to "there's",
        "heres" to "here's",
        "wheres" to "where's",
        "whos" to "who's",
        "hes" to "he's",
        "shes" to "she's",
    )

    /**
     * The contraction Space may put in place of [typedFolded] in English, or null.
     *
     * The caller still has to find it among the forms the dictionary offers, and still owes the other
     * checks: that autocorrect is on at all, that the bare spelling is not an ordinary word in another
     * language the writer types in — `dont` is French and `im` is German — nor in their own dictionary, and
     * that it was not typed in capitals, which the strip cannot give back. Every strength level takes it:
     * the strengths measure how far the fingers were off, and here they were not off at all.
     */
    fun englishAutoCommitFor(typedFolded: String): String? = ENGLISH_AUTO_COMMIT[typedFolded]
}

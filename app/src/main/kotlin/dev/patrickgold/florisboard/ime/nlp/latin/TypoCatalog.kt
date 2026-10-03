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

import java.util.concurrent.ConcurrentHashMap

/**
 * The misspellings a language is known for, each with the word that was meant: `teh` → `the`,
 * `recieve` → `receive`, `alot` → `a lot`. Read from the bundled `ime/dict/<lang>_typos.txt`, one
 * `typo<TAB>correction` per line.
 *
 * The edit-distance corrector cannot be relied on for these, and the reason is not distance. The word
 * lists are counted from subtitles, and subtitles are typed by people: `en.json` held `teh`, `recieve`,
 * `definately` and `alot` as words, so they were "known", and a known word is never corrected or even
 * underlined. They have been taken out of the dictionary (and `tools/glide-dict/generate.py` keeps them
 * out), which makes them correctable again — but "correctable" still leaves the choice to a reader that
 * has to guess among neighbours, while a famous misspelling has exactly one answer and the list says
 * which. `definately` is one letter from `definitely` and two from nothing else useful; `alot` is not
 * within reach of `a lot` at all, because the corrector never inserts a space.
 *
 * A language without a file simply has no list: every lookup answers null and nothing else changes.
 *
 * @param read Reads an asset by path, or returns null when it does not exist. A parameter rather than a
 *  Context so the parsing and the case rule can be tested on the JVM, without Android.
 */
class TypoCatalog(private val read: (path: String) -> String?) {
    /** Per language, typo (lowercased) → correction. An empty map once a language is known to have none. */
    private val byLang = ConcurrentHashMap<String, Map<String, String>>()

    /** The typo list for [lang], read on first use and kept. Never throws: a broken file is no list. */
    fun forLang(lang: String): Map<String, String> = byLang.getOrPut(lang) {
        runCatching { read(pathFor(lang))?.let { parse(it) } }.getOrNull().orEmpty()
    }

    /**
     * What [typed] should have been in [lang], written the way it was typed ([matchCase]), or null when
     * it is not a listed misspelling.
     */
    fun correctionFor(lang: String, typed: String): String? =
        forLang(lang)[typed.lowercase()]?.let { matchCase(typed, it) }

    companion object {
        fun pathFor(lang: String): String = "ime/dict/${lang}_typos.txt"

        /**
         * `typo<TAB>correction` lines into a map keyed by the lowercased typo. Blank lines and lines
         * starting with `#` are skipped, as is anything without both halves or whose correction would be
         * the typo itself (that would be a "fix" that changes nothing and still claims the space bar).
         * The first entry for a typo wins, so a duplicate further down cannot silently change it.
         */
        fun parse(text: String): Map<String, String> {
            val out = HashMap<String, String>()
            for (raw in text.lineSequence()) {
                val line = raw.trimEnd('\r')
                if (line.isBlank() || line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val typo = line.substring(0, tab).trim().lowercase()
                val correction = line.substring(tab + 1).trim()
                if (typo.isEmpty() || correction.isEmpty() || correction.lowercase() == typo) continue
                out.putIfAbsent(typo, correction)
            }
            return out
        }

        /**
         * [correction] in the shape [typed] was written in: all capitals stay all capitals (`TEH` →
         * `THE`), a capital first letter stays one (`Teh` → `The`, `Alot` → `A lot`), and anything else
         * leaves the correction as the list spells it — which is what keeps a German noun capitalised
         * when the typo was not (`standart` → `Standard`).
         *
         * All capitals needs more than one letter to mean anything: a lone capital is a sentence start.
         */
        fun matchCase(typed: String, correction: String): String {
            val letters = typed.filter { it.isLetter() }
            return when {
                letters.length > 1 && letters.none { it.isLowerCase() } -> correction.uppercase()
                typed.firstOrNull()?.isUpperCase() == true -> correction.replaceFirstChar { it.uppercaseChar() }
                else -> correction
            }
        }
    }
}

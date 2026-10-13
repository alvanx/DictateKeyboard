/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.prompts

import kotlin.math.floor

/**
 * "My words": one list the user keeps, used both as spelling hints for the speech AI and as words the
 * keyboard suggests while typing.
 *
 * There used to be two lists that did one job each: the dictation custom words (`dictate.customWords`,
 * sent with every request) and the keyboard's personal dictionary. People expected one to be the other.
 * The least risky way to make them one is to keep the dictation list as the single source of truth and
 * copy its words into the personal dictionary — the dictionary itself, its database and the keyboard code
 * that reads it stay exactly as they were. [MyWordsSync] does the copying; this file is only the rules,
 * kept free of Android so they can be tested.
 *
 * Words are compared ignoring case everywhere, the same way [PersonalDictionaryWriter] does: "Søren"
 * and "søren" are one word to the keyboard, and a second entry would only clutter the list.
 */
object MyWords {
    /** How many words the Dictionary tab shows as chips before "+ N more". */
    const val PREVIEW_COUNT = 7

    /**
     * What one entry typically costs in the speech AI's context, its ", " included, used to say how many
     * words fit while the list is still too short to measure. 4.8 tokens is a name like "Kubernetes" or
     * "Søren Berg"; it makes an empty list read "0 of 40", which is about right for real lists.
     */
    private const val TYPICAL_TOKENS_PER_WORD = 4.8

    /** The entries of [raw], with repeats dropped (the first spelling wins). */
    fun parse(raw: String?): List<String> = distinct(CustomWordList.parse(raw))

    /** [words] without case-insensitive repeats, keeping the first spelling and the order. */
    fun distinct(words: List<String>): List<String> {
        val seen = HashSet<String>()
        return words.filter { seen.add(it.lowercase()) }
    }

    /**
     * What the user typed into the "Add a word or name" field, as entries: split on commas and new lines
     * like the list itself, inner whitespace collapsed, blanks dropped.
     */
    fun entriesOf(input: String): List<String> = distinct(
        input.split(',', '\n')
            .map { WHITESPACE_RUN.replace(it.trim(), " ") }
            .filter { it.isNotEmpty() },
    )

    /**
     * [raw] with [input]'s entries added at the **front**, as a new preference value.
     *
     * The front, because the speech AI only reads as many words as fit (see [usage]) and it reads them
     * from the front: a word somebody just added is the one they are about to say. Adding a word that is
     * already there moves it to the front with the new spelling, which is also how a capitalisation gets
     * fixed ("iphone" → "iPhone").
     */
    fun add(raw: String?, input: String): String {
        val added = entriesOf(input)
        if (added.isEmpty()) return raw.orEmpty()
        val addedKeys = added.mapTo(HashSet()) { it.lowercase() }
        val rest = parse(raw).filterNot { it.lowercase() in addedKeys }
        return join(added + rest, raw)
    }

    /** [raw] without [word] (ignoring case), as a new preference value. */
    fun remove(raw: String?, word: String): String {
        val key = word.trim().lowercase()
        return join(parse(raw).filterNot { it.lowercase() == key }, raw)
    }

    /**
     * [raw] with [old] (ignoring case) spelled [new] instead, in the same place. If [new] is already in
     * the list as well, the two become one.
     */
    fun rename(raw: String?, old: String, new: String): String {
        val key = old.trim().lowercase()
        val replacement = WHITESPACE_RUN.replace(new.trim(), " ")
        if (replacement.isEmpty()) return remove(raw, old)
        return join(distinct(parse(raw).map { if (it.lowercase() == key) replacement else it }), raw)
    }

    /**
     * [raw] with [words] added at the end, leaving the user's own text exactly as written — the same rule
     * as [CustomWordList.plan], so a carefully one-word-per-line list is not collapsed into a comma run.
     * Words already in [raw] are skipped.
     */
    fun append(raw: String?, words: List<String>): String {
        val known = CustomWordList.parse(raw).mapTo(HashSet()) { it.lowercase() }
        val new = distinct(words).filter { known.add(it.lowercase()) }
        if (new.isEmpty()) return raw.orEmpty()
        val separator = separatorFor(raw)
        val joined = new.joinToString(separator)
        return if (raw.isNullOrBlank()) joined else raw.trimEnd().trimEnd(',').trimEnd() + separator + joined
    }

    /** [words] written the way [like] already separates its entries (one per line unless it uses commas). */
    fun join(words: List<String>, like: String?): String = words.joinToString(separatorFor(like))

    private fun separatorFor(raw: String?): String =
        if (raw != null && raw.contains(',') && !raw.contains('\n')) ", " else "\n"

    /** What the one-time merge of the two old lists does. */
    data class Merge(
        /** Personal-dictionary words that join My words, in dictionary order, behind the existing ones. */
        val toMyWords: List<String>,
        /** Existing My words that the personal dictionary did not have yet. */
        val toDictionary: List<String>,
    )

    /**
     * Brings the two old lists together so that nothing in either is lost: every dictation word goes into
     * the personal dictionary, every personal-dictionary word into My words. The dictation words keep
     * their place at the front, since they are the ones that were already reaching the speech AI.
     */
    fun merge(myWords: List<String>, dictionaryWords: List<String>): Merge {
        val mine = myWords.mapTo(HashSet()) { it.lowercase() }
        val dict = dictionaryWords.mapTo(HashSet()) { it.lowercase() }
        return Merge(
            toMyWords = distinct(dictionaryWords.map { it.trim() }.filter { it.isNotEmpty() })
                .filterNot { it.lowercase() in mine },
            toDictionary = distinct(myWords).filterNot { it.lowercase() in dict },
        )
    }

    /** What has to change in the personal dictionary to follow My words. */
    data class Mirror(
        /** Delete these from the dictionary (matched ignoring case). Applied first. */
        val remove: List<String>,
        /** Then add these. */
        val add: List<String>,
    )

    /**
     * The dictionary changes that follow My words from [previous] (what was last copied) to [current].
     *
     * Only words that were in [previous] are ever removed, so a word the user put into the personal
     * dictionary some other way — by hand, from contacts, learned from typing — is never touched. A
     * changed capitalisation is a removal plus an addition; and because removing matches ignoring case,
     * any word still in the list whose spelling was hit by a removal is added back afterwards.
     */
    fun mirror(previous: List<String>, current: List<String>): Mirror {
        val now = distinct(current)
        val nowExact = now.toHashSet()
        val before = distinct(previous)
        val beforeExact = before.toHashSet()

        val remove = before.filter { it !in nowExact }
        val removedKeys = remove.mapTo(HashSet()) { it.lowercase() }
        val add = now.filter { it !in beforeExact || it.lowercase() in removedKeys }
        return Mirror(remove = remove, add = add)
    }

    /** How much of the list reaches the speech AI. */
    data class Usage(
        /** Words that go to the speech AI: the first [sent] of the list. */
        val sent: Int,
        /** About how many words fit in all; equals [sent] once the list is over. */
        val limit: Int,
        /** Words in the list. */
        val total: Int,
    ) {
        /** Some words are only used for typing, because the speech AI has no room for them. */
        val isFull: Boolean get() = total > sent

        /** How full the bar is, 0 to 1. */
        val fraction: Float get() = when {
            isFull -> 1f
            limit <= 0 -> 0f
            else -> (sent.toFloat() / limit).coerceIn(0f, 1f)
        }
    }

    /**
     * How many of [words] the speech AI gets, and about how many it could.
     *
     * The real limit is in tokens ([CustomWordList.TOKEN_BUDGET]), since that is what the speech model
     * counts, but "112 of 192 tokens" means nothing to anybody. So it is said in words: how many fit
     * exactly, and — while there is still room — how many would fit if the rest of the room went to
     * words like the ones already in the list.
     */
    fun usage(words: List<String>, budget: Int = CustomWordList.TOKEN_BUDGET): Usage {
        val sent = CustomWordList.countWithinBudget(words, budget)
        if (words.isEmpty()) return Usage(0, floor(budget / TYPICAL_TOKENS_PER_WORD).toInt(), 0)
        if (sent < words.size) return Usage(sent, sent, words.size)
        val used = CustomWordList.estimateTokens(words)
        val perWord = (used.toDouble() / sent).coerceAtLeast(1.0)
        val room = floor((budget - used).coerceAtLeast(0) / perWord).toInt()
        return Usage(sent, sent + room, words.size)
    }

    private val WHITESPACE_RUN = Regex("\\s+")
}

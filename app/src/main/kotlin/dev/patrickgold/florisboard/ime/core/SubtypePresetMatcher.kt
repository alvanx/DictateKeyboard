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
import java.util.Locale

/**
 * Finds the subtype preset that fits a locale: for the subtype editor's suggestions, for pre-filling a
 * subtype whose language was just picked, and for the keyboard languages an install starts with
 * (see [SubtypeManager]).
 *
 * The matching is generic over the candidate, with [SubtypePreset] overloads on top, so it can be tested
 * on the JVM with bare locales. Nothing in here touches Android or the extension manager.
 */
object SubtypePresetMatcher {
    /**
     * How many keyboard languages are seeded from the system language list at most. Every one of them
     * is a stop on the language-switch key, and a fourth language on the system list is rarely one that
     * is typed in.
     */
    const val SEED_LIMIT = 3

    // The presets and Android spell a few locales differently: the British preset is en-UK where the
    // system says en-GB, and older devices report Norwegian Bokmål as `no` where the preset says `nb`.
    private val COUNTRY_ALIASES = mapOf("GB" to "UK")
    private val LANGUAGE_ALIASES = mapOf("no" to "nb")

    /**
     * The candidate that fits [locale] best, or null when none shares its language. In order of
     * preference: the same language, region and variant; the same language and region (zh-CN finds
     * zh-CN-pinyin, ja-JP finds ja-JP-jis); then the same language, preferring a plain preset for the
     * language's home region (de-LU finds de-DE rather than de-AT or the Neo layout).
     *
     * Compared field by field rather than with [FlorisLocale.equals], which would also compare the script
     * and extensions a system locale may carry (zh-Hans-CN, ar-EG-u-nu-latn) and no preset ever does.
     * Among equally good candidates the first one in [candidates] wins.
     */
    fun <T> bestMatch(locale: FlorisLocale, candidates: List<T>, localeOf: (T) -> FlorisLocale): T? {
        val language = LANGUAGE_ALIASES[locale.language] ?: locale.language
        if (language.isEmpty()) return null
        val country = COUNTRY_ALIASES[locale.country] ?: locale.country
        val sameLanguage = candidates.filter { localeOf(it).language == language }
        if (sameLanguage.isEmpty()) return null
        return sameLanguage.firstOrNull { localeOf(it).country == country && localeOf(it).variant == locale.variant }
            ?: sameLanguage.firstOrNull { country.isNotEmpty() && localeOf(it).country == country }
            ?: sameLanguage.minBy { genericRank(localeOf(it)) }
    }

    /**
     * The best candidate for each of [locales], in their order, skipping locales without one and never
     * repeating a candidate (de-DE and de-LU on the same list both find de-DE).
     */
    fun <T> matchAll(locales: List<FlorisLocale>, candidates: List<T>, localeOf: (T) -> FlorisLocale): List<T> {
        return locales.mapNotNull { bestMatch(it, candidates, localeOf) }.distinct()
    }

    /**
     * The candidates to seed an empty subtype list with: [matchAll], but one per language — de-DE and
     * de-AT on the system list would be the same keyboard twice behind the language-switch key — and at
     * most [limit] of them. The first one is the primary system language's.
     */
    fun <T> seedFor(
        locales: List<FlorisLocale>,
        candidates: List<T>,
        limit: Int = SEED_LIMIT,
        localeOf: (T) -> FlorisLocale,
    ): List<T> {
        return matchAll(locales, candidates, localeOf).distinctBy { localeOf(it).language }.take(limit)
    }

    fun bestMatch(locale: FlorisLocale, presets: List<SubtypePreset>): SubtypePreset? =
        bestMatch(locale, presets) { it.locale }

    fun matchAll(locales: List<FlorisLocale>, presets: List<SubtypePreset>): List<SubtypePreset> =
        matchAll(locales, presets) { it.locale }

    fun seedFor(locales: List<FlorisLocale>, presets: List<SubtypePreset>): List<SubtypePreset> =
        seedFor(locales, presets) { it.locale }

    /**
     * Lower is a better stand-in for a language as a whole: a preset without a variant whose region is
     * none or the language's own (de-DE, fr-FR, ru), then any other preset without a variant, then the rest.
     */
    private fun genericRank(locale: FlorisLocale): Int {
        if (locale.variant.isNotEmpty()) return 2
        val homeRegion = locale.language.uppercase(Locale.ROOT)
        return if (locale.country.isEmpty() || locale.country == homeRegion) 0 else 1
    }
}

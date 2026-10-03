/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.core

import android.content.Context
import android.content.res.Resources
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.keyboard.CurrencySet
import dev.patrickgold.florisboard.ime.nlp.han.PinyinPackManager
import dev.patrickgold.florisboard.ime.nlp.latin.GlideDictionaryManager
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.devtools.flogError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.florisboard.lib.kotlin.collectLatestIn

val SubtypeJsonConfig = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = false
}

/**
 * Class which acts as a high level helper for the raw implementation of subtypes in the prefs. Additionally provides
 * helper methods for the in-keyboard language switch process.
 */
class SubtypeManager(context: Context) {
    private val prefs by FlorisPreferenceStore
    private val appContext = context.applicationContext
    private val application by context.appContext()
    private val extensionManager by context.extensionManager()
    private val keyboardManager by context.keyboardManager()
    private val scope = CoroutineScope(Dispatchers.Default)

    val subtypesFlow: StateFlow<List<Subtype>>
        field = MutableStateFlow(listOf())
    inline var subtypes
        get() = subtypesFlow.value
        private set(v) { subtypesFlow.value = v }

    val activeSubtypeFlow: StateFlow<Subtype>
        field = MutableStateFlow(Subtype.DEFAULT)
    inline var activeSubtype
        get() = activeSubtypeFlow.value
        private set(v) { activeSubtypeFlow.value = v }

    init {
        prefs.localization.subtypes.asFlow().collectLatestIn(scope) { listRaw ->
            flogDebug { listRaw }
            val list = if (listRaw.isNotBlank()) {
                SubtypeJsonConfig.decodeFromString<List<Subtype>>(listRaw)
            } else {
                emptyList()
            }
            subtypes = list
            evaluateActiveSubtype(list)
        }
        scope.launch {
            // This scope has no supervisor: a failure here must not take the subtype collector above
            // down with it, and the keyboard works fine on the English fallback.
            try {
                seedFromSystemLocalesIfNeeded()
            } catch (e: Exception) {
                flogError { "Seeding subtypes from system locales failed: $e" }
            }
        }
    }

    /**
     * Gives an install without any subtype the keyboard languages of the device, once.
     *
     * With an empty list the keyboard falls back to [Subtype.DEFAULT], English QWERTY, whatever the device
     * speaks — and onboarding never asks — so a German phone typed on an English layout until someone found
     * the Languages & Layouts screen. Instead, each system language (in the order the system ranks them)
     * gets its matching preset, see [SubtypePresetMatcher.seedFor]. Languages without a preset are skipped;
     * when none has one the list stays empty and the English fallback applies as before.
     *
     * Runs once per install, guarded by `prefs.localization.subtypesSeeded`: an existing install that
     * still has an empty list is seeded on its first start after the update, and a list the user empties
     * or changes later is never touched again.
     */
    private suspend fun seedFromSystemLocalesIfNeeded() {
        // The store loads asynchronously while this manager is being constructed, and before then the
        // subtype list reads as the empty default on every install.
        application.preferenceStoreLoaded.first { it }
        if (prefs.localization.subtypesSeeded.get()) return
        // The presets come from the bundled localization extension, which is indexed off the main thread at
        // app start. Taken from the extension index rather than the keyboard manager's copy of it, so that
        // this does not construct the keyboard manager on a background thread just to read a list.
        val presets = extensionManager.keyboardExtensions
            .first { extensions -> extensions.any { it.subtypePresets.isNotEmpty() } }
            .flatMap { it.subtypePresets }
        val listRaw = prefs.localization.subtypes.get()
        val existing: List<Subtype>? = if (listRaw.isBlank()) {
            emptyList()
        } else {
            runCatching { SubtypeJsonConfig.decodeFromString<List<Subtype>>(listRaw) }.getOrNull()
        }
        // An unreadable list is left alone rather than overwritten with a guess.
        if (existing != null && existing.isEmpty()) {
            val seeded = SubtypePresetMatcher.seedFor(systemLocales(), presets)
            if (seeded.isNotEmpty()) {
                // Distinct ids in one go: addSubtype stamps each with the current millisecond and reads
                // the list back from the flow, so calling it in a loop would lose all but the last one.
                val now = System.currentTimeMillis()
                val list = seeded.mapIndexed { n, preset -> preset.toSubtype().copy(id = now + n) }
                flogDebug { "Seeding subtypes from system locales: ${list.map { it.toShortString() }}" }
                prefs.localization.subtypes.set(SubtypeJsonConfig.encodeToString(list))
                // Only the language the keyboard opens in: nobody was asked, and the others (4-14 MB each,
                // issue #334) still fetch themselves the first time the keyboard is switched to them.
                ensureLanguageData(list.first())
            }
        }
        // Set last, so a process that dies halfway gets another go on the next start.
        prefs.localization.subtypesSeeded.set(true)
    }

    /**
     * The device's languages in the user's order of preference, as set in the system settings. Read from
     * the system resources rather than the app's, which follow a per-app language if one is set. Also what
     * the keyboard's language menu offers to add, see [SelectSubtypePanel].
     */
    fun systemLocales(): List<FlorisLocale> {
        val localeList = Resources.getSystem().configuration.locales
        return (0 until localeList.size()).map { FlorisLocale.from(localeList.get(it)) }
    }

    private fun persistNewSubtypeList(list: List<Subtype>) = scope.launch {
        val listRaw = SubtypeJsonConfig.encodeToString(list)
        prefs.localization.subtypes.set(listRaw)
    }

    /**
     * Gets the active subtype and returns it. If the activeSubtypeId points to a non-existent
     * subtype, this method tries to determine a new active subtype.
     *
     * @return The active subtype or null, if the subtype list is empty or no new active subtype
     *  could be determined.
     */
    private fun evaluateActiveSubtype(list: List<Subtype>) = scope.launch {
        val activeSubtypeId = prefs.localization.activeSubtypeId.get()
        val subtype = list.find { it.id == activeSubtypeId } ?: list.firstOrNull() ?: Subtype.DEFAULT
        if (subtype.id != activeSubtypeId) {
            prefs.localization.activeSubtypeId.set(subtype.id)
        }
        activeSubtype = subtype
    }

    /**
     * Adds a given [subtype] to the subtype list, if it does not exist.
     *
     * @param subtype The subtype which should be added.
     * @return True if the subtype was added, false otherwise. A return value of false indicates
     *  that the subtype already exists.
     */
    fun addSubtype(subtype: Subtype): Boolean {
        val subtypeToAdd = subtype.copy(id = System.currentTimeMillis())
        val subtypeList = subtypes
        if (subtypeList.find { it.equalsExcludingId(subtype) } != null) {
            return false
        }
        val newSubtypeList = subtypeList + subtypeToAdd
        persistNewSubtypeList(newSubtypeList)
        ensureLanguageData(subtypeToAdd)
        return true
    }

    /**
     * Adds [subtype] like [addSubtype] and switches the keyboard to it, for the language menu's "Add"
     * rows. One already on the list (equal but for the id) is switched to instead of added a second time.
     *
     * [switchToSubtypeById] cannot follow [addSubtype] here: the new list reaches [subtypes] only once
     * the preference store hands it back, so the id would not be found yet. Instead the active id is
     * stored before the list, so that the list collector's [evaluateActiveSubtype] keeps the new one.
     */
    fun addSubtypeAndSwitchTo(subtype: Subtype) = scope.launch {
        val existing = subtypes.find { it.equalsExcludingId(subtype) }
        if (existing != null) {
            switchToSubtypeById(existing.id)
            return@launch
        }
        val subtypeToAdd = subtype.copy(id = System.currentTimeMillis())
        val previous = activeSubtype
        prefs.localization.activeSubtypeId.set(subtypeToAdd.id)
        prefs.localization.subtypes.set(SubtypeJsonConfig.encodeToString(subtypes + subtypeToAdd))
        activeSubtype = subtypeToAdd
        ensureLanguageData(subtypeToAdd)
        DictateController.followKeyboardLanguage(subtypeToAdd.primaryLocale.base, previous.primaryLocale.base)
    }

    /**
     * Starts fetching the language data a newly added [subtype] needs, both for a subtype the user adds
     * and for one seeded from the system languages.
     */
    private fun ensureLanguageData(subtype: Subtype) {
        // Start fetching the glide-typing dictionary for the new language right away (issue #127) instead
        // of waiting until the keyboard is first switched to it.
        GlideDictionaryManager.ensureDownloaded(appContext, subtype.primaryLocale.language)
        // Same for the Pinyin reading table (issue #262), without which the Chinese Pinyin subtype would
        // be a QWERTY layout that produces no characters at all.
        PinyinPackManager.ensureDownloaded(appContext, subtype.primaryLocale)
    }

    /**
     * Gets the currency set from the given subtype and returns it. Falls back to a default one if the subtype does not
     * exist.
     *
     * @return The currency set or a fallback.
     */
    fun getCurrencySet(subtypeToSearch: Subtype): CurrencySet {
        return keyboardManager.resources.currencySets.value[subtypeToSearch.currencySet] ?: CurrencySet.Fallback
    }

    /**
     * Gets a subtype by the given [id].
     *
     * @param id The id of the subtype you want to get.
     * @return The subtype or null, if no matching subtype could be found.
     */
    fun getSubtypeById(id: Long): Subtype? {
        val subtypeList = subtypes
        return subtypeList.find { it.id == id }
    }

    /**
     * Gets the default system subtype for a given [locale].
     *
     * @param locale The locale of the default system subtype to get.
     * @return The default system locale or null, if no matching default system subtype could be
     *  found.
     */
    fun getSubtypePresetForLocale(locale: FlorisLocale): SubtypePreset? {
        return SubtypePresetMatcher.bestMatch(locale, keyboardManager.resources.subtypePresets.value)
    }

    /**
     * Modifies an existing subtype with the newly provided details. In order to determine which
     * subtype should be updated, the id must be the same.
     *
     * @param subtypeToModify The subtype with the new details but same id.
     */
    fun modifySubtypeWithSameId(subtypeToModify: Subtype) {
        val subtypeList = subtypes
        val index = subtypeList.indexOfFirst { subtypeToModify.id == it.id }
        if (index >= 0 && index < subtypeList.size) {
            val newSubtypeList = subtypeList.mapIndexed { n, subtype ->
                if (n == index) {
                    subtypeToModify
                } else {
                    subtype
                }
            }
            persistNewSubtypeList(newSubtypeList)
        }
    }

    /**
     * Removes a given [subtypeToRemove]. Nothing happens if the given [subtypeToRemove] does not
     * exist.
     *
     * @param subtypeToRemove The subtype which should be removed.
     */
    fun removeSubtype(subtypeToRemove: Subtype) {
        val subtypeList = subtypes
        val indexToRemove = subtypeList.indexOf(subtypeToRemove)
        if (indexToRemove in subtypeList.indices) {
            val newSubtypeList = subtypeList.mapIndexedNotNull { n, subtype ->
                if (n != indexToRemove) {
                    subtype
                } else {
                    null
                }
            }
            persistNewSubtypeList(newSubtypeList)
            evaluateActiveSubtype(newSubtypeList)
            // Free the downloaded glide dictionary once no remaining subtype uses that language (issue #127).
            val lang = subtypeToRemove.primaryLocale.language
            if (newSubtypeList.none { it.primaryLocale.language == lang }) {
                GlideDictionaryManager.deleteDownloaded(appContext, lang)
            }
        }
    }

    /**
     * Switch to the previous subtype in the subtype list if possible.
     */
    fun switchToPrevSubtype() = scope.launch {
        val subtypeList = subtypes
        val cachedActiveSubtype = activeSubtype
        var triggerNextSubtype = false
        var newActiveSubtype: Subtype = Subtype.DEFAULT
        for (subtype in subtypeList.asReversed()) {
            if (triggerNextSubtype) {
                triggerNextSubtype = false
                newActiveSubtype = subtype
            } else if (subtype == cachedActiveSubtype) {
                triggerNextSubtype = true
            }
        }
        if (triggerNextSubtype) {
            newActiveSubtype = subtypeList.last()
        }
        prefs.localization.activeSubtypeId.set(newActiveSubtype.id)
        activeSubtype = newActiveSubtype
        DictateController.followKeyboardLanguage(newActiveSubtype.primaryLocale.base, cachedActiveSubtype.primaryLocale.base)
    }

    /**
     * Switch to the next subtype in the subtype list if possible.
     */
    fun switchToNextSubtype() = scope.launch {
        val subtypeList = subtypes
        val cachedActiveSubtype = activeSubtype
        var triggerNextSubtype = false
        var newActiveSubtype: Subtype = Subtype.DEFAULT
        for (subtype in subtypeList) {
            if (triggerNextSubtype) {
                triggerNextSubtype = false
                newActiveSubtype = subtype
            } else if (subtype == cachedActiveSubtype) {
                triggerNextSubtype = true
            }
        }
        if (triggerNextSubtype) {
            newActiveSubtype = subtypeList.first()
        }
        prefs.localization.activeSubtypeId.set(newActiveSubtype.id)
        activeSubtype = newActiveSubtype
        DictateController.followKeyboardLanguage(newActiveSubtype.primaryLocale.base, cachedActiveSubtype.primaryLocale.base)
    }

    fun switchToSubtypeById(id: Long) = scope.launch {
        if (subtypes.any { it.id == id }) {
            val previous = activeSubtype
            activeSubtype = getSubtypeById(id)!!
            prefs.localization.activeSubtypeId.set(id)
            DictateController.followKeyboardLanguage(activeSubtype.primaryLocale.base, previous.primaryLocale.base)
        }
    }
}

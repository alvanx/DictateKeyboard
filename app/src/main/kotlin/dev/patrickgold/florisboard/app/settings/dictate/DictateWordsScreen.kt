/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictate

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.dictionary.UserDictionaryType
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import org.florisboard.lib.compose.stringRes

/**
 * One place for every word list, reached from a home tile. "Dictionary" meant three things spread over
 * two branches of the settings: the speech model's custom words and the find-and-replace mappings
 * (Dictate → Formatting & vocabulary), and the keyboard's own dictionary (Typing → User dictionaries).
 * The original screens stay where they are; this only gathers them.
 */
@Composable
fun DictateWordsScreen() = FlorisScreen {
    title = stringRes(R.string.words__title)
    previewFieldVisible = false
    iconSpaceReserved = true

    content {
        val navController = LocalNavController.current

        PreferenceGroup(title = stringRes(R.string.words__dictation_group)) {
            CustomWordsSection(prefs.dictate.customWords)
            Preference(
                icon = Icons.Default.SwapHoriz,
                modifier = Modifier.settingsSearchAnchor("dictate__mappings_title"),
                title = stringRes(R.string.dictate__mappings_title),
                summary = stringRes(R.string.dictate__mappings_entry_summary),
                onClick = { navController.navigate(Routes.Settings.DictateMappings) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.words__typing_group)) {
            Preference(
                icon = Icons.Default.Spellcheck,
                modifier = Modifier.settingsSearchAnchor("words__personal_dictionary"),
                title = stringRes(R.string.words__personal_dictionary),
                summary = stringRes(R.string.words__personal_dictionary_summary),
                onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.FLORIS)) },
                enabledIf = { prefs.dictionary.enableFlorisUserDictionary isEqualTo true },
            )
            Preference(
                icon = Icons.Default.Lightbulb,
                modifier = Modifier.settingsSearchAnchor("settings__learned__title"),
                title = stringRes(R.string.settings__learned__title),
                summary = stringRes(R.string.pref__dictionary__manage_learned_words__summary),
                onClick = { navController.navigate(Routes.Settings.LearnedWords) },
            )
            Preference(
                icon = Icons.Default.Settings,
                modifier = Modifier.settingsSearchAnchor("settings__dictionary__title"),
                title = stringRes(R.string.settings__dictionary__title),
                summary = stringRes(R.string.words__dictionary_settings_summary),
                onClick = { navController.navigate(Routes.Settings.Dictionary) },
            )
        }
    }
}

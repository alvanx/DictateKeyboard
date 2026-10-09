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

package dev.patrickgold.florisboard.app.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.dictate.providerDisplayName
import dev.patrickgold.florisboard.dictate.importer.ImportTranscriber
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.stringRes

/**
 * The Settings tab. Dictation comes first, since that is what the app is for: the AI model, the floating
 * mic button, the keyboard (with a way to turn it on, so it is never only hidden in system settings) and
 * the way into every other dictation setting. Below them sit the keyboard's and the app's own categories.
 */
@Composable
fun AllSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__all__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current

    actions {
        FlorisIconButton(
            onClick = { navController.navigate(Routes.Settings.Search) },
            icon = Icons.Default.Search,
        )
    }

    content {
        val context = LocalContext.current
        val providerId by prefs.dictate.transcriptionProviderId.collectAsState()
        val accounts by prefs.dictate.providerAccounts.collectAsState()
        // The model in use and who runs it, or just the provider when it has no model of its own to show.
        val modelSummary = remember(providerId, accounts) {
            val account = accounts.getOrEmpty(providerId)
            val provider = providerDisplayName(providerId, accounts)
            val model = account.transcriptionModel
                .ifBlank { ImportTranscriber.presetFor(account).defaultTranscriptionModel ?: "" }
            if (model.isBlank() || model == provider) provider else "$model · $provider"
        }
        val buttonOn = rememberMicButtonOn()
        val keyboardOn = rememberKeyboardOn()

        PreferenceGroup(title = stringRes(R.string.settings__dictation_group)) {
            Preference(
                icon = Icons.Default.Cloud,
                title = stringRes(R.string.home__ai_model),
                summary = modelSummary,
                onClick = { navController.navigate(Routes.Settings.DictateProviders) },
            )
            Preference(
                icon = Icons.Default.Mic,
                title = stringRes(R.string.dictate__floating_button_title),
                summary = stringRes(
                    if (buttonOn) R.string.settings__floating_button_on else R.string.home__mic_off,
                ),
                onClick = { navController.navigate(Routes.Settings.DictateFloatingButton) },
            )
            Preference(
                icon = Icons.Outlined.Keyboard,
                title = stringRes(R.string.home__keyboard_title),
                summary = stringRes(if (keyboardOn) R.string.home__keyboard_on else R.string.home__keyboard_off),
                onClick = {
                    if (keyboardOn) navController.navigate(Routes.Settings.Keyboard)
                    else InputMethodUtils.showImeEnablerActivity(context)
                },
            )
            Preference(
                icon = Icons.Default.Tune,
                title = stringRes(R.string.settings__more_dictation),
                summary = stringRes(R.string.settings__more_dictation_summary),
                onClick = { navController.navigate(Routes.Settings.Dictate) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__more_group)) {
            Preference(
                icon = Icons.Default.Language,
                title = stringRes(R.string.settings__localization__title),
                onClick = { navController.navigate(Routes.Settings.Localization) },
            )
            Preference(
                icon = Icons.Outlined.Palette,
                title = stringRes(R.string.settings__theme__title),
                onClick = { navController.navigate(Routes.Settings.Theme) },
            )
            Preference(
                icon = Icons.Outlined.Keyboard,
                title = stringRes(R.string.settings__keyboard__title),
                onClick = { navController.navigate(Routes.Settings.Keyboard) },
            )
            Preference(
                icon = Icons.Default.SmartButton,
                title = stringRes(R.string.settings__smartbar__title),
                onClick = { navController.navigate(Routes.Settings.Smartbar) },
            )
            Preference(
                icon = Icons.Default.Spellcheck,
                title = stringRes(R.string.settings__typing__title),
                onClick = { navController.navigate(Routes.Settings.Typing) },
            )
            Preference(
                icon = Icons.Default.Gesture,
                title = stringRes(R.string.settings__gestures__title),
                onClick = { navController.navigate(Routes.Settings.Gestures) },
            )
            Preference(
                icon = Icons.AutoMirrored.Outlined.Assignment,
                title = stringRes(R.string.settings__clipboard__title),
                onClick = { navController.navigate(Routes.Settings.Clipboard) },
            )
            Preference(
                icon = Icons.Default.SentimentSatisfiedAlt,
                title = stringRes(R.string.settings__media__title),
                onClick = { navController.navigate(Routes.Settings.Media) },
            )
            // With the other tools the Smartbar opens (issue #424): it is one, and not a dictation feature.
            Preference(
                icon = Icons.Outlined.Translate,
                title = stringRes(R.string.settings__translation__title),
                onClick = { navController.navigate(Routes.Settings.Translation) },
            )
            Preference(
                icon = Icons.Default.Extension,
                title = stringRes(R.string.ext__home__title),
                onClick = { navController.navigate(Routes.Ext.Home) },
            )
            Preference(
                icon = Icons.Outlined.Build,
                title = stringRes(R.string.settings__other__title),
                onClick = { navController.navigate(Routes.Settings.Other) },
            )
            Preference(
                icon = Icons.Outlined.Info,
                title = stringRes(R.string.about__title),
                onClick = { navController.navigate(Routes.Settings.About) },
            )
        }
    }
}

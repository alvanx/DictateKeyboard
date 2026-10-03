/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggSpacer
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * The keyboard's language menu, opened by holding the globe key or the space bar: the sheet Gboard users
 * know from the same gestures.
 *
 * It used to be a bare list of the keyboard languages, reachable only from a gesture nobody had set, while
 * the globe key opened the system's list of keyboard apps. That left no way back to English from inside the
 * keyboard once German had been added — adding a language drops the English fallback, and with a single
 * language there is no globe key to cycle with. So the menu now also offers the phone's own languages that
 * have no keyboard yet ([SubtypePresetMatcher.toAddFor]), one tap each, and ends with the way into the
 * language settings and the system's keyboard-app picker the globe key used to open.
 */
@Composable
fun SelectSubtypePanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    val inputFeedbackController = LocalInputFeedbackController.current

    val listState = rememberLazyListState()
    val subtypes by subtypeManager.subtypesFlow.collectAsState()
    val activeSubtype by subtypeManager.activeSubtypeFlow.collectAsState()
    val presets by keyboardManager.resources.subtypePresets.collectAsState()
    val layouts by keyboardManager.resources.layouts.collectAsState()
    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()

    // Without keyboard languages the keyboard types on the English fallback, which is listed rather than
    // leaving the menu without the language that is being typed in.
    val languages = subtypes.ifEmpty { listOf(Subtype.DEFAULT) }
    val presetsToAdd = remember(languages, presets) {
        SubtypePresetMatcher.toAddFor(subtypeManager.systemLocales(), languages.map { it.primaryLocale }, presets)
    }

    // Named the way the space bar and the Languages & Layouts screen name them.
    fun languageName(locale: FlorisLocale): String = when (displayLanguageNamesIn) {
        DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName()
        DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.displayName(locale)
    }

    fun layoutName(layoutMap: SubtypeLayoutMap): String? {
        return layouts[LayoutType.CHARACTERS]?.get(layoutMap.characters)?.label
    }

    fun close() {
        keyboardManager.activeState.isSubtypeSelectionVisible = false
    }

    SnyggColumn(FlorisImeUi.SubtypePanel.elementName, modifier = modifier.safeDrawingPadding()) {
        SnyggRow(
            elementName = FlorisImeUi.SubtypePanelHeader.elementName,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggText(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(false) {},
                text = stringRes(R.string.select_subtype_panel__header),
            )
        }

        SnyggBox(FlorisImeUi.SubtypePanelList.elementName) {
            LazyColumn(
                state = listState,
            ) {
                items(
                    languages,
                    key = {
                        it.id
                    }
                ) {
                    LanguageMenuItem(
                        icon = Icons.Default.Check,
                        isIconVisible = it.id == activeSubtype.id,
                        text = languageName(it.primaryLocale),
                        secondaryText = layoutName(it.layoutMap),
                        onClick = {
                            inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                            subtypeManager.switchToSubtypeById(it.id)
                            close()
                        },
                    )
                }
                if (presetsToAdd.isNotEmpty()) {
                    item(key = "from-phone") {
                        // The Smartbar actions editor's subheader: the one section label the keyboard's
                        // sheets already have, in the theme's accent.
                        SnyggText(
                            elementName = FlorisImeUi.SmartbarActionsEditorSubheader.elementName,
                            modifier = Modifier.fillMaxWidth(),
                            text = stringRes(R.string.select_subtype_panel__from_your_phone),
                        )
                    }
                    items(
                        presetsToAdd,
                        key = {
                            "add-${it.locale.languageTag()}"
                        }
                    ) {
                        LanguageMenuItem(
                            icon = Icons.Default.Add,
                            text = stringRes(
                                R.string.select_subtype_panel__add_language,
                                "language" to languageName(it.locale),
                            ),
                            secondaryText = layoutName(it.preferred),
                            onClick = {
                                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                                subtypeManager.addSubtypeAndSwitchTo(it.toSubtype())
                                close()
                            },
                        )
                    }
                }
                item(key = "divider") {
                    SnyggSpacer(
                        elementName = FlorisImeUi.SmartbarCandidateSpacer.elementName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .height(1.dp),
                    )
                }
                item(key = "settings") {
                    LanguageMenuItem(
                        icon = Icons.Default.Settings,
                        text = stringRes(R.string.select_subtype_panel__language_settings),
                        onClick = {
                            inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                            close()
                            FlorisImeService.launchSettings("settings/localization")
                        },
                    )
                }
                item(key = "switch-keyboard-app") {
                    // What holding the globe key did before it opened this menu.
                    LanguageMenuItem(
                        icon = Icons.Default.KeyboardAlt,
                        text = stringRes(R.string.select_subtype_panel__switch_keyboard_app),
                        onClick = {
                            inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                            close()
                            InputMethodUtils.showImePicker(context)
                        },
                    )
                }
            }
        }
    }
}

/**
 * A row of the language menu: the styling of a [SnyggListItem][org.florisboard.lib.snygg.ui.SnyggListItem],
 * which takes a single line, plus an optional second, smaller one for the layout.
 *
 * The icon's place is kept when [isIconVisible] is false, so that the check mark moving between languages
 * does not shift their names sideways.
 */
@Composable
private fun LanguageMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isIconVisible: Boolean = true,
    secondaryText: String? = null,
) {
    SnyggRow(
        elementName = FlorisImeUi.SubtypePanelListItem.elementName,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggBox(elementName = FlorisImeUi.SubtypePanelListItemIconLeading.elementName) {
            SnyggIcon(
                modifier = Modifier.alpha(if (isIconVisible) 1f else 0f),
                imageVector = icon,
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            SnyggText(
                elementName = FlorisImeUi.SubtypePanelListItemText.elementName,
                modifier = Modifier.fillMaxWidth(),
                text = text,
            )
            if (secondaryText != null) {
                SnyggText(
                    elementName = FlorisImeUi.SubtypePanelListItemText.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.7f),
                    fontSizeMultiplier = 0.8f,
                    text = secondaryText,
                )
            }
        }
    }
}

/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.advanced

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * The app's style, picked with one tap and applied on that tap: System, Light or Dark, plus whether dark
 * goes pure black. It replaces a list dialog that only applied the choice after a second tap on "OK", so
 * picking a style looked as if nothing happened. The keyboard follows the same choice (see ThemeManager).
 */
@Composable
internal fun AppThemePicker() {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val theme by prefs.other.settingsTheme.collectAsState()
    val black = theme == AppTheme.AUTO_AMOLED || theme == AppTheme.AMOLED_DARK
    val base = when (theme) {
        AppTheme.AUTO, AppTheme.AUTO_AMOLED -> AppTheme.AUTO
        AppTheme.LIGHT -> AppTheme.LIGHT
        AppTheme.DARK, AppTheme.AMOLED_DARK -> AppTheme.DARK
    }
    fun pick(base: AppTheme, black: Boolean) {
        val next = when (base) {
            AppTheme.LIGHT -> AppTheme.LIGHT
            AppTheme.DARK, AppTheme.AMOLED_DARK -> if (black) AppTheme.AMOLED_DARK else AppTheme.DARK
            AppTheme.AUTO, AppTheme.AUTO_AMOLED -> if (black) AppTheme.AUTO_AMOLED else AppTheme.AUTO
        }
        scope.launch { prefs.other.settingsTheme.set(next) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsSearchAnchor("pref__other__settings_theme__label")
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp).size(24.dp),
            )
            Text(stringRes(R.string.pref__other__settings_theme__label), style = MaterialTheme.typography.titleMedium)
        }
        val options = listOf(
            AppTheme.AUTO to stringRes(R.string.settings__system_default),
            AppTheme.LIGHT to stringRes(R.string.pref__other__settings_theme__light),
            AppTheme.DARK to stringRes(R.string.pref__other__settings_theme__dark),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = base == option,
                    onClick = { pick(option, black) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
        // Pure black only means something once the app can be dark.
        if (base != AppTheme.LIGHT) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringRes(R.string.pref__other__settings_theme__pure_black),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = black, onCheckedChange = { pick(base, it) })
            }
        }
    }
}

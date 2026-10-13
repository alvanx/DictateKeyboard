/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.referral

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import org.florisboard.lib.compose.florisDialogScroll
import org.florisboard.lib.compose.stringRes

/** Settings row "Referral code": shows the stored code, or lets the user type one in. */
@Composable
fun PreferenceUiScope<FlorisPreferenceModel>.ReferralCodePreference() {
    val store = Referral.get(LocalContext.current)
    val code by store.code.collectAsState(initial = store.currentCode)
    var dialogOpen by remember { mutableStateOf(false) }

    Preference(
        icon = Icons.Default.CardGiftcard,
        title = stringRes(R.string.dictate__referral_title),
        summary = code ?: stringRes(R.string.dictate__referral_summary_none),
        onClick = { dialogOpen = true },
    )

    if (dialogOpen) {
        var text by remember { mutableStateOf(code.orEmpty()) }
        var invalid by remember { mutableStateOf(false) }
        JetPrefAlertDialog(
            scrollModifier = florisDialogScroll(),
            title = stringRes(R.string.dictate__referral_title),
            confirmLabel = stringRes(R.string.dictate__referral_save),
            dismissLabel = stringRes(R.string.dictate__referral_cancel),
            onConfirm = {
                if (store.setManually(text)) dialogOpen = false else invalid = true
            },
            onDismiss = { dialogOpen = false },
        ) {
            OutlinedTextField(
                modifier = Modifier.padding(top = 4.dp),
                value = text,
                onValueChange = { text = it; invalid = false },
                singleLine = true,
                isError = invalid,
                placeholder = { Text(stringRes(R.string.dictate__referral_hint)) },
                supportingText = if (invalid) {
                    { Text(stringRes(R.string.dictate__referral_invalid)) }
                } else {
                    null
                },
            )
            if (code != null) {
                TextButton(onClick = { store.clear(); dialogOpen = false }) {
                    Text(stringRes(R.string.dictate__referral_remove))
                }
            }
        }
    }
}

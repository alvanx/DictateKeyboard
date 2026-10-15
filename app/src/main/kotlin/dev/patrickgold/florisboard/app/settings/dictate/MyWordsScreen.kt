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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.data.prompts.MyWords
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * The whole of My words, one row per word with a way to remove it, plus the text editor with import and
 * export for big changes. Reached from the chips on the Dictionary tab.
 *
 * A lazy list rather than a settings column: after the one-time merge a personal dictionary full of
 * contact names lands here, and a list can reach a couple of thousand words.
 */
@Composable
fun MyWordsScreen() = FlorisScreen {
    title = stringRes(R.string.mywords__title)
    previewFieldVisible = false
    scrollable = false

    content {
        val scope = rememberCoroutineScope()
        val customWords = prefs.dictate.customWords
        val raw by customWords.collectAsState()
        val words = remember(raw) { MyWords.parse(raw) }
        val usage = remember(words) { MyWords.usage(words) }

        LazyColumn {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringRes(R.string.mywords__explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MyWordsAddField()
                    MyWordsUsageBar(usage)
                }
            }
            item {
                // The text editor: pasting a list, importing and exporting a file.
                CustomWordsSection(customWords)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            itemsIndexed(words, key = { _, word -> word.lowercase() }) { index, word ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            word,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // The speech AI reads the list from the front and stops when it is full.
                        if (index >= usage.sent) {
                            Text(
                                stringRes(R.string.mywords__typing_only),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                customWords.set(MyWords.remove(customWords.get(), word))
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringRes(R.string.mywords__remove, "word" to word),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The "Add a word or name" field and its + button. New words go to the front of the list, so a word just
 * added is one the speech AI gets. Commas add several at once.
 */
@Composable
internal fun MyWordsAddField(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    val canAdd = MyWords.entriesOf(input).isNotEmpty()

    fun add() {
        if (!canAdd) return
        val typed = input
        input = ""
        scope.launch { prefs.dictate.customWords.set(MyWords.add(prefs.dictate.customWords.get(), typed)) }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringRes(R.string.mywords__add_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
        FilledIconButton(
            onClick = { add() },
            enabled = canAdd,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringRes(R.string.mywords__add))
        }
    }
}

/** The bar and line saying how much of the list goes to the speech AI. */
@Composable
internal fun MyWordsUsageBar(usage: MyWords.Usage, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LinearProgressIndicator(
            progress = { usage.fraction },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            // Full is not an error — every word still works for typing — but it is worth seeing.
            color = if (usage.isFull) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            myWordsUsageText(usage),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.4f, fill = false),
        )
    }
}

/** "12 of 40 go to the speech AI", or, once the list is longer than that, how many of all of them do. */
@Composable
internal fun myWordsUsageText(usage: MyWords.Usage): String = if (usage.isFull) {
    stringRes(R.string.mywords__usage_full, "count" to usage.sent, "total" to usage.total)
} else {
    stringRes(R.string.mywords__usage, "count" to usage.sent, "limit" to usage.limit)
}

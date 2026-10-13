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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.florisboard.dictate.data.mappings.DictateMappings
import dev.patrickgold.florisboard.dictate.data.prompts.MyWords
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.stringRes

/**
 * The Dictionary tab: My words, the fixes, and the way to the keyboard's own dictionary settings.
 *
 * My words is the one list people keep: the dictation custom words and the keyboard's personal
 * dictionary used to be two lists here, and are one now (see [MyWords]). Its card shows the first few
 * words, a field to add one and how many the speech AI gets; tapping a word opens the full list
 * ([MyWordsScreen]) to remove any or edit the list as text. The personal dictionary itself, with its
 * text expansions, stays reachable through the phone dictionary settings at the bottom.
 */
@Composable
fun DictateWordsScreen() = FlorisScreen {
    title = stringRes(R.string.dictionary_hub__title)
    navigationIconVisible = false
    previewFieldVisible = false
    iconSpaceReserved = true

    content {
        val navController = LocalNavController.current
        val raw by prefs.dictate.customWords.collectAsState()
        val words = remember(raw) { MyWords.parse(raw) }
        val usage = remember(words) { MyWords.usage(words) }
        val mappings by prefs.dictate.customMappings.collectAsState()

        Text(
            stringRes(R.string.mywords__subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
        )

        SectionHeading(stringRes(R.string.mywords__title))
        MyWordsCard(
            words = words,
            usage = usage,
            onOpenList = { navController.navigate(Routes.Settings.MyWords) },
            modifier = Modifier.settingsSearchAnchor("mywords__title"),
        )

        SectionHeading(stringRes(R.string.mywords__fixes))
        Card(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                FixRow(
                    icon = Icons.Default.SwapHoriz,
                    title = stringRes(R.string.mywords__find_replace),
                    summary = findReplaceSummary(mappings),
                    onClick = { navController.navigate(Routes.Settings.DictateMappings) },
                    modifier = Modifier.settingsSearchAnchor("dictate__mappings_title"),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FixRow(
                    icon = Icons.Default.Lightbulb,
                    title = stringRes(R.string.mywords__learned),
                    summary = stringRes(R.string.mywords__learned_summary),
                    onClick = { navController.navigate(Routes.Settings.LearnedWords) },
                    modifier = Modifier.settingsSearchAnchor("settings__learned__title"),
                )
            }
        }
        TextButton(
            onClick = { navController.navigate(Routes.Settings.Dictionary) },
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp)
                .settingsSearchAnchor("settings__dictionary__title"),
        ) {
            Text(stringRes(R.string.mywords__phone_settings), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A heading between the tab's sections, the same as on the Style tab. */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** The My words card: what the list is for, its first words, the field to add one, and the usage bar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyWordsCard(
    words: List<String>,
    usage: MyWords.Usage,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringRes(if (words.isEmpty()) R.string.mywords__empty else R.string.mywords__explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (words.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val chipColors = listOf(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    )
                    words.take(MyWords.PREVIEW_COUNT).forEachIndexed { index, word ->
                        WordChip(word, chipColors[index % chipColors.size], onClick = onOpenList)
                    }
                    val more = words.size - MyWords.PREVIEW_COUNT
                    Text(
                        if (more > 0) stringRes(R.string.mywords__more, "count" to more)
                        else stringRes(R.string.mywords__see_all),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenList)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
            MyWordsAddField()
            MyWordsUsageBar(usage)
        }
    }
}

/** One word of the preview; every chip opens the full list, where words are removed. */
@Composable
private fun WordChip(word: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = color,
    ) {
        Text(
            word,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** A row of the Fixes card: icon, title, one line of summary, and a chevron. */
@Composable
private fun FixRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "4 rules, e.g. “gonna” → “going to”", from the first rule that has something to find. */
@Composable
private fun findReplaceSummary(mappings: DictateMappings): String {
    val rules = mappings.items.filter { it.from.isNotEmpty() }
    val first = rules.firstOrNull() ?: return stringRes(R.string.mywords__find_replace_none)
    val found = first.from
    val replacement = first.to
    return if (rules.size == 1) {
        stringRes(R.string.mywords__find_replace_one, "from" to found, "to" to replacement)
    } else {
        stringRes(R.string.mywords__find_replace_many, "count" to rules.size, "from" to found, "to" to replacement)
    }
}

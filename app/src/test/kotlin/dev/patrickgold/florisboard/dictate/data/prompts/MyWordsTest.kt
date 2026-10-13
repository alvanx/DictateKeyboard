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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules behind "My words", the one list for dictation and typing. Worth testing because a mistake
 * here is silent and lasting: a word dropped in the merge, or removed from somebody's personal dictionary
 * that they put there themselves, is gone without anything saying so.
 */
class MyWordsTest {

    // --- reading and editing the list ------------------------------------------------------------

    @Test
    fun `repeats are dropped ignoring case and the first spelling wins`() {
        assertEquals(listOf("iPhone", "Søren"), MyWords.parse("iPhone, Søren\niphone, SØREN"))
    }

    @Test
    fun `a new word goes to the front`() {
        assertEquals("Figma\nKubernetes\nJira", MyWords.add("Kubernetes\nJira", "Figma"))
    }

    @Test
    fun `a comma separated list stays comma separated`() {
        assertEquals("Figma, Kubernetes, Jira", MyWords.add("Kubernetes, Jira", "Figma"))
    }

    @Test
    fun `adding a known word in a new spelling moves it to the front with that spelling`() {
        assertEquals("iPhone\nJira", MyWords.add("Jira\niphone", " iPhone "))
    }

    @Test
    fun `several words can be added at once and blanks are ignored`() {
        assertEquals("Ngozi\nOKRs\nJira", MyWords.add("Jira", "Ngozi, ,  OKRs  "))
        assertEquals("Jira", MyWords.add("Jira", " , "))
    }

    @Test
    fun `the field collapses inner whitespace`() {
        assertEquals(listOf("Deutsche Bahn"), MyWords.entriesOf("  Deutsche   Bahn "))
    }

    @Test
    fun `removing ignores case`() {
        assertEquals("Kubernetes", MyWords.remove("Kubernetes\nFIGMA", "figma"))
        assertEquals("", MyWords.remove("Figma", "Figma"))
    }

    @Test
    fun `renaming keeps the place and merges with an existing spelling`() {
        assertEquals("A\niPhone\nB", MyWords.rename("A\niphone\nB", "IPHONE", "iPhone"))
        assertEquals("A\nB", MyWords.rename("A\nold\nB", "old", "A"))
    }

    @Test
    fun `appending keeps the user's own text and skips known words`() {
        // The first spelling of a repeat wins, and the existing text is not rewritten.
        assertEquals("Acme,  Jira, figma", MyWords.append("Acme,  Jira,", listOf("figma", "jira", "Figma")))
        assertEquals("One\nTwo", MyWords.append("", listOf("One", "Two")))
        assertEquals("Same", MyWords.append("Same", listOf("SAME")))
    }

    // --- the one-time merge ----------------------------------------------------------------------

    @Test
    fun `the merge loses nothing from either list`() {
        val merge = MyWords.merge(
            myWords = listOf("Kubernetes", "Lokalise"),
            dictionaryWords = listOf("lokalise", "Ngozi", "Søren", "ngozi"),
        )
        assertEquals(listOf("Ngozi", "Søren"), merge.toMyWords)
        assertEquals(listOf("Kubernetes"), merge.toDictionary)
    }

    @Test
    fun `merging empty lists does nothing`() {
        val merge = MyWords.merge(emptyList(), listOf(" ", ""))
        assertTrue(merge.toMyWords.isEmpty())
        assertTrue(merge.toDictionary.isEmpty())
    }

    // --- following the list into the personal dictionary -----------------------------------------

    @Test
    fun `only words that were copied before are ever removed`() {
        val plan = MyWords.mirror(previous = listOf("Figma", "Jira"), current = listOf("Jira", "Ngozi"))
        assertEquals(listOf("Figma"), plan.remove)
        assertEquals(listOf("Ngozi"), plan.add)
    }

    @Test
    fun `nothing changed means nothing to do`() {
        val plan = MyWords.mirror(listOf("Figma", "Jira"), listOf("Jira", "Figma"))
        assertTrue(plan.remove.isEmpty())
        assertTrue(plan.add.isEmpty())
    }

    @Test
    fun `a new capitalisation replaces the old one`() {
        val plan = MyWords.mirror(listOf("iphone"), listOf("iPhone"))
        assertEquals(listOf("iphone"), plan.remove)
        assertEquals(listOf("iPhone"), plan.add)
    }

    @Test
    fun `the first copy adds everything and removes nothing`() {
        val plan = MyWords.mirror(emptyList(), listOf("Figma", "Jira"))
        assertTrue(plan.remove.isEmpty())
        assertEquals(listOf("Figma", "Jira"), plan.add)
    }

    // --- how much reaches the speech AI ----------------------------------------------------------

    @Test
    fun `an empty list has room for about forty words`() {
        val usage = MyWords.usage(emptyList())
        assertEquals(0, usage.sent)
        assertEquals(40, usage.limit)
        assertFalse(usage.isFull)
        assertEquals(0f, usage.fraction)
    }

    @Test
    fun `a short list goes to the speech AI whole and estimates the room left`() {
        val words = listOf("Kubernetes", "Lokalise", "OKRs", "Figma", "Ngozi", "Jira", "Søren")
        val usage = MyWords.usage(words)
        assertEquals(7, usage.sent)
        assertEquals(7, usage.total)
        assertFalse(usage.isFull)
        assertTrue(usage.limit in 30..80, "limit was ${usage.limit}")
        assertTrue(usage.fraction > 0f && usage.fraction < 1f)
    }

    @Test
    fun `a long list says how many of all of them go`() {
        val words = (1..200).map { "Word$it" }
        val usage = MyWords.usage(words)
        assertTrue(usage.isFull)
        assertEquals(200, usage.total)
        assertEquals(CustomWordList.countWithinBudget(words), usage.sent)
        assertEquals(usage.sent, usage.limit)
        assertEquals(1f, usage.fraction)
    }

    @Test
    fun `the prompt carries only the words that fit`() {
        val words = (1..200).map { "Word$it" }
        val prompt = DictatePromptDefaults.appendCustomWords(null, words.joinToString("\n"))!!
        val sent = prompt.split(", ")
        assertEquals(words.take(CustomWordList.countWithinBudget(words)), sent)
        assertTrue(CustomWordList.estimateTokens(sent) <= CustomWordList.TOKEN_BUDGET)
    }
}

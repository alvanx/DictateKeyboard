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

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.glideTypingManager
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.LearnedWordsStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryWriter
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.florisboard.lib.kotlin.collectIn

/**
 * Keeps the keyboard's personal dictionary following My words ([MyWords]).
 *
 * My words (`dictate.customWords`) is the one list the user edits. Every word in it is also put into the
 * internal personal dictionary — the one the keyboard's own suggestions, autocorrect and glide typing
 * read — and taken out again when it leaves the list. Not the phone's system dictionary: that one belongs
 * to the device and every other keyboard on it.
 *
 * Words that reached the personal dictionary some other way (added there by hand, from contacts, or
 * learned from typing) are left alone, except that the one-time [mergeIfNeeded] brings the hand-added
 * ones into My words so the two old lists end up as one.
 */
object MyWordsSync {
    private val prefs by FlorisPreferenceStore
    private val mutex = Mutex()

    /**
     * One-time merge of the two old lists (guarded by `dictate.myWordsMerged`): every personal-dictionary
     * word joins My words, behind the words already there, and every My word goes into the dictionary.
     * Shortcut entries (text expansions) and words the keyboard learned on its own are not brought over:
     * neither was ever a word somebody chose to teach it.
     *
     * Not marked done while the internal dictionary is switched off or not set up yet; [mirror] tries
     * again, so it happens as soon as the dictionary is there.
     */
    suspend fun mergeIfNeeded(context: Context): Unit = mutex.withLock { mergeLocked(context) }

    /** The merge itself, with [mutex] held. True once it has been done, now or before. */
    private suspend fun mergeLocked(context: Context): Boolean {
        if (prefs.dictate.myWordsMerged.get()) return true
        val dao = withContext(Dispatchers.IO) { dao() } ?: return false
        val learned = runCatching { LearnedWordsStore.flow(context).first() }.getOrDefault(emptyList())
            .filter { it.promoted }
            .mapTo(HashSet()) { it.word.lowercase() }
        val dictionaryWords = withContext(Dispatchers.IO) {
            runCatching { dao.queryAll() }.getOrNull()
        }?.filter { it.shortcut.isNullOrBlank() && it.word.isNotBlank() && it.word.lowercase() !in learned }
            ?.map { it.word }
            ?: return false

        val raw = prefs.dictate.customWords.get()
        val merge = MyWords.merge(MyWords.parse(raw), dictionaryWords)
        val added = withContext(Dispatchers.IO) { PersonalDictionaryWriter.addWords(dao, merge.toDictionary) }
        val merged = MyWords.append(raw, merge.toMyWords)
        // What the dictionary now holds of My words, before the list itself changes — so the mirror that
        // the change sets off finds nothing left to do.
        prefs.dictate.myWordsMirrored.set(MyWords.parse(merged).joinToString("\n"))
        if (merged != raw) prefs.dictate.customWords.set(merged)
        prefs.dictate.myWordsMerged.set(true)
        if (added.isNotEmpty()) invalidateGlide(context)
        return true
    }

    /**
     * Follows My words into the personal dictionary whenever the list changes, wherever it was changed
     * from, and when the internal dictionary is switched back on. Call once after the preference store
     * has loaded.
     */
    fun startMirroring(context: Context, scope: CoroutineScope) {
        merge(
            prefs.dictate.customWords.asFlow().map {},
            prefs.dictionary.enableFlorisUserDictionary.asFlow().map {},
        ).debounce(500L).collectIn(scope) {
            runCatching { mirror(context) }
        }
    }

    /** Applies whatever My words changed since the last copy to the personal dictionary. */
    suspend fun mirror(context: Context): Unit = mutex.withLock {
        // The merge comes first, so nothing is copied over a dictionary whose own words were never read.
        if (!mergeLocked(context)) return@withLock
        val dao = withContext(Dispatchers.IO) { dao() } ?: return@withLock
        val current = MyWords.parse(prefs.dictate.customWords.get())
        val previous = MyWords.parse(prefs.dictate.myWordsMirrored.get())
        val plan = MyWords.mirror(previous, current)
        if (plan.remove.isEmpty() && plan.add.isEmpty()) return@withLock
        withContext(Dispatchers.IO) {
            plan.remove.forEach { deleteWord(dao, it) }
            PersonalDictionaryWriter.addWords(dao, plan.add)
        }
        prefs.dictate.myWordsMirrored.set(current.joinToString("\n"))
        invalidateGlide(context)
    }

    /**
     * The personal-dictionary screen deleted [old] (when [new] is null) or renamed it to [new]: My words
     * follows, so a word removed there does not linger here. A word that is not in My words is ignored.
     */
    suspend fun onDictionaryWordChanged(old: String, new: String?): Unit = mutex.withLock {
        val raw = prefs.dictate.customWords.get()
        val words = MyWords.parse(raw)
        if (words.none { it.equals(old, ignoreCase = true) }) return@withLock
        val changed = if (new.isNullOrBlank()) MyWords.remove(raw, old) else MyWords.rename(raw, old, new)
        // The dictionary already holds the result, so the copy is updated first and the mirror that the
        // list change sets off has nothing to do.
        val mirrored = prefs.dictate.myWordsMirrored.get()
        prefs.dictate.myWordsMirrored.set(
            if (new.isNullOrBlank()) MyWords.remove(mirrored, old) else MyWords.rename(mirrored, old, new),
        )
        prefs.dictate.customWords.set(changed)
    }

    /** Glide typing indexes the vocabulary once per subtype; without this a swipe keeps the old list. */
    private fun invalidateGlide(context: Context) {
        runCatching { context.glideTypingManager().value.invalidateWordData() }
    }

    /** The internal personal dictionary, or null while it is switched off or not set up yet. */
    private fun dao(): UserDictionaryDao? {
        val manager = runCatching { DictionaryManager.default() }.getOrNull() ?: return null
        manager.loadUserDictionariesIfNecessary()
        return manager.florisUserDictionaryDao()
    }

    /** Removes [word] from [dao] in every language, ignoring case. Text-expansion entries stay. */
    private fun deleteWord(dao: UserDictionaryDao, word: String) {
        runCatching {
            dao.query(word)
                .filter { it.word.equals(word, ignoreCase = true) && it.shortcut.isNullOrBlank() }
                .forEach { dao.delete(it) }
        }
    }
}

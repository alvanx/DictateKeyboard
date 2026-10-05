/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.offline

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryStore
import kotlinx.coroutines.launch

/**
 * What tapping the "recording transcribed" notification opens: it copies the text and goes away, so the
 * person can paste it wherever they were headed when the connection dropped.
 *
 * The text is read from the history by id rather than carried in the intent — a long dictation does not
 * belong in a notification's extras, and the history is where it was written anyway.
 */
class OfflineTranscriptCopyActivity : ComponentActivity() {

    private var text: String? = null
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        val id = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
        lifecycleScope.launch {
            text = DictateHistoryStore.getById(this@OfflineTranscriptCopyActivity, id)
                ?.text?.takeIf { it.isNotBlank() }
            if (text == null) finish() else copyIfFocused()
        }
    }

    // Android 10+ only lets the app that has focus write the clipboard, and a freshly started activity
    // does not have it yet — so this waits for the window to be the one in front.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) copyIfFocused()
    }

    private fun copyIfFocused() {
        val value = text ?: return
        if (done || !hasWindowFocus()) return
        done = true
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.dictate__title), value))
        Toast.makeText(this, R.string.dictate__offline_copied, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_HISTORY_ID = "history_id"
    }
}

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

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryStore
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.wear.PhoneTranscriber
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Recordings made with no connection, waiting to be transcribed once there is one.
 *
 * A recording that could not be sent is already in the history as a failed row with its audio kept (issue
 * #358). What this adds is the *intent*: the row's id goes on a short list, and a system job that only runs
 * when a network exists works through that list, writes each transcript into the same row and posts a
 * notification that copies it.
 *
 * The list is separate from the failed flag on purpose. Plenty of rows fail for reasons a connection will
 * not fix — a wrong key, a file too big — and retrying those by itself, forever, would be a bug. Only a
 * recording that failed *because there was no network* is put here.
 *
 * What the job does not do is type the text into the app the person is in now: by the time the connection
 * is back they have usually moved on, and inserting into whatever field happens to be focused would be a
 * worse surprise than a notification. The text goes to the history, and the notification copies it.
 */
object OfflineDictationQueue {

    private const val PREFS_NAME = "dictate_offline_queue"
    private const val KEY_IDS = "history_ids"
    private const val JOB_ID = 4208
    private const val CHANNEL_ID = "dictate_offline_queue"
    private const val NOTIF_ID_BASE = 5000
    private const val NOTIF_PREVIEW_CHARS = 2000

    private val lock = Any()

    /** History row ids still waiting for a connection, oldest first. */
    fun pendingIds(context: Context): List<Long> = synchronized(lock) {
        prefs(context).getStringSet(KEY_IDS, null).orEmpty().mapNotNull { it.toLongOrNull() }.sorted()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun add(context: Context, id: Long) = synchronized(lock) {
        val ids = prefs(context).getStringSet(KEY_IDS, null).orEmpty().toMutableSet()
        if (ids.add(id.toString())) prefs(context).edit().putStringSet(KEY_IDS, ids).apply()
    }

    fun remove(context: Context, id: Long) = synchronized(lock) {
        val ids = prefs(context).getStringSet(KEY_IDS, null).orEmpty().toMutableSet()
        if (ids.remove(id.toString())) prefs(context).edit().putStringSet(KEY_IDS, ids).apply()
    }

    /**
     * Queues history row [historyId] for transcription when the connection returns. Returns false — and
     * queues nothing — when the row is gone, is no longer waiting for a transcript, or has no audio kept,
     * because then there is nothing a later pass could do with it.
     */
    suspend fun enqueue(context: Context, historyId: Long): Boolean {
        val app = context.applicationContext
        val entry = DictateHistoryStore.getById(app, historyId) ?: return false
        val hasAudio = entry.audioPath?.let { File(it).let { f -> f.exists() && f.length() > 0L } } == true
        if (!entry.failed || !hasAudio) return false
        add(app, historyId)
        DictateHistoryStore.setPlaceholderText(app, historyId, app.getString(R.string.dictate__history_waiting))
        schedule(app)
        return true
    }

    /**
     * Asks the system to run [OfflineQueueJobService] as soon as there is a network. A no-op while a pass
     * is already pending or running: scheduling over a running job would stop it halfway through an upload,
     * and the running pass picks up anything queued meanwhile by itself.
     */
    fun schedule(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, OfflineQueueJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build(),
            )
        }
    }

    /**
     * Jobs do not survive a reboot unless the app asks for the boot permission, which it does not. So the
     * list is checked again whenever the app starts — the keyboard process starts on first use after a
     * reboot — and the job is put back if anything is still waiting.
     */
    fun rescheduleIfPending(context: Context) {
        if (pendingIds(context).isNotEmpty()) schedule(context)
    }

    /**
     * One pass over the queue, called by the job. Returns true when it stopped because the connection is
     * still not usable, so the job should be tried again later; false when the queue is empty or the rest
     * of it can never succeed.
     */
    suspend fun process(context: Context, prefs: FlorisPreferenceModel): Boolean {
        val app = context.applicationContext
        val skipped = mutableSetOf<Long>()
        // Re-read each round: a dictation queued while this pass runs is picked up by it.
        while (true) {
            val id = pendingIds(app).firstOrNull { it !in skipped } ?: return false
            val entry = DictateHistoryStore.getById(app, id)
            val audio = entry?.audioPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
            // Gone, already transcribed some other way (a manual resend clears the failed flag), or its audio
            // was pruned: nothing left to do for this one.
            if (entry == null || !entry.failed || audio == null) {
                remove(app, id)
                continue
            }
            try {
                val text = PhoneTranscriber.transcribe(app, prefs, audio, reword = false)
                if (text.isBlank()) {
                    // Nothing was said: same as a live dictation with no speech — no trace of it.
                    DictateHistoryStore.deleteById(app, id)
                } else {
                    // The audio was force-kept so there was something to recover; now that there is a
                    // transcript it follows the ordinary "keep audio" setting like any other dictation.
                    DictateHistoryStore.completePending(
                        app, id, text,
                        keepAudio = prefs.dictate.historyAudioRetention.get(),
                    )
                    notifyTranscribed(app, id, text)
                }
                remove(app, id)
            } catch (c: CancellationException) {
                throw c
            } catch (e: DictateApiException) {
                if (e.kind == DictateApiException.Kind.NETWORK || e.kind == DictateApiException.Kind.TIMEOUT) {
                    return true
                }
                giveUp(app, id)
            } catch (t: Throwable) {
                giveUp(app, id)
            }
            skipped.add(id)
        }
    }

    /** A failure a connection will not fix: back to an ordinary failed row, with its own re-transcribe. */
    private suspend fun giveUp(context: Context, id: Long) {
        remove(context, id)
        DictateHistoryStore.setPlaceholderText(context, id, context.getString(R.string.dictate__history_failed))
        notifyFailed(context, id)
    }

    private fun notifyTranscribed(context: Context, id: Long, text: String) {
        val copy = PendingIntent.getActivity(
            context, id.toInt(),
            Intent(context, OfflineTranscriptCopyActivity::class.java)
                .putExtra(OfflineTranscriptCopyActivity.EXTRA_HISTORY_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.dictate__offline_notif_title)
        val hint = context.getString(R.string.dictate__offline_notif_hint)
        post(
            context, id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text.take(NOTIF_PREVIEW_CHARS))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(NOTIF_PREVIEW_CHARS)).setSummaryText(hint))
                // On the lock screen a dictation is private: say that it arrived, not what it says.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(baseBuilder(context).setContentTitle(title).setContentText(hint).build())
                .setContentIntent(copy),
        )
    }

    private fun notifyFailed(context: Context, id: Long) {
        val openHistory = PendingIntent.getActivity(
            context, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse("ui://florisboard/settings/dictate/history"), context, FlorisAppActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(
            context, id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.dictate__offline_notif_failed_title))
                .setContentText(context.getString(R.string.dictate__offline_notif_failed_text))
                .setContentIntent(openHistory),
        )
    }

    private fun baseBuilder(context: Context) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_app_icon_monochrome)
        .setAutoCancel(true)

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled(), which covers the Android 13 grant
    private fun post(context: Context, id: Long, builder: NotificationCompat.Builder) {
        runCatching {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            ensureChannel(context)
            builder.setSmallIcon(R.drawable.ic_app_icon_monochrome).setAutoCancel(true)
            manager.notify(NOTIF_ID_BASE + (id % 100_000L).toInt(), builder.build())
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.dictate__offline_notif_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}

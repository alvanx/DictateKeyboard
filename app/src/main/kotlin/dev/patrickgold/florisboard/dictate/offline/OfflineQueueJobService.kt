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

import android.app.job.JobParameters
import android.app.job.JobService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The system runs this once the phone has a network (see [OfflineDictationQueue.schedule]) and it
 * transcribes the recordings that were saved while offline. Returning "reschedule" from a pass that hit
 * a still-dead connection makes the system try again later, with its own backoff.
 */
class OfflineQueueJobService : JobService() {

    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            var again = false
            try {
                // The job can be what wakes the process, and the preference store loads in the background:
                // reading a provider key before it has would transcribe with an empty one.
                applicationContext.appContext().value.preferenceStoreLoaded.first { it }
                again = OfflineDictationQueue.process(applicationContext, prefs)
            } finally {
                jobFinished(params, again)
            }
        }
        return true
    }

    // The system is taking the job back (constraint lost, or its time ran out). Reschedule, so a
    // recording that was not reached is not forgotten.
    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

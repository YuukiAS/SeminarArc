package com.yuukias.seminararc.media.clip

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WorkManagerClipWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ClipWorkScheduler {
    override fun enqueueClipGeneration(clipId: Long) {
        val request = OneTimeWorkRequestBuilder<ClipGenerationWorker>()
            .setInputData(workDataOf(ClipGenerationWorker.KEY_CLIP_ID to clipId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "seminararc-clip-$clipId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

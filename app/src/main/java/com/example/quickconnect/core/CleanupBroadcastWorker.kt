
package com.example.quickconnect.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.quickconnect.data.AppDatabase

class CleanupBroadcastWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("id", -1L)
        if (id > 0) {
            AppDatabase.getInstance(applicationContext)
                .broadcastMessageDAO()
                .deleteById(id)
        }
        return Result.success()
    }
}
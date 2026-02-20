package io.udayabharathi.familytracker.background

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.udayabharathi.familytracker.sheets.SheetsManager

class LocationWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            LocationTrackerWorker::class.java.name -> {
                LocationTrackerWorker(appContext, workerParameters)
            }
            else -> null
        }
    }
}

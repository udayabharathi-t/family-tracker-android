package io.udayabharathi.familytracker.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import io.udayabharathi.familytracker.logging.LogManager
import io.udayabharathi.familytracker.sheets.SheetsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LogManager.initialize(context)
            LogManager.log("BootReceiver: Received action: ${intent.action}. Starting background work.")

            val pendingResult = goAsync()
            val coroutineScope = CoroutineScope(Dispatchers.IO)

            coroutineScope.launch {
                try {
                    LogManager.log("BootReceiver: Coroutine started. Checking for signed-in account.")
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null) {
                        LogManager.log("BootReceiver: User is signed in as ${account.email}.")
                        val sheetsManager = SheetsManager(context, account)
                        val config = sheetsManager.readConfiguration()

                        if (config != null && config.trackedEmails[account.email] == true) {
                            LogManager.log("BootReceiver: Config loaded, user is tracked. Enqueuing periodic work.")
                            val workRequest = PeriodicWorkRequestBuilder<LocationTrackerWorker>(
                                config.intervalMinutes,
                                TimeUnit.MINUTES
                            ).build()

                            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                                "LocationTrackerWork",
                                ExistingPeriodicWorkPolicy.REPLACE,
                                workRequest
                            )
                            LogManager.log("BootReceiver: Periodic work enqueued successfully.")
                        } else {
                            LogManager.log("BootReceiver: Not enqueuing work. Config is null, or user is not in the tracked list.")
                        }
                    } else {
                        LogManager.log("BootReceiver: User not signed in. No work will be enqueued.")
                    }
                } catch (e: Exception) {
                    LogManager.log("BootReceiver: An exception occurred: ${e.message}")
                } finally {
                    LogManager.log("BootReceiver: Coroutine work finished. Finishing broadcast.")
                    pendingResult.finish()
                }
            }
        }
    }
}

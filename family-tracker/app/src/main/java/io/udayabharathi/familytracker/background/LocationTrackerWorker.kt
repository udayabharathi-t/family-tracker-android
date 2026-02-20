package io.udayabharathi.familytracker.background

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.udayabharathi.familytracker.logging.LogManager
import io.udayabharathi.familytracker.sheets.SheetsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class LocationTrackerWorker(private val appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val NOTIFICATION_ID = 1
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification())
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        LogManager.log("LocationTrackerWorker started")

        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPercentage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        if (batteryPercentage <= 20) {
            LogManager.log("Battery percentage is too low ($batteryPercentage%), skipping work")
            return@withContext Result.failure()
        }

        val account = GoogleSignIn.getLastSignedInAccount(appContext)
        if (account == null) {
            LogManager.log("User is not signed in, skipping work")
            return@withContext Result.failure()
        }

        val sheetsManager = SheetsManager(appContext, account)
        val config = sheetsManager.readConfiguration()

        if (config == null || config.trackedEmails[account.email] != true) {
            LogManager.log("Not tracking user ${account.email}, not in tracked emails or config is null")
            return@withContext Result.failure()
        }

        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            LogManager.log("Location permission not granted, skipping work")
            return@withContext Result.failure()
        }

        val location = suspendCancellableCoroutine<Location?> { continuation ->
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)
            val cancellationTokenSource = CancellationTokenSource()
            LogManager.log("Requesting current location")
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        LogManager.log("Location received: $location")
                        continuation.resume(location)
                    }
                }.addOnFailureListener {
                    if (continuation.isActive) {
                        LogManager.log("Failed to get location: ${it.message}")
                        continuation.resume(null)
                    }
                }
            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }

        return@withContext if (location != null) {
            sheetsManager.writeData(account.email!!, location.latitude, location.longitude, batteryPercentage)
            Result.success()
        } else {
            Result.failure()
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(appContext, "location_tracking_channel")
            .setContentTitle("Location Tracking Active")
            .setContentText("Your location is being tracked in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }
}

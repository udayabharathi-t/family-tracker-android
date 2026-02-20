package io.udayabharathi.familytracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Configuration
import io.udayabharathi.familytracker.background.LocationWorkerFactory
import io.udayabharathi.familytracker.logging.LogManager

class FamilyTrackerApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        LogManager.initialize(this)
        createNotificationChannel()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(LocationWorkerFactory())
            .build()

    private fun createNotificationChannel() {
        val name = "Location Tracking"
        val descriptionText = "Shows a notification when location is being tracked in the background"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel("location_tracking_channel", name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager:
                NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

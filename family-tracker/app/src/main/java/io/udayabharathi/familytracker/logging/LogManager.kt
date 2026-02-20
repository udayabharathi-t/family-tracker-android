package io.udayabharathi.familytracker.logging

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Log(val timestamp: String, val message: String)

object LogManager {
    private val _logs = MutableLiveData<List<Log>>(emptyList())
    val logs: LiveData<List<Log>> = _logs

    private val logList = mutableListOf<Log>()
    private lateinit var logFile: File

    // Use a format that includes the date for correct purging
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private const val TIMESTAMP_LENGTH = 19 // Length of "yyyy-MM-dd HH:mm:ss"

    fun initialize(context: Context) {
        logFile = File(context.filesDir, "logs.txt")
        logList.clear()
        if (logFile.exists()) {
            logFile.readLines().forEach { line ->
                if (line.length > TIMESTAMP_LENGTH) {
                    val timestamp = line.substring(0, TIMESTAMP_LENGTH)
                    val message = line.substring(TIMESTAMP_LENGTH + 1)
                    logList.add(Log(timestamp, message))
                }
            }
        }
        purgeOldLogs() // Purge after loading
        _logs.postValue(logList.toList())
    }

    fun log(message: String) {
        // Add new log first
        val timestamp = sdf.format(Date())
        val newLog = Log(timestamp, message)
        logList.add(newLog)

        // Now purge old logs from the list
        purgeOldLogs()

        // Update UI
        _logs.postValue(logList.toList())

        // Write the cleaned list back to the file
        logFile.writeText(logList.joinToString("\n") { "${it.timestamp} ${it.message}" })
    }

    private fun purgeOldLogs() {
        val oneHourAgo = System.currentTimeMillis() - 3600_000 // 1 hour in milliseconds

        logList.removeAll { log ->
            try {
                sdf.parse(log.timestamp)?.time?.let { it < oneHourAgo } ?: true
            } catch (e: Exception) {
                true // Remove if parsing fails
            }
        }
    }
}

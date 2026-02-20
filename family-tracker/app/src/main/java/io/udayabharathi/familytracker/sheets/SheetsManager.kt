package io.udayabharathi.familytracker.sheets

import android.content.Context
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.*
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import io.udayabharathi.familytracker.logging.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrackerConfig(val intervalMinutes: Long, val trackedEmails: Map<String, Boolean>)

class SheetsManager(context: Context, googleSignInAccount: GoogleSignInAccount) {

    private val sheetsService: Sheets
    private val spreadsheetId = "1tkXaQoO7R7YPCsXoNELVf7SsCvkfv2xZ2lN131T-yy4"

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(SheetsScopes.SPREADSHEETS)
        )
        credential.selectedAccount = googleSignInAccount.account
        sheetsService = Sheets.Builder(
            AndroidHttp.newCompatibleTransport(),
            JacksonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Family Tracker")
            .build()
    }

    suspend fun readConfiguration(): TrackerConfig? = withContext(Dispatchers.IO) {
        LogManager.log("Reading configuration from spreadsheet")
        try {
            val range = "Sheet1!A1:B"
            val response = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
            val values = response.getValues()

            if (values.isNullOrEmpty()) {
                LogManager.log("No configuration found in spreadsheet")
                return@withContext null
            }

            val intervalMinutes = values[0][0].toString().toLongOrNull() ?: 5L
            val trackedEmails = mutableMapOf<String, Boolean>()

            for (i in 1 until values.size) {
                val row = values[i]
                if (row.size >= 2) {
                    val email = row[0].toString()
                    val isEnabled = row[1].toString().toBoolean()
                    trackedEmails[email] = isEnabled
                }
            }
            LogManager.log("Configuration read successfully")
            TrackerConfig(intervalMinutes, trackedEmails)
        } catch (e: Exception) {
            e.printStackTrace()
            LogManager.log("Error reading configuration: ${e.message}")
            null
        }
    }

    suspend fun writeData(email: String, latitude: Double, longitude: Double, batteryPercentage: Int) = withContext(Dispatchers.IO) {
        LogManager.log("Writing data to spreadsheet for $email")
        try {
            ensureSheetExists(email)

            val sheetId = getSheetId(email)
            if (sheetId != null) {
                val sheetProperties = sheetsService.spreadsheets().get(spreadsheetId).setFields("sheets.properties").execute()
                val sheet = sheetProperties.sheets.find { it.properties.sheetId == sheetId }
                val rowCount = sheet?.properties?.gridProperties?.rowCount ?: 0

                if (rowCount >= 1000) {
                    val deleteRequest = Request()
                        .setDeleteDimension(DeleteDimensionRequest()
                            .setRange(DimensionRange()
                                .setSheetId(sheetId)
                                .setDimension("ROWS")
                                .setStartIndex(0)
                                .setEndIndex(1)
                            )
                        )

                    sheetsService.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(deleteRequest))).execute()
                    LogManager.log("Deleted first row from sheet $email")
                }
            }

            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            val values = listOf(
                listOf(timestamp, latitude, longitude, batteryPercentage)
            )
            val body = ValueRange().setValues(values)
            sheetsService.spreadsheets().values().append(spreadsheetId, "'$email'!A1", body)
                .setValueInputOption("USER_ENTERED")
                .execute()
            LogManager.log("Data written successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            LogManager.log("Error writing data: ${e.message}")
        }
    }

    private suspend fun ensureSheetExists(sheetTitle: String) = withContext(Dispatchers.IO) {
        val spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute()
        val sheetExists = spreadsheet.sheets.any { it.properties.title == sheetTitle }

        if (!sheetExists) {
            LogManager.log("Creating new sheet: $sheetTitle")
            val addSheetRequest = AddSheetRequest().setProperties(SheetProperties().setTitle(sheetTitle))
            val batchUpdateSpreadsheetRequest = BatchUpdateSpreadsheetRequest()
                .setRequests(listOf(Request().setAddSheet(addSheetRequest)))
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest).execute()
        }
    }

    private suspend fun getSheetId(sheetTitle: String): Int? = withContext(Dispatchers.IO) {
        val spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).setFields("sheets.properties.sheetId,sheets.properties.title").execute()
        val sheet = spreadsheet.sheets.find { it.properties.title == sheetTitle }
        sheet?.properties?.sheetId
    }
}
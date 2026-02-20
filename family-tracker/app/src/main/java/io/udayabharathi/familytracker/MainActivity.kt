package io.udayabharathi.familytracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import io.udayabharathi.familytracker.background.LocationTrackerWorker
import io.udayabharathi.familytracker.logging.LogManager
import io.udayabharathi.familytracker.sheets.SheetsManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var logTextView: TextView
    private lateinit var refreshButton: Button
    private lateinit var signInButton: Button

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                handleSignInResult(account)
            } catch (e: ApiException) {
                LogManager.log("Google sign-in failed: ${e.statusCode}")
                signInButton.visibility = View.VISIBLE
            }
        } else {
            LogManager.log("Google Sign-In was cancelled.")
            signInButton.visibility = View.VISIBLE
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.POST_NOTIFICATIONS] == true) {
            LogManager.log("Notification permission granted.")
        }
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            checkSignInStatus()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder(this)
                    .setTitle("Background Location Access")
                    .setMessage("This app needs background location access to track your location even when the app is closed. Please select 'Allow all the time' in the next screen.")
                    .setPositiveButton("OK") { _, _ ->
                        requestBackgroundLocationPermission()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                    }
                    .show()
            }
        } else {
            LogManager.log("Location permission not granted.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https.googleapis.com/auth/spreadsheets"))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        logTextView = findViewById(R.id.log_textview)
        logTextView.movementMethod = ScrollingMovementMethod()

        refreshButton = findViewById(R.id.refresh_button)
        refreshButton.setOnClickListener {
            LogManager.log("Log refresh button clicked")
            checkSignInStatus()
        }

        signInButton = findViewById(R.id.sign_in_button)
        signInButton.setOnClickListener {
            signIn()
        }

        LogManager.logs.observe(this) { logs ->
            logTextView.text = logs.joinToString("\n") { "${it.timestamp}: ${it.message}" }
        }

        LogManager.log("MainActivity created")

        requestPermissions()
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            LogManager.log("Requesting permissions: $permissionsToRequest")
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkSignInStatus()
        }
    }

    private fun checkSignInStatus() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            LogManager.log("Not signed in. Attempting to sign in.")
            signIn()
        } else {
            handleSignInResult(account)
        }
    }

    private fun requestBackgroundLocationPermission() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun signIn() {
        signInButton.visibility = View.GONE
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(account: GoogleSignInAccount) {
        signInButton.visibility = View.GONE
        LogManager.log("Sign-in successful for ${account.email}")
        startBackgroundWorker(account)
    }

    private fun startBackgroundWorker(account: GoogleSignInAccount) {
        LogManager.log("Starting background worker for ${account.email}")
        lifecycleScope.launch {
            val sheetsManager = SheetsManager(this@MainActivity, account)
            val config = sheetsManager.readConfiguration()

            if (config != null && config.trackedEmails[account.email] == true) {
                val workManager = WorkManager.getInstance(this@MainActivity)

                // Enqueue a one-time work request to run immediately
                val oneTimeWorkRequest = OneTimeWorkRequestBuilder<LocationTrackerWorker>().build()
                workManager.enqueue(oneTimeWorkRequest)
                LogManager.log("One-time work request enqueued for ${account.email}")

                val periodicWorkRequest = PeriodicWorkRequestBuilder<LocationTrackerWorker>(
                    config.intervalMinutes,
                    TimeUnit.MINUTES
                ).build()

                workManager.enqueueUniquePeriodicWork(
                    "LocationTrackerWork",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    periodicWorkRequest
                )
                LogManager.log(
                    "Periodic work request enqueued for ${account.email} with interval ${config.intervalMinutes} minutes"
                )
            } else {
                LogManager.log("Not starting worker for ${account.email}, not in tracked emails or config is null")
            }
        }
    }
}

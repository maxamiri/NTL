// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.wired

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Main activity for the WiredApp (BLE server) in the NTL protocol.
 *
 * This activity serves as the entry point for the wired device application.
 * It handles runtime permission requests for Bluetooth, location services, and advertising,
 * then starts the WiredService to perform BLE advertising and GATT server operations.
 *
 * The UI displays real-time GPS and time data being broadcast to battery-powered devices:
 * - Synchronized epoch time (Unix timestamp in seconds)
 * - GPS coordinates (latitude and longitude)
 *
 * ## Required Permissions:
 * - **Android 12+ (API 31+):**
 *   - `BLUETOOTH_ADVERTISE`
 *   - `BLUETOOTH_SCAN`
 *   - `BLUETOOTH_CONNECT`
 *   - `ACCESS_FINE_LOCATION`
 * - **Android 11 and below:**
 *   - `BLUETOOTH`
 *   - `BLUETOOTH_ADMIN`
 *   - `ACCESS_FINE_LOCATION`
 */
class MainActivity : ComponentActivity() {
    // Tag for logging
    private val tag = "Wire"

    /**
     * Activity result launcher for handling multiple permission requests.
     *
     * If all permissions are granted, starts the WiredService. If any permission
     * is denied, the activity finishes to prevent operation without required permissions.
     */
    private val permissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            // If all permissions are granted, start the service
            if (permissions.values.all { it }) {
                val intent = Intent(this, WiredService::class.java)
                startService(intent)
            } else {
                // Log if permissions are denied and Close the app
                Log.e(tag, "Permissions denied")
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Close the app if permissions are not granted
        checkPermissions()

        setContent {
            // Set the main content UI
            MainScreen()
        }
    }

    /**
     * Checks if all required permissions are granted, and requests them if needed.
     *
     * If permissions are already granted, the WiredService is started immediately.
     * Otherwise, the permission request dialog is displayed to the user.
     */
    @SuppressLint("UnsafeIntentLaunch")
    private fun checkPermissions() {
        val requiredPermissions = getRequiredPermissions()
        // Check if all required permissions are granted
        if (requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(
                    this,
                    permission,
                ) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            // Start the service if permissions are granted
            val intent = Intent(this, WiredService::class.java)
            startService(intent)
        } else {
            // Request permissions if they are not granted
            permissionsLauncher.launch(requiredPermissions)
        }
    }

    /**
     * Returns an array of required permissions based on the Android API level.
     *
     * Bluetooth permission requirements changed significantly in Android 12 (API 31).
     * This method ensures the correct permissions are requested for each Android version.
     *
     * @return Array of permission strings required for BLE advertising and GPS operations
     */
    private fun getRequiredPermissions(): Array<String> {
        val requiredPermissions =
            mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Bluetooth permissions for Android S and above
                    add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    // Older Bluetooth permissions
                    add(Manifest.permission.BLUETOOTH)
                    add(Manifest.permission.BLUETOOTH_ADMIN)
                }
            }
        return requiredPermissions.toTypedArray()
    }

    /**
     * Composable function that renders the main UI screen.
     *
     * Displays real-time GPS and time data being broadcast by the WiredService:
     * - Epoch time (Unix timestamp in seconds)
     * - Latitude (scaled by 10000)
     * - Longitude (scaled by 10000)
     *
     * The data is collected from StateFlows exposed by the WiredService companion object.
     */
    @Suppress("ktlint:standard:function-naming")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        // Collect StateFlow values from BatteryService as Compose States
        val latitude by WiredService.latitude.collectAsState()
        val longitude by WiredService.longitude.collectAsState()
        val epoch by WiredService.epoch.collectAsState()
        Scaffold(
            topBar = { TopAppBar(title = { Text("BLE Broadcast App") }) },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Placeholder for data display
                Text("Epoch: $epoch")
                Text("Latitude: $latitude")
                Text("Longitude: $longitude")
            }
        }
    }
}

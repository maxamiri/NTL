// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.battery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
 * Main activity for the BatteryApp (BLE client) in the NTL protocol.
 *
 * This activity serves as the entry point for the battery-powered device application.
 * It handles runtime permission requests for Bluetooth and location services, then
 * starts the BatteryService to perform BLE scanning and data offloading operations.
 *
 * The UI displays real-time data received from nearby wired devices:
 * - Device ID of the connected wired device
 * - Synchronized epoch time
 * - GPS coordinates (latitude and longitude)
 *
 * ## Required Permissions:
 * - **Android 12+ (API 31+):**
 *   - `BLUETOOTH_SCAN`
 *   - `BLUETOOTH_CONNECT`
 *   - `ACCESS_FINE_LOCATION`
 * - **Android 11 and below:**
 *   - `BLUETOOTH`
 *   - `BLUETOOTH_ADMIN`
 *   - `ACCESS_FINE_LOCATION`
 */
class MainActivity : ComponentActivity() {

    private val TAG = "Battery"

    /**
     * Activity result launcher for handling multiple permission requests.
     *
     * If all permissions are granted, starts the BatteryService. If any permission
     * is denied, the activity finishes to prevent operation without required permissions.
     */
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            val intent = Intent(this, BatteryService::class.java)
            startService(intent)
        } else {
            Log.e(TAG, "Permissions denied")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainScreen()
        }

        checkPermissions()
    }

    /**
     * Checks if all required permissions are granted, and requests them if needed.
     *
     * If permissions are already granted, the BatteryService is started immediately.
     * Otherwise, the permission request dialog is displayed to the user.
     */
    private fun checkPermissions() {
        val requiredPermissions = getRequiredPermissions()
        if (requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }) {

            val intent = Intent(this, BatteryService::class.java)
            startService(intent)
        } else {
            permissionsLauncher.launch(requiredPermissions)
        }
    }

    /**
     * Returns an array of required permissions based on the Android API level.
     *
     * Bluetooth permission requirements changed significantly in Android 12 (API 31).
     * This method ensures the correct permissions are requested for each Android version.
     *
     * @return Array of permission strings required for BLE operations
     */
    private fun getRequiredPermissions(): Array<String> {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
        return requiredPermissions.toTypedArray()
    }

    /**
     * Composable function that renders the main UI screen.
     *
     * Displays real-time data received from the BatteryService:
     * - Remote device ID
     * - Synchronized epoch time (Unix timestamp in seconds)
     * - Latitude and longitude (scaled by 10000)
     *
     * The data is collected from StateFlows exposed by the BatteryService companion object.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        // Collect StateFlow values from BatteryService as Compose States
        val deviceId by BatteryService.remoteDeviceId.collectAsState()
        val latitude by BatteryService.latitude.collectAsState()
        val longitude by BatteryService.longitude.collectAsState()
        val epoch by BatteryService.epoch.collectAsState()

        Scaffold(
            topBar = { TopAppBar(title = { Text("BLE Client App") }) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Display the collected values
                Text("Device ID: $deviceId")
                Text("Epoch Time: $epoch")
                Text("Latitude: $latitude")
                Text("Longitude: $longitude")
            }
        }
    }
}

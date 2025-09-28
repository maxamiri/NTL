// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.wired

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
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

class MainActivity : ComponentActivity() {

    // Tag for logging
    private val TAG = "Wire"

    // Launcher for handling permission results
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // If all permissions are granted, start the service
        if (permissions.values.all { it }) {
            val intent = Intent(this, WiredService::class.java)
            startService(intent)
        } else {
            // Log if permissions are denied and Close the app
            Log.e(TAG, "Permissions denied")
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

    // Returns an array of required permissions based on the Android version
    @SuppressLint("UnsafeIntentLaunch")
    private fun checkPermissions() {
        val requiredPermissions = getRequiredPermissions()
        // Check if all required permissions are granted
        if (requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }) {
            // Start the service if permissions are granted
            val intent = Intent(this, WiredService::class.java)
            startService(intent)
        } else {
            // Request permissions if they are not granted
            permissionsLauncher.launch(requiredPermissions)
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        // Collect StateFlow values from BatteryService as Compose States
        val latitude by WiredService.latitude.collectAsState()
        val longitude by WiredService.longitude.collectAsState()
        val epoch by WiredService.epoch.collectAsState()
        Scaffold(
            topBar = { TopAppBar(title = { Text("BLE Broadcast App") }) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Placeholder for data display
                Text("Epoch: $epoch")
                Text("Latitude: $latitude")
                Text("Longitude: $longitude")
            }
        }
    }
}

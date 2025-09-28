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

class MainActivity : ComponentActivity() {

    private val TAG = "Battery"

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

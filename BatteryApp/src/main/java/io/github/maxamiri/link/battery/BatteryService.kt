// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.battery

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.app.NotificationCompat
import io.github.maxamiri.link.common.DeviceList
import io.github.maxamiri.link.common.KeyUtil
import io.github.maxamiri.link.common.KeyUtil.convertStringToPrivateKey
import io.github.maxamiri.link.common.KeyUtil.convertStringToPublicKey
import io.github.maxamiri.link.common.KeyUtil.generateSecretKey
import io.github.maxamiri.link.common.toHexString
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.util.UUID
import javax.crypto.SecretKey
import kotlin.collections.forEach

class BatteryService : Service() {

    private val TAG = "BLEClient"
    val context: Context = this

    companion object {
        private var _remoteDeviceId = MutableStateFlow(0)
        val remoteDeviceId: StateFlow<Int> = _remoteDeviceId.asStateFlow()
        private var _latitude = MutableStateFlow(0)
        val latitude: StateFlow<Int> = _latitude.asStateFlow()
        private var _longitude = MutableStateFlow(0)
        val longitude: StateFlow<Int> = _longitude.asStateFlow()
        private var _epoch = MutableStateFlow(0)
        val epoch: StateFlow<Int> = _epoch.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    private fun startForegroundService() {
        val notificationChannelId = "LinkServiceChannel"
        val channel = NotificationChannel(
            notificationChannelId,
            "Link Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Link Service")
            .setContentText("Running Link Service")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your own icon
            .setContentIntent(getPendingIntent())
            .build()

        startForeground(1, notification)

        bleSetup()
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(scanRunnable)
        bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null

    }

    private var ownDeviceId: Int = 0
    private lateinit var privateKey: PrivateKey
    private lateinit var publicKey: PublicKey
    private lateinit var deviceList: DeviceList

    // Info for remote device
    private lateinit var gattServiceUUID: UUID
    private lateinit var readCharacteristicUUID: UUID
    private lateinit var writeCharacteristicUUID: UUID
    private lateinit var remoteKey: PublicKey
    private lateinit var secretKey: SecretKey

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val handler = Handler()
    private val scanRunnable = Runnable {
        scan()
        //handler.postDelayed(this, scanInterval)
    }

    fun bleSetup() {
        deviceList = KeyUtil.loadDeviceList(this, R.raw.device_info)
        ownDeviceId = deviceList.self.deviceId

        // Convert self keys for immediate access if needed
        privateKey = convertStringToPrivateKey(deviceList.self.privateKeyStr!!)
        publicKey = convertStringToPublicKey(deviceList.self.publicKeyStr)

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Start scanning
        scan()
    }

    @SuppressLint("MissingPermission")
    private fun scan() {
        // Stop any ongoing scan before starting a new one
        bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)

        val scanFilter = ScanFilter.Builder()
            .setManufacturerData(0xFFFF, byteArrayOf()) // Filter by manufacturer ID only
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        Log.d(TAG, "Starting BLE scan")
        bluetoothAdapter.bluetoothLeScanner.startScan(
            listOf(scanFilter),
            scanSettings,
            scanCallback
        )
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val manufacturerData = result.scanRecord?.getManufacturerSpecificData(0xFFFF)
            if (manufacturerData != null && manufacturerData.size >= 16) {
                val dataBuffer = ByteBuffer.wrap(manufacturerData).order(ByteOrder.LITTLE_ENDIAN)

                _remoteDeviceId.value = dataBuffer.getInt()
                val newEpochTime = dataBuffer.getInt()

                if (newEpochTime != epoch.value) {
                    _epoch.value = newEpochTime
                    _latitude.value = dataBuffer.getInt()
                    _longitude.value = dataBuffer.getInt()

                    Log.d(
                        TAG,
                        "New Time/Location: ${_epoch.value};${_latitude.value};${_longitude.value}"
                    )
                }
            }

            // Identify the nearby device
            val nearbyDevice = deviceList.knownDevices.find { it.deviceId == _remoteDeviceId.value }

            if (nearbyDevice != null) {
                println("Device found: ${_remoteDeviceId.value}")
                gattServiceUUID = UUID.fromString(nearbyDevice.gattServiceUUID)
                readCharacteristicUUID = UUID.fromString(nearbyDevice.readCharacteristicUUID)
                writeCharacteristicUUID = UUID.fromString(nearbyDevice.writeCharacteristicUUID)
                remoteKey = convertStringToPublicKey(nearbyDevice.publicKeyStr)
                secretKey = generateSecretKey(privateKey, remoteKey)
            } else {
                println("Device with ID ${_remoteDeviceId.value} not found.")
            }

            result.device?.let { device ->
                Log.d(TAG, "Found device: ${device.address}, connecting.")

                // Stop scanning to prevent duplicate connections
                bluetoothAdapter.bluetoothLeScanner.stopScan(this)

                // Clean up the previous GATT connection
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null  // Ensure previous GATT is fully cleared

                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server, discovering services.")
                gatt?.discoverServices()
                gatt?.requestMtu(512)
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server")
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null
                // Restart scanning if disconnected
                handler.postDelayed(scanRunnable, 8000L)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU changed successfully to $mtu bytes")
                // You can now use the negotiated MTU size (e.g., mtu - 3 for payload size)
            } else {
                Log.e(TAG, "Failed to change MTU with status: $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                val service = gatt?.getService(gattServiceUUID)
                val readCharacteristic = service?.getCharacteristic(readCharacteristicUUID)
                readCharacteristic?.let {
                    Log.d(TAG, "Reading characteristic.")
                    gatt.readCharacteristic(it)
                }
            } else {
                Log.e(TAG, "Failed to discover services with status: $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic?.uuid == readCharacteristicUUID) {
                val iv = characteristic.value
                if (iv != null) {

                    val dataBuffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    dataBuffer.putInt(_epoch.value)
                    dataBuffer.putInt(_latitude.value)
                    dataBuffer.putInt(_longitude.value)
                    val data = dataBuffer.array()

                    val checksum = MessageDigest.getInstance("SHA-256").digest(data)
                    val dataWithChecksum = ByteBuffer.allocate(data.size + checksum.size).apply {
                        put(data)
                        put(checksum)
                    }.array()

                    Log.d(TAG, "Secret ${secretKey.encoded.toHexString()}")
                    Log.d(TAG, "IV ${iv.toHexString()}")
                    Log.d(TAG, "Plain data ${dataWithChecksum.toHexString()}")

                    val encryptedData = KeyUtil.encryptData(secretKey, iv, dataWithChecksum)

                    Log.d(TAG, "Encrypted data ${encryptedData.toHexString()}")
                    val buffer = ByteBuffer.allocate(4 + encryptedData.size).apply {
                        order(ByteOrder.LITTLE_ENDIAN)
                        putInt(ownDeviceId)      // Append deviceId as plain text
                        put(encryptedData)       // Append encrypted data
                    }

                    val writeCharacteristic = gatt?.getService(gattServiceUUID)
                        ?.getCharacteristic(writeCharacteristicUUID)
                    writeCharacteristic?.value = buffer.array()

                    Log.d(TAG, "Send: ${buffer.array().toHexString()}")

                    writeCharacteristic?.let {
                        Log.d(TAG, "Writing characteristic...")
                        gatt.writeCharacteristic(it)
                    }
                } else {
                    Log.e(TAG, "Characteristic IV is null")
                }
            } else {
                Log.e(TAG, "Failed to read characteristic with status: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic?.uuid == writeCharacteristicUUID) {
                Log.d(TAG, "Characteristic write successful")
                gatt?.disconnect()
            } else {
                Log.e(TAG, "Failed to write characteristic with status: $status")
            }
        }
    }
}

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
import androidx.core.app.NotificationCompat
import io.github.maxamiri.link.common.DeviceList
import io.github.maxamiri.link.common.KeyUtil
import io.github.maxamiri.link.common.KeyUtil.convertStringToPrivateKey
import io.github.maxamiri.link.common.KeyUtil.convertStringToPublicKey
import io.github.maxamiri.link.common.KeyUtil.generateSecretKey
import io.github.maxamiri.link.common.toHexString
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * BLE client service for battery-powered devices in the NTL protocol.
 *
 * This service represents the battery-powered node in the Network Time Link (NTL) protocol.
 * It performs the following operations:
 * 1. Scans for nearby wired devices advertising via BLE
 * 2. Extracts time and location data from BLE advertisements
 * 3. Connects to wired devices via GATT to offload sensor data
 * 4. Encrypts and securely transmits data using ECDH + AES-GCM
 *
 * The service runs in the foreground to ensure continuous operation for time synchronization
 * and data offloading, which is essential for energy-efficient IoT operations.
 *
 * ## Data Flow:
 * - **Receive:** Device ID, epoch time, GPS coordinates from BLE advertisements
 * - **Send:** Encrypted sensor data (epoch, location) via GATT write characteristic
 *
 * @see WiredService for the server-side implementation
 */
class BatteryService : Service() {
    private val tag = "BLEClient"
    val context: Context = this

    /**
     * Companion object exposing observed data from wired devices as StateFlows.
     *
     * These StateFlows can be collected by UI components to display real-time
     * synchronization status and location data received from nearby wired devices.
     */
    companion object {
        private var _remoteDeviceId = MutableStateFlow(0)

        /**
         * Device ID of the currently connected or last seen wired device.
         */
        val remoteDeviceId: StateFlow<Int> = _remoteDeviceId.asStateFlow()

        private var _latitude = MutableStateFlow(0)

        /**
         * Latitude received from wired device (scaled by 10000, e.g., 12345 = 1.2345).
         */
        val latitude: StateFlow<Int> = _latitude.asStateFlow()

        private var _longitude = MutableStateFlow(0)

        /**
         * Longitude received from wired device (scaled by 10000, e.g., 67890 = 6.7890).
         */
        val longitude: StateFlow<Int> = _longitude.asStateFlow()

        private var _epoch = MutableStateFlow(0)

        /**
         * Unix epoch time in seconds received from wired device for time synchronization.
         */
        val epoch: StateFlow<Int> = _epoch.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    /**
     * Starts the service in foreground mode with a persistent notification.
     *
     * Running as a foreground service ensures that the BLE scanning and GATT operations
     * continue even when the app is in the background, which is critical for continuous
     * time synchronization and data offloading.
     */
    private fun startForegroundService() {
        val notificationChannelId = "LinkServiceChannel"
        val channel =
            NotificationChannel(
                notificationChannelId,
                "Link Service",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification: Notification =
            NotificationCompat
                .Builder(this, notificationChannelId)
                .setContentTitle("Link Service")
                .setContentText("Running Link Service")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your own icon
                .setContentIntent(getPendingIntent())
                .build()

        startForeground(1, notification)

        bleSetup()
    }

    /**
     * Creates a PendingIntent to launch MainActivity when the notification is tapped.
     *
     * @return PendingIntent for opening the app's main activity
     */
    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Cleans up BLE resources when the service is destroyed.
     *
     * Stops ongoing scans, disconnects GATT connections, and removes scheduled tasks.
     */
    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(scanRunnable)
        bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    // Device cryptographic configuration
    private var ownDeviceId: Int = 0
    private lateinit var privateKey: PrivateKey
    private lateinit var publicKey: PublicKey
    private lateinit var deviceList: DeviceList

    // Remote device GATT configuration (loaded dynamically when device is discovered)
    private lateinit var gattServiceUUID: UUID
    private lateinit var readCharacteristicUUID: UUID
    private lateinit var writeCharacteristicUUID: UUID
    private lateinit var remoteKey: PublicKey
    private lateinit var secretKey: SecretKey

    // Bluetooth components
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val handler = Handler()
    private val scanRunnable =
        Runnable {
            scan()
            // handler.postDelayed(this, scanInterval)
        }

    /**
     * Initializes the BLE client by loading device configuration and starting scanning.
     *
     * Loads the device registry from device_info.json, extracts the device's own keys,
     * and begins scanning for nearby wired devices.
     */
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

    /**
     * Starts a BLE scan for nearby wired devices advertising with manufacturer data.
     *
     * The scan filters for manufacturer ID 0xFFFF and uses low-latency mode for
     * quick discovery of nearby wired devices broadcasting time and location data.
     */
    @SuppressLint("MissingPermission")
    private fun scan() {
        // Stop any ongoing scan before starting a new one
        bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)

        // Filter by manufacturer ID only
        val scanFilter =
            ScanFilter
                .Builder()
                .setManufacturerData(0xFFFF, byteArrayOf())
                .build()

        val scanSettings =
            ScanSettings
                .Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        Log.d(tag, "Starting BLE scan")
        bluetoothAdapter.bluetoothLeScanner.startScan(
            listOf(scanFilter),
            scanSettings,
            scanCallback,
        )
    }

    /**
     * BLE scan callback that processes discovered wired devices.
     *
     * When a device is discovered:
     * 1. Extracts device ID, epoch time, and GPS coordinates from manufacturer data
     * 2. Validates the device against the known device whitelist
     * 3. Establishes GATT connection to send encrypted sensor data
     *
     * The manufacturer data format (16 bytes, little-endian):
     * - Bytes 0-3: Device ID (int32)
     * - Bytes 4-7: Epoch time in seconds (int32)
     * - Bytes 8-11: Latitude x 10000 (int32)
     * - Bytes 12-15: Longitude x 10000 (int32)
     */
    @SuppressLint("MissingPermission")
    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
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
                            tag,
                            "New Time/Location: ${_epoch.value};${_latitude.value};${_longitude.value}",
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
                    Log.d(tag, "Found device: ${device.address}, connecting.")

                    // Stop scanning to prevent duplicate connections
                    bluetoothAdapter.bluetoothLeScanner.stopScan(this)

                    // Clean up the previous GATT connection
                    bluetoothGatt?.disconnect()
                    bluetoothGatt?.close()
                    bluetoothGatt = null // Ensure previous GATT is fully cleared

                    bluetoothGatt = device.connectGatt(context, false, gattCallback)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { result ->
                    onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(tag, "Scan failed with error: $errorCode")
            }
        }

    /**
     * GATT client callback for handling connection, service discovery, and data transfer.
     *
     * This callback orchestrates the secure data offloading process:
     * 1. Connects to the wired device's GATT server
     * 2. Discovers GATT services and characteristics
     * 3. Reads the IV (initialization vector) from the read characteristic
     * 4. Encrypts sensor data using ECDH-derived AES key and the received IV
     * 5. Writes encrypted data to the write characteristic
     * 6. Disconnects and resumes scanning
     */
    @SuppressLint("MissingPermission")
    private val gattCallback =
        object : BluetoothGattCallback() {
            /**
             * Called when the connection state changes.
             *
             * @param gatt The GATT client instance
             * @param status Connection status code
             * @param newState New connection state (CONNECTED or DISCONNECTED)
             */
            override fun onConnectionStateChange(
                gatt: BluetoothGatt?,
                status: Int,
                newState: Int,
            ) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d(tag, "Connected to GATT server, discovering services.")
                    gatt?.discoverServices()
                    gatt?.requestMtu(512)
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d(tag, "Disconnected from GATT server")
                    bluetoothGatt?.disconnect()
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    // Restart scanning if disconnected
                    handler.postDelayed(scanRunnable, 8000L)
                }
            }

            /**
             * Called when the MTU (Maximum Transmission Unit) size is changed.
             *
             * @param gatt The GATT client instance
             * @param mtu The negotiated MTU size in bytes
             * @param status Operation status code
             */
            override fun onMtuChanged(
                gatt: BluetoothGatt?,
                mtu: Int,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(tag, "MTU changed successfully to $mtu bytes")
                    // You can now use the negotiated MTU size (e.g., mtu - 3 for payload size)
                } else {
                    Log.e(tag, "Failed to change MTU with status: $status")
                }
            }

            /**
             * Called when GATT services are discovered.
             *
             * Initiates reading the IV from the read characteristic to begin secure data transfer.
             *
             * @param gatt The GATT client instance
             * @param status Service discovery status code
             */
            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(
                gatt: BluetoothGatt?,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(tag, "Services discovered")
                    val service = gatt?.getService(gattServiceUUID)
                    val readCharacteristic = service?.getCharacteristic(readCharacteristicUUID)
                    readCharacteristic?.let {
                        Log.d(tag, "Reading characteristic.")
                        gatt.readCharacteristic(it)
                    }
                } else {
                    Log.e(tag, "Failed to discover services with status: $status")
                }
            }

            /**
             * Called when a characteristic read operation completes.
             *
             * Receives the IV from the wired device, encrypts sensor data with AES-GCM,
             * and writes it to the write characteristic. The data format includes:
             * - Sensor data (12 bytes): epoch, latitude, longitude
             * - SHA-256 checksum (32 bytes) for integrity verification
             * - Device ID (4 bytes, plaintext) prepended to encrypted payload
             *
             * @param gatt The GATT client instance
             * @param characteristic The characteristic that was read
             * @param status Read operation status code
             */
            @SuppressLint("MissingPermission")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
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
                        val dataWithChecksum =
                            ByteBuffer
                                .allocate(data.size + checksum.size)
                                .apply {
                                    put(data)
                                    put(checksum)
                                }.array()

                        Log.d(tag, "Secret ${secretKey.encoded.toHexString()}")
                        Log.d(tag, "IV ${iv.toHexString()}")
                        Log.d(tag, "Plain data ${dataWithChecksum.toHexString()}")

                        val encryptedData = KeyUtil.encryptData(secretKey, iv, dataWithChecksum)

                        Log.d(tag, "Encrypted data ${encryptedData.toHexString()}")
                        val buffer =
                            ByteBuffer.allocate(4 + encryptedData.size).apply {
                                order(ByteOrder.LITTLE_ENDIAN)
                                putInt(ownDeviceId) // Append deviceId as plain text
                                put(encryptedData) // Append encrypted data
                            }

                        val writeCharacteristic =
                            gatt
                                ?.getService(gattServiceUUID)
                                ?.getCharacteristic(writeCharacteristicUUID)
                        writeCharacteristic?.value = buffer.array()

                        Log.d(tag, "Send: ${buffer.array().toHexString()}")

                        writeCharacteristic?.let {
                            Log.d(tag, "Writing characteristic...")
                            gatt.writeCharacteristic(it)
                        }
                    } else {
                        Log.e(tag, "Characteristic IV is null")
                    }
                } else {
                    Log.e(tag, "Failed to read characteristic with status: $status")
                }
            }

            /**
             * Called when a characteristic write operation completes.
             *
             * Disconnects from the GATT server after successfully writing sensor data.
             *
             * @param gatt The GATT client instance
             * @param characteristic The characteristic that was written
             * @param status Write operation status code
             */
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic?.uuid == writeCharacteristicUUID) {
                    Log.d(tag, "Characteristic write successful")
                    gatt?.disconnect()
                } else {
                    Log.e(tag, "Failed to write characteristic with status: $status")
                }
            }
        }
}

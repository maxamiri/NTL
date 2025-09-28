// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.wired

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.maxamiri.link.common.DeviceList
import io.github.maxamiri.link.common.KeyUtil
import io.github.maxamiri.link.common.KeyUtil.convertStringToPrivateKey
import io.github.maxamiri.link.common.KeyUtil.convertStringToPublicKey
import io.github.maxamiri.link.common.KeyUtil.generateIV
import io.github.maxamiri.link.common.KeyUtil.generateSecretKey
import io.github.maxamiri.link.common.toHexString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.security.PrivateKey
import java.security.PublicKey

class WiredService : Service(), LocationListener {

    private val TAG = "WiredService"
    private lateinit var locationManager: LocationManager

    // Companion object to expose location and device data as StateFlows
    companion object {
        private var _latitude = MutableStateFlow(0)
        val latitude: StateFlow<Int> = _latitude.asStateFlow()
        private var _longitude = MutableStateFlow(0)
        val longitude: StateFlow<Int> = _longitude.asStateFlow()
        private var _epoch = MutableStateFlow(0)
        val epoch: StateFlow<Int> = _epoch.asStateFlow()
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()

        // Start the service as a foreground service
        startForegroundService()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // Request location updates every 1 second
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L, // 1 second in milliseconds
            0f,
            this,
            Looper.getMainLooper()
        )
        // Set up BLE services and characteristics
        bleSetup()
    }

    // Sets up a foreground service notification
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(getPendingIntent())
            .build()

        startForeground(1, notification)
    }

    // Creates a PendingIntent to launch MainActivity when the notification is tapped
    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    // Manufacturer data for BLE advertising
    private var manufacturerData: ByteArray? = null

    // Updates location data when location changes
    override fun onLocationChanged(location: Location) {
        _epoch.value = (System.currentTimeMillis() / 1000).toInt()
        _latitude.value = (location.latitude * 10000).toInt()
        _longitude.value = (location.longitude * 10000).toInt()

        Log.i(TAG, "Location: ${_epoch.value},${_latitude.value},${_longitude.value}")

        // Prepare manufacturer data buffer for BLE advertising
        val dataBuffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        dataBuffer.putInt(ownDeviceId)
        dataBuffer.putInt(_epoch.value)
        dataBuffer.putInt(_latitude.value)
        dataBuffer.putInt(_longitude.value)
        manufacturerData = dataBuffer.array()

        // Restart advertising with updated location data
        if (advertising) {
            stopAdvertising()
            startAdvertising()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Cleanup resources when the service is destroyed
    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
        handler.removeCallbacks(advertiseRunnable)
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
        gattServer.close()
    }

    // Main Service objects and variables
    private var ownDeviceId: Int = 0
    private lateinit var privateKey: PrivateKey
    private lateinit var publicKey: PublicKey
    private lateinit var gattServiceUUID: UUID
    private lateinit var readCharacteristicUUID: UUID
    private lateinit var writeCharacteristicUUID: UUID
    private lateinit var deviceList: DeviceList

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    private lateinit var gattServer: BluetoothGattServer
    private lateinit var gattService: BluetoothGattService
    private lateinit var readCharacteristic: BluetoothGattCharacteristic
    private lateinit var writeCharacteristic: BluetoothGattCharacteristic

    private val handler = Handler()
    private val advertiseRunnable = Runnable {
        // Start BLE advertising
        startAdvertising()
        // handler.postDelayed(this, 1000)
    }

    @SuppressLint("MissingPermission")
    fun bleSetup() {
        deviceList = KeyUtil.loadDeviceList(this, R.raw.device_info)
        ownDeviceId = deviceList.self.deviceId

        // Convert self keys for immediate access if needed
        privateKey = convertStringToPrivateKey(deviceList.self.privateKeyStr!!)
        publicKey = convertStringToPublicKey(deviceList.self.publicKeyStr)

        gattServiceUUID = UUID.fromString(deviceList.self.gattServiceUUID)
        readCharacteristicUUID = UUID.fromString(deviceList.self.readCharacteristicUUID)
        writeCharacteristicUUID = UUID.fromString(deviceList.self.writeCharacteristicUUID)

        Log.i(TAG, "BLEServer started")

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

        // Check if BLE advertising is supported
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            Log.e(TAG, "Device does not support BLE advertising.")
            return
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled.")
            return
        }

        // Setup GATT server with read and write characteristics
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        readCharacteristic = BluetoothGattCharacteristic(
            readCharacteristicUUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        writeCharacteristic = BluetoothGattCharacteristic(
            writeCharacteristicUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        gattService =
            BluetoothGattService(gattServiceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        gattService.addCharacteristic(readCharacteristic)
        gattService.addCharacteristic(writeCharacteristic)
        gattServer.addService(gattService)

        // Start advertising
        handler.post(advertiseRunnable)
    }

    private var advertising = false

    // Start advertising BLE data
    @SuppressLint("MissingPermission")
    @Synchronized
    private fun startAdvertising() {

        if (manufacturerData == null) {
            Log.d(TAG, "Location not yet available.")
            handler.postDelayed(advertiseRunnable, 2000L)
            return
        }

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        Log.d(TAG, "manufacturerData size: ${manufacturerData!!.size}")
        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(0xFFFF, manufacturerData) // manufacturerData
            .build()

        bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        advertising = true
    }

    // Stop advertising BLE data
    @Synchronized
    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        advertising = false
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
        Log.d(TAG, "Advertising stopped.")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "AdvertiseCallback.onStartSuccess")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "AdvertiseCallback.onStartFailure: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        lateinit var deviceMap: Pair<BluetoothDevice, ByteArray>

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothAdapter.STATE_CONNECTED) {
                Log.d(TAG, "Client connected: ${device?.address}")
                // We stop advertising, expecting the client to connect here.
                // On production, there should be a timer to disconnect faulty clients if don't connect
                stopAdvertising()
            } else if (newState == BluetoothAdapter.STATE_DISCONNECTED) {
                Log.d(TAG, "Client disconnected: ${device?.address}")
                // Restart advertising
                handler.postDelayed(advertiseRunnable, 2000L)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            Log.d(TAG, "onCharacteristicReadRequest characteristic.uuid: ${characteristic?.uuid}")
            if (characteristic?.uuid == readCharacteristicUUID) {
                device?.let {
                    deviceMap = device to generateIV()
                    gattServer.sendResponse(
                        device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset,
                        deviceMap.second
                    )
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {

            if (value == null || device == null) {
                Log.e(TAG, "value or device are null")
                return
            }

            Log.d(TAG, "onCharacteristicWriteRequest characteristic.uuid: ${characteristic?.uuid}")
            if (characteristic?.uuid == writeCharacteristicUUID) {
                Log.d(TAG, "Payload received, size:${value.size}, value:${value.toHexString()}")

                // Process data from remote device
                // Get remote device ID
                val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
                val remoteDeviceId = buffer.getInt()
                Log.d(TAG, "Remote DeviceId :$remoteDeviceId")

                // Extract encrypted data
                val encryptedData = ByteArray(buffer.remaining())
                buffer.get(encryptedData)
                Log.d(TAG, "Encrypted data ${encryptedData.toHexString()}")

                // Find remote device-id in device white-list
                val remoteDevice = deviceList.knownDevices.find { it.deviceId == remoteDeviceId }
                val remoteKey = convertStringToPublicKey(remoteDevice!!.publicKeyStr)

                // Generate AES secret key with ECDH
                val secretKey = generateSecretKey(privateKey, remoteKey)
                Log.d(TAG, "Secret ${secretKey.encoded.toHexString()}")
                Log.d(TAG, "IV ${deviceMap.second.toHexString()}")

                // Decrypt payload with AES
                val decryptedDataWithChecksum =
                    KeyUtil.decryptData(secretKey, deviceMap.second, encryptedData)

                // Validate data size with checksum
                val checksumLength = 32
                if (decryptedDataWithChecksum.size < checksumLength) {
                    Log.e(TAG, "Data too small")
                    return
                }

                // Split data and checksum
                val decryptedData =
                    decryptedDataWithChecksum.sliceArray(0 until decryptedDataWithChecksum.size - checksumLength)
                val checksum =
                    decryptedDataWithChecksum.sliceArray(decryptedDataWithChecksum.size - checksumLength until decryptedDataWithChecksum.size)

                // Compute and validate checksum
                val calculatedChecksum = MessageDigest.getInstance("SHA-256").digest(decryptedData)
                if (!calculatedChecksum.contentEquals(checksum)) {
                    Log.e(TAG, "Checksum verification failed.")
                    return
                }

                // Acknowledge data is correct for further processing
                Log.i(TAG, "Received data is correct, ${decryptedData.size}")

                // TODO: Send data to cloud

            }
            if (responseNeeded) {
                gattServer.sendResponse(
                    device,
                    requestId,
                    android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                    offset,
                    null
                )
            }
        }
    }
}

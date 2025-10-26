// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.common

import kotlinx.serialization.Serializable

/**
 * Represents device configuration and cryptographic information for NTL protocol devices.
 *
 * This data class contains device identification, public/private key pairs for ECDH key exchange,
 * and BLE GATT service UUIDs for communication. It is used to configure both battery-powered
 * and wired devices in the NTL network.
 *
 * @property deviceId Unique identifier for the device
 * @property publicKeyStr Base64-encoded EC public key (secp256r1/prime256v1 curve) for ECDH key agreement
 * @property privateKeyStr Base64-encoded EC private key (optional, only present for the device itself)
 * @property gattServiceUUID UUID of the BLE GATT service for communication (optional)
 * @property readCharacteristicUUID UUID of the GATT characteristic for reading data (optional)
 * @property writeCharacteristicUUID UUID of the GATT characteristic for writing data (optional)
 */
@Serializable
data class DeviceInfo(
    val deviceId: Int,
    val publicKeyStr: String,
    val privateKeyStr: String? = null,
    val gattServiceUUID: String? = null,
    val readCharacteristicUUID: String? = null,
    val writeCharacteristicUUID: String? = null,
)

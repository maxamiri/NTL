// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.common

import kotlinx.serialization.Serializable

/**
 * Container for device registry information in the NTL protocol.
 *
 * This data class represents the device whitelist and self-configuration loaded from
 * the device_info.json resource file. It contains information about the current device
 * and all trusted devices in the network that are authorized for secure communication.
 *
 * @property self Configuration and cryptographic information for the current device (includes private key)
 * @property knownDevices List of trusted devices in the network (public keys only)
 */
@Serializable
data class DeviceList(
    val self: DeviceInfo,
    val knownDevices: List<DeviceInfo>
)

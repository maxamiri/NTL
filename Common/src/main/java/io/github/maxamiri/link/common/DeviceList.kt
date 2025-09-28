// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.common

import kotlinx.serialization.Serializable

@Serializable
data class DeviceList(
    val self: DeviceInfo,
    val knownDevices: List<DeviceInfo>
)

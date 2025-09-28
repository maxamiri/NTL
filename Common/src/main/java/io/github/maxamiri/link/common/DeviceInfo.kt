// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.common

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val deviceId: Int,
    val publicKeyStr: String,
    val privateKeyStr: String? = null,
    val gattServiceUUID: String? = null,
    val readCharacteristicUUID: String? = null,
    val writeCharacteristicUUID: String? = null
)
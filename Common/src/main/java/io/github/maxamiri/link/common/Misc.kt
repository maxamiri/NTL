// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.common

/**
 * Converts a ByteArray to a hexadecimal string representation.
 *
 * This extension function formats each byte in the array as a two-digit hexadecimal value
 * and concatenates them into a single string without separators.
 *
 * @receiver The ByteArray to convert
 * @return A lowercase hexadecimal string representation of the byte array
 *
 * @sample
 * ```kotlin
 * val bytes = byteArrayOf(0x0A, 0x1B, 0x2C)
 * println(bytes.toHexString()) // Output: "0a1b2c"
 * ```
 */
fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

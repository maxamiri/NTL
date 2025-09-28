// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.common

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

object KeyUtil {

    const val EC_KEY_SIZE = 256  // Recommended key size for EC
    private const val AES_KEY_SIZE = 128 // AES-128
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    fun loadDeviceList(context: Context, resId: Int): DeviceList {
        val inputStream = context.resources.openRawResource(resId)
        val json = inputStream.bufferedReader().use { it.readText() }
        val deviceList = Json.decodeFromString<DeviceList>(json)
        return deviceList
    }

    fun generateSecretKey(ownPrivateKey: PrivateKey, otherPublicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ownPrivateKey)
        keyAgreement.doPhase(otherPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()
        return SecretKeySpec(sharedSecret, 0, AES_KEY_SIZE / 8, "AES")
    }

    fun convertStringToPrivateKey(privateKeyStr: String): PrivateKey {
        val privateBytes = Base64.getDecoder().decode(privateKeyStr)
        val keySpec = PKCS8EncodedKeySpec(privateBytes)
        return KeyFactory.getInstance("EC").generatePrivate(keySpec)
    }

    fun convertStringToPublicKey(publicKeyStr: String): PublicKey {
        val publicBytes = Base64.getDecoder().decode(publicKeyStr)
        val keySpec = X509EncodedKeySpec(publicBytes)
        return KeyFactory.getInstance("EC").generatePublic(keySpec)
    }

    fun encryptData(secretKey: SecretKey, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        return cipher.doFinal(data)
    }

    fun decryptData(secretKey: SecretKey, iv: ByteArray, encryptedData: ByteArray): ByteArray {

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        return cipher.doFinal(encryptedData)
    }

    fun logSharedSecret(secretKey: SecretKey?) {
        secretKey?.let {
            val secretHex = it.encoded.joinToString("") { byte -> "%02x".format(byte) }
            Log.d("ECDH", "Generated Shared Secret Key: $secretHex")
        }
    }

    fun generateIV(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv) // Fills the byte array with random values
        return iv
    }
}

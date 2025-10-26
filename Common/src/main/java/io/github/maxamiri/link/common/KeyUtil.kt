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
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utility object for the NTL protocol.
 *
 * This object provides key management and encryption/decryption utilities for secure
 * communication between battery-powered and wired devices. It implements:
 * - ECDH (Elliptic Curve Diffie-Hellman) key exchange using secp256r1 curve
 * - AES-128-GCM authenticated encryption
 * - Device registry loading and management
 *
 * The security model assumes:
 * - All devices operate under a unified trust framework
 * - Pre-shared public keys are distributed through device_info.json
 * - Each device has a unique EC key pair (256-bit)
 */
object KeyUtil {
    /**
     * Elliptic curve key size in bits (secp256r1/prime256v1 curve).
     */
    const val EC_KEY_SIZE = 256 // Recommended key size for EC

    /**
     * AES symmetric key size in bits.
     */
    private const val AES_KEY_SIZE = 128 // AES-128

    /**
     * GCM authentication tag length in bits.
     */
    private const val GCM_TAG_LENGTH = 128

    /**
     * GCM initialization vector (IV) length in bytes.
     */
    private const val GCM_IV_LENGTH = 12

    /**
     * Loads the device registry from a raw JSON resource file.
     *
     * This method deserializes the device_info.json file containing the current device's
     * configuration (including its private key) and the list of known trusted devices
     * (public keys only) in the NTL network.
     *
     * @param context Android context for accessing resources
     * @param resId Resource ID of the device_info.json file (e.g., R.raw.device_info)
     * @return DeviceList containing self configuration and known devices
     * @throws kotlinx.serialization.SerializationException if JSON parsing fails
     */
    fun loadDeviceList(
        context: Context,
        resId: Int,
    ): DeviceList {
        val inputStream = context.resources.openRawResource(resId)
        val json = inputStream.bufferedReader().use { it.readText() }
        val deviceList = Json.decodeFromString<DeviceList>(json)
        return deviceList
    }

    /**
     * Generates a shared AES secret key using ECDH key agreement.
     *
     * This method performs Elliptic Curve Diffie-Hellman (ECDH) key exchange to derive
     * a shared secret between two devices. The shared secret is then used to create
     * an AES-128 symmetric key for encrypting/decrypting communications.
     *
     * @param ownPrivateKey The local device's EC private key
     * @param otherPublicKey The remote device's EC public key
     * @return SecretKey AES-128 symmetric key derived from ECDH shared secret
     * @throws java.security.InvalidKeyException if keys are incompatible
     */
    fun generateSecretKey(
        ownPrivateKey: PrivateKey,
        otherPublicKey: PublicKey,
    ): SecretKey {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ownPrivateKey)
        keyAgreement.doPhase(otherPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()
        return SecretKeySpec(sharedSecret, 0, AES_KEY_SIZE / 8, "AES")
    }

    /**
     * Converts a Base64-encoded string to an EC PrivateKey object.
     *
     * The private key string must be in PKCS#8 format and Base64-encoded.
     *
     * @param privateKeyStr Base64-encoded PKCS#8 private key string
     * @return PrivateKey EC private key object for cryptographic operations
     * @throws java.security.spec.InvalidKeySpecException if the key format is invalid
     */
    fun convertStringToPrivateKey(privateKeyStr: String): PrivateKey {
        val privateBytes = Base64.getDecoder().decode(privateKeyStr)
        val keySpec = PKCS8EncodedKeySpec(privateBytes)
        return KeyFactory.getInstance("EC").generatePrivate(keySpec)
    }

    /**
     * Converts a Base64-encoded string to an EC PublicKey object.
     *
     * The public key string must be in X.509 format and Base64-encoded.
     *
     * @param publicKeyStr Base64-encoded X.509 public key string
     * @return PublicKey EC public key object for cryptographic operations
     * @throws java.security.spec.InvalidKeySpecException if the key format is invalid
     */
    fun convertStringToPublicKey(publicKeyStr: String): PublicKey {
        val publicBytes = Base64.getDecoder().decode(publicKeyStr)
        val keySpec = X509EncodedKeySpec(publicBytes)
        return KeyFactory.getInstance("EC").generatePublic(keySpec)
    }

    /**
     * Encrypts data using AES-128-GCM authenticated encryption.
     *
     * GCM (Galois/Counter Mode) provides both confidentiality and authenticity,
     * ensuring that encrypted data cannot be tampered with. The IV must be unique
     * for each encryption operation with the same key.
     *
     * @param secretKey AES-128 symmetric key (typically derived from ECDH)
     * @param iv 12-byte initialization vector (must be unique per encryption)
     * @param data Plaintext data to encrypt
     * @return Encrypted data with authentication tag appended
     * @throws javax.crypto.AEADBadTagException if authentication fails
     */
    fun encryptData(
        secretKey: SecretKey,
        iv: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        return cipher.doFinal(data)
    }

    /**
     * Decrypts data using AES-128-GCM authenticated encryption.
     *
     * This method verifies the authentication tag before decrypting, ensuring the
     * data has not been tampered with. Decryption fails if the tag is invalid.
     *
     * @param secretKey AES-128 symmetric key (must match the encryption key)
     * @param iv 12-byte initialization vector (must match the IV used for encryption)
     * @param encryptedData Encrypted data with authentication tag
     * @return Decrypted plaintext data
     * @throws javax.crypto.AEADBadTagException if authentication tag verification fails
     */
    fun decryptData(
        secretKey: SecretKey,
        iv: ByteArray,
        encryptedData: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        return cipher.doFinal(encryptedData)
    }

    /**
     * Logs the shared secret key in hexadecimal format for debugging purposes.
     *
     * **Warning:** This method should only be used during development and testing.
     * Logging cryptographic keys in production environments is a security risk.
     *
     * @param secretKey The AES secret key to log (nullable)
     */
    fun logSharedSecret(secretKey: SecretKey?) {
        secretKey?.let {
            val secretHex = it.encoded.joinToString("") { byte -> "%02x".format(byte) }
            Log.d("ECDH", "Generated Shared Secret Key: $secretHex")
        }
    }

    /**
     * Generates a cryptographically secure random initialization vector (IV) for AES-GCM.
     *
     * The IV must be unique for each encryption operation with the same key to maintain
     * security. This method uses SecureRandom to ensure unpredictability.
     *
     * @return 12-byte random IV suitable for AES-GCM encryption
     */
    fun generateIV(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv) // Fills the byte array with random values
        return iv
    }
}

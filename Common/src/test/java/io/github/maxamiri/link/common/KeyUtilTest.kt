// Copyright (c) 2025 Max Amiri
// Licensed under the MIT License. See LICENSE file in the project root for full license information.

package io.github.maxamiri.link.common

import io.github.maxamiri.link.common.KeyUtil.EC_KEY_SIZE
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Base64

/**
 * Unit tests for cryptographic key generation utilities.
 *
 * This test class provides utilities for generating EC key pairs that can be used
 * in the device_info.json configuration files. The test output includes Base64-encoded
 * keys that can be directly copied into device configurations.
 *
 * **Note:** These tests are primarily utilities for key generation during setup,
 * not traditional validation tests.
 */
class KeyUtilTest {
    /**
     * Generates an Elliptic Curve (EC) key pair using the secp256r1 curve.
     *
     * This method is used for creating new device key pairs during initial configuration.
     * The generated keys use the same 256-bit EC parameters as the NTL protocol.
     *
     * @return KeyPair containing EC public and private keys
     */
    private fun generateECKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(EC_KEY_SIZE)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Encodes an EC private key to Base64 PKCS#8 format.
     *
     * @param privateKey The EC private key to encode
     * @return Base64-encoded string representation of the private key
     */
    private fun encodePrivateKey(privateKey: PrivateKey): String = Base64.getEncoder().encodeToString(privateKey.encoded)

    /**
     * Encodes an EC public key to Base64 X.509 format.
     *
     * @param publicKey The EC public key to encode
     * @return Base64-encoded string representation of the public key
     */
    private fun encodePublicKey(publicKey: PublicKey): String = Base64.getEncoder().encodeToString(publicKey.encoded)

    /**
     * Generates and prints a new EC key pair for device configuration.
     *
     * This test generates a fresh EC key pair and prints both the private and public keys
     * in Base64 format. The output can be directly copied into the device_info.json file
     * when configuring new devices for the NTL network.
     *
     * **Usage:**
     * 1. Run this test
     * 2. Copy the printed keys from the test output
     * 3. Paste them into device_info.json for the new device
     */
    @Test
    fun generateAndPrintKeys() {
        // Generate a new EC key pair
        val keyPair: KeyPair = generateECKeyPair()

        // Encode the keys as Base64 strings
        val privateKeyStr = encodePrivateKey(keyPair.private)
        val publicKeyStr = encodePublicKey(keyPair.public)

        // Print the keys for easy copy-pasting into the resource file
        println("Private Key: $privateKeyStr")
        println("Public Key: $publicKeyStr")
    }
}

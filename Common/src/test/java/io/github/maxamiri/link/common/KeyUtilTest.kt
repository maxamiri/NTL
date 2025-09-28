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

class KeyUtilTest {

    // JUnit Only
    private fun generateECKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(EC_KEY_SIZE)
        return keyPairGenerator.generateKeyPair()
    }

    private fun encodePrivateKey(privateKey: PrivateKey): String {
        return Base64.getEncoder().encodeToString(privateKey.encoded)
    }

    private fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }

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

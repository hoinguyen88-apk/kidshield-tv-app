package com.example.data.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinCryptoHelper {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 15_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    const val PIN_LENGTH = 8

    private val secureRandom = SecureRandom()

    fun isValidPinFormat(pin: String): Boolean {
        return pin.length == PIN_LENGTH && pin.all { it.isDigit() }
    }

    fun hashPin(pin: String): Pair<String, String> {
        require(isValidPinFormat(pin)) { "PIN must be exactly $PIN_LENGTH digits" }
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val derived = pbkdf2(pin.toCharArray(), salt)
        return Base64.encodeToString(derived, Base64.NO_WRAP) to
            Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, expectedHashB64: String?, saltB64: String?): Boolean {
        if (!isValidPinFormat(pin) || expectedHashB64.isNullOrBlank() || saltB64.isNullOrBlank()) {
            return false
        }
        return try {
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expectedHash = Base64.decode(expectedHashB64, Base64.NO_WRAP)
            val actualHash = pbkdf2(pin.toCharArray(), salt)
            MessageDigest.isEqual(actualHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    private fun pbkdf2(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Produces a visual-only cryptographic token so onlookers cannot tell which digit was pressed.
     * Uses a per-dialog random nonce so identical inputs produce completely different tokens.
     */
    fun computeMaskedVisualDigest(input: String, sessionNonce: String): String {
        if (input.isEmpty()) return "AES/SHA256 • CHỜ NHẬP"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sessionNonce:$input".toByteArray(Charsets.UTF_8))
        val hex = digest.take(4).joinToString("") { "%02X".format(it) }
        return "ENC-${hex.take(4)}-${hex.drop(4)}"
    }

    fun generateSessionNonce(): String {
        val bytes = ByteArray(12)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun generateShuffledDigits(): List<Int> {
        val list = (0..9).toMutableList()
        for (i in list.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
        return list
    }
}

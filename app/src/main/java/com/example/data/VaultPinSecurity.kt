package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Device-bound PIN verification using Android Keystore.
 * Existing SHA-256 PIN records are accepted once and upgraded after success.
 */
object VaultPinSecurity {
    private const val KEY_ALIAS = "emreview_vault_pin_hmac"
    private const val PREFIX = "hmac-v1:"

    fun validatePin(pin: String): Boolean =
        pin.length == 4 && pin.all(Char::isDigit)

    fun hashForStorage(pin: String): String {
        require(validatePin(pin)) { "PIN must be exactly 4 digits." }
        return PREFIX + Base64.encodeToString(sign(pin), Base64.NO_WRAP)
    }

    fun verify(pin: String, stored: String?): Boolean {
        if (!validatePin(pin) || stored.isNullOrBlank()) return false
        return if (stored.startsWith(PREFIX)) {
            val expected = runCatching {
                Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            }.getOrNull() ?: return false
            MessageDigest.isEqual(expected, sign(pin))
        } else {
            legacySha256(pin) == stored
        }
    }

    fun isModernRecord(stored: String?): Boolean =
        stored?.startsWith(PREFIX) == true

    private fun sign(pin: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(getOrCreateKey())
        return mac.doFinal(pin.toByteArray(Charsets.UTF_8))
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setDigests(KeyProperties.DIGEST_SHA256).build()
        )
        return generator.generateKey()
    }

    private fun legacySha256(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

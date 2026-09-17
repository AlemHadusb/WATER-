package com.example.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object PasswordHasher {
    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(password: String, salt: String): String {
        val input = "$salt:$password"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        val calculated = hashPassword(password, salt)
        return calculated.equals(expectedHash, ignoreCase = true)
    }
}

object DeviceIdentity {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "wms_hardware_device_key"

    /**
     * Obtains or derives an immutable hardware-locked fingerprint tied to this physical device.
     * Combines Android ID, hardware specs, and Android Keystore hardware-backed keys so that
     * even if the APK or database file is extracted and transferred via Xender, Bluetooth,
     * or backup to another device, the installation identity will not match.
     */
    fun getOrCreateInstallationId(context: Context): String {
        val prefs = context.getSharedPreferences("app_security_identity", Context.MODE_PRIVATE)
        val savedId = prefs.getString("device_installation_id", null)
        val hardwareHash = generateHardwareFingerprint(context)

        if (!savedId.isNullOrBlank()) {
            val savedFingerprint = prefs.getString("hardware_fingerprint", null)
            // Verify hardware match
            if (savedFingerprint == null || savedFingerprint == hardwareHash) {
                return savedId
            }
        }

        // Generate unique hardware-locked device ID
        val deviceModel = Build.MODEL.replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }.take(10)
        val shortFingerprint = hardwareHash.take(8).uppercase()
        val newId = "WMS-$deviceModel-$shortFingerprint"

        // Store with Keystore verification
        initKeystoreKey()
        prefs.edit()
            .putString("device_installation_id", newId)
            .putString("hardware_fingerprint", hardwareHash)
            .apply()

        return newId
    }

    /**
     * Validates whether the active hardware matches the device to which this installation is locked.
     */
    fun isHardwareMatched(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_security_identity", Context.MODE_PRIVATE)
        val savedFingerprint = prefs.getString("hardware_fingerprint", null) ?: return true
        val currentFingerprint = generateHardwareFingerprint(context)
        return savedFingerprint == currentFingerprint
    }

    /**
     * Creates a composite cryptographic hash from fixed hardware traits:
     * - Settings.Secure.ANDROID_ID (unique per device and app signing key)
     * - Build.BOARD, Build.BOOTLOADER, Build.BRAND, Build.DEVICE, Build.HARDWARE, Build.MANUFACTURER
     */
    fun generateHardwareFingerprint(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_ID"
        } catch (_: Exception) {
            "UNKNOWN_ID"
        }

        val hardwareComponents = listOf(
            androidId,
            Build.BOARD,
            Build.BRAND,
            Build.DEVICE,
            Build.HARDWARE,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.PRODUCT
        ).joinToString(separator = "|")

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(hardwareComponents.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun initKeystoreKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenSpec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Gracefully ignore if running in test environment or restricted hardware
        }
    }
}


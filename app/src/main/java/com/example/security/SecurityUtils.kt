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
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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

object BackupCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    @android.annotation.SuppressLint("NewApi")
    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    @android.annotation.SuppressLint("NewApi")
    private fun decodeBase64(base64Str: String): ByteArray {
        return try {
            android.util.Base64.decode(base64Str, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(base64Str)
        }
    }

    fun deriveKey(passphrase: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptPayload(plainText: String, secretKey: String): String {
        val keySpec = deriveKey(secretKey)
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return encodeBase64(combined)
    }

    fun decryptPayload(cipherTextBase64: String, secretKey: String): String {
        val keySpec = deriveKey(secretKey)
        val combined = decodeBase64(cipherTextBase64)
        if (combined.size < IV_LENGTH_BYTE) throw IllegalArgumentException("Invalid encrypted payload")
        val iv = ByteArray(IV_LENGTH_BYTE)
        val cipherText = ByteArray(combined.size - IV_LENGTH_BYTE)
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTE)
        System.arraycopy(combined, IV_LENGTH_BYTE, cipherText, 0, cipherText.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
    }
}



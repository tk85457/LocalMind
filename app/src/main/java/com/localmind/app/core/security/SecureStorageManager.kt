package com.localmind.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.localmind.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OWASP MASVS L2 — Secure Storage & Cryptography
 *
 * Fixes:
 * - MSTG-CRYPTO-1: AES/GCM/NoPadding (replaces ECB/CBC)
 * - MSTG-CRYPTO-4: SHA-256 (replaces MD5/SHA-1)
 * - MSTG-STORAGE-1: EncryptedSharedPreferences for sensitive prefs
 * - MSTG-STORAGE-2: Android Keystore for key storage
 * - MSTG-CRYPTO-6: SecureRandom (replaces java.util.Random)
 * - Keys never leave Android Keystore hardware boundary
 */
@Singleton
class SecureStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SecureStorage"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "localmind_master_key_v1"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val ENCRYPTED_PREFS_FILE = "localmind_secure_prefs"
    }

    // SecureRandom — cryptographically secure (MSTG-CRYPTO-6)
    private val secureRandom = SecureRandom()

    // Android Keystore — AES/GCM key generation (MSTG-STORAGE-2, MSTG-CRYPTO-1)
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply { init(keyGenSpec) }
            .generateKey()
    }

    // Encrypt with AES/GCM/NoPadding — returns iv + ciphertext
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE)
        val key = getOrCreateKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size > GCM_IV_LENGTH) { "Invalid encrypted data length" }
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(AES_MODE)
        val key = getOrCreateKey()
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    fun encryptString(plaintext: String): ByteArray = encrypt(plaintext.toByteArray(Charsets.UTF_8))
    fun decryptString(data: ByteArray): String = decrypt(data).toString(Charsets.UTF_8)

    // SHA-256 hashing (replaces MD5/SHA-1) (MSTG-CRYPTO-4)
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun sha512(input: String): String {
        val digest = MessageDigest.getInstance("SHA-512")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    // Secure random bytes (replaces java.util.Random) (MSTG-CRYPTO-6)
    fun randomBytes(count: Int): ByteArray {
        return ByteArray(count).also { secureRandom.nextBytes(it) }
    }

    fun randomInt(bound: Int): Int = secureRandom.nextInt(bound)

    // EncryptedSharedPreferences for sensitive key-value data (MSTG-STORAGE-1)
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun putSecureString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    fun getSecureString(key: String, default: String = ""): String {
        return encryptedPrefs.getString(key, default) ?: default
    }

    fun removeSecureKey(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    // Input validation (OWASP — input sanitization)
    fun sanitizeInput(input: String, maxLength: Int = 1024): String {
        return input
            .take(maxLength)
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")
    }

    fun isValidModelId(repoId: String): Boolean {
        return repoId.matches(Regex("^[a-zA-Z0-9._\\-/]{1,200}$"))
    }
}

package com.routepix.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec


object SecurityManager {

    private const val PREFS_FILE = "routepix_secure_prefs"
    private const val AES_ALGO = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    @SuppressLint("StaticFieldLeak") // Application context is safe
    private lateinit var prefs: SharedPreferences

    
    fun init(context: Context) {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }


    
    fun saveBotToken(tripId: String, token: String) {
        prefs.edit().putString("bot_token_$tripId", token).apply()
    }

    
    fun getBotToken(tripId: String): String? {
        return prefs.getString("bot_token_$tripId", null)
    }


    
    fun encryptForFirestore(plaintext: String, key: String): String {
        val secretKey = deriveKey(key)
        val cipher = Cipher.getInstance(AES_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv // GCM generates a random IV
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    
    fun decryptFromFirestore(ciphertext: String, key: String): String {
        val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
        val secretKey = deriveKey(key)

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(AES_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }


    
    private fun deriveKey(input: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }
}


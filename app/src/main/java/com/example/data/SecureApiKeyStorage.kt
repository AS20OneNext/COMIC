package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.secureKeyDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "comic_secure_keys")

/**
 * Abstraction for Gemini API authentication credentials so V0.1's user-entered API key
 * can later be upgraded to backend-issued ephemeral tokens without changing the Live client.
 */
interface ApiKeyAuthProvider {
  suspend fun getCredential(): String?
}

interface ApiKeyStorage : ApiKeyAuthProvider {
  val hasSavedKeyFlow: Flow<Boolean>

  suspend fun saveKey(rawKey: String)

  suspend fun deleteKey()

  suspend fun hasSavedKey(): Boolean
}

class SecureApiKeyStorage(private val context: Context) : ApiKeyStorage {

  override val hasSavedKeyFlow: Flow<Boolean> =
    context.secureKeyDataStore.data.map { prefs ->
      !prefs[KEY_CIPHERTEXT].isNullOrBlank() && !prefs[KEY_IV].isNullOrBlank()
    }

  override suspend fun hasSavedKey(): Boolean = hasSavedKeyFlow.first()

  override suspend fun saveKey(rawKey: String) {
    val trimmed = rawKey.trim()
    require(trimmed.isNotEmpty()) { "API key must not be empty" }
    withContext(Dispatchers.IO) {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
      val encryptedBytes = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
      val ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
      val cipherBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

      context.secureKeyDataStore.edit { prefs ->
        prefs[KEY_CIPHERTEXT] = cipherBase64
        prefs[KEY_IV] = ivBase64
      }
    }
  }

  override suspend fun getCredential(): String? =
    withContext(Dispatchers.IO) {
      val prefs = context.secureKeyDataStore.data.first()
      val cipherBase64 = prefs[KEY_CIPHERTEXT] ?: return@withContext null
      val ivBase64 = prefs[KEY_IV] ?: return@withContext null
      runCatching {
        val cipherBytes = Base64.decode(cipherBase64, Base64.NO_WRAP)
        val ivBytes = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
          Cipher.DECRYPT_MODE,
          getOrCreateSecretKey(),
          GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes),
        )
        val plainBytes = cipher.doFinal(cipherBytes)
        String(plainBytes, StandardCharsets.UTF_8)
      }.getOrNull()
    }

  override suspend fun deleteKey() {
    withContext(Dispatchers.IO) {
      context.secureKeyDataStore.edit { prefs ->
        prefs.remove(KEY_CIPHERTEXT)
        prefs.remove(KEY_IV)
      }
    }
  }

  private fun getOrCreateSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
    if (existing != null) {
      return existing.secretKey
    }
    val keyGenerator =
      KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          KEYSTORE_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "comic_gemini_api_key_aes_gcm"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    private val KEY_CIPHERTEXT = stringPreferencesKey("encrypted_api_key_b64")
    private val KEY_IV = stringPreferencesKey("encrypted_api_key_iv_b64")
  }
}

package com.nehonar.operator.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nehonar.operator.core.ai.AIProviderType
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Cifra la API key con una clave AES-256/GCM que vive en el Android Keystore (no
 * exportable) y guarda ciphertext + IV en DataStore. La clave nunca se guarda en claro
 * ni se registra en logs.
 *
 * No testeada con unit test: el provider "AndroidKeyStore" no existe bajo Robolectric
 * (ver docs/decisiones.md D-008). El resto de la lógica que depende de esta interfaz
 * se testea con un `ApiKeyStore` falso.
 */
class AndroidKeystoreApiKeyStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ApiKeyStore {

    override suspend fun get(provider: AIProviderType): String? {
        val prefs = dataStore.data.first()
        val ciphertextB64 = prefs[ciphertextKey(provider)] ?: return null
        val ivB64 = prefs[ivKey(provider)] ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivB64, Base64.NO_WRAP))
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            String(cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    override suspend fun set(provider: AIProviderType, apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        dataStore.edit { prefs ->
            prefs[ciphertextKey(provider)] = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            prefs[ivKey(provider)] = Base64.encodeToString(iv, Base64.NO_WRAP)
        }
    }

    override suspend fun clear(provider: AIProviderType) {
        dataStore.edit { prefs ->
            prefs.remove(ciphertextKey(provider))
            prefs.remove(ivKey(provider))
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun ciphertextKey(provider: AIProviderType) =
        stringPreferencesKey("apikey_${provider.name.lowercase()}_ct")

    private fun ivKey(provider: AIProviderType) =
        stringPreferencesKey("apikey_${provider.name.lowercase()}_iv")

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "operator_api_key_aes"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}

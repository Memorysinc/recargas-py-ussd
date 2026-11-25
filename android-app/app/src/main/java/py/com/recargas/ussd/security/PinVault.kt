package py.com.recargas.ussd.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PinVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPin(operatorId: String): Boolean = !prefs.getString(keyFor(operatorId), null).isNullOrBlank()

    fun savePin(operatorId: String, pin: String) {
        val cleanPin = pin.trim()
        require(cleanPin.isNotEmpty()) { "El PIN no puede estar vacío." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(cleanPin.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = iv.size.toByte()
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        prefs.edit().putString(keyFor(operatorId), encoded).apply()
    }

    fun getPin(operatorId: String): String? {
        val encoded = prefs.getString(keyFor(operatorId), null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size < 2) return null
            val ivLen = payload[0].toInt() and 0xFF
            if (payload.size <= 1 + ivLen) return null
            val iv = payload.copyOfRange(1, 1 + ivLen)
            val encrypted = payload.copyOfRange(1 + ivLen, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun clearPin(operatorId: String) {
        prefs.edit().remove(keyFor(operatorId)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun keyFor(operatorId: String) = "enc_pin_${operatorId.lowercase()}"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "recargas_py_pin_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "recargas_pin_vault"
        private const val GCM_TAG_BITS = 128
    }
}

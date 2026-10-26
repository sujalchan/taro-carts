package nz.ac.aut.comp713.tarocartmobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the opaque session token encrypted with an Android Keystore AES key. */
class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("mobile-session", Context.MODE_PRIVATE)
    private val keyAlias = "taro-cart-mobile-session"

    fun read(): Pair<MobileAccount, String>? = runCatching {
        val encoded = preferences.getString("session", null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        MobileAccount(json.getString("username"), json.getString("role"),
            if (json.isNull("profileId")) null else json.getLong("profileId")) to json.getString("token")
    }.getOrElse {
        clear()
        null
    }

    fun write(account: MobileAccount, token: String) {
        val json = JSONObject().put("username", account.username).put("role", account.role)
            .put("profileId", account.profileId).put("token", token)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val combined = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        preferences.edit().putString("session", Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    fun clear() { preferences.edit().remove("session").apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}

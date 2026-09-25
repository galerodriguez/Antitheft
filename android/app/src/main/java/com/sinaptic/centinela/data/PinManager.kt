package com.sinaptic.centinela.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Gestiona el PIN maestro de la app.
 *
 * - Se define en el primer arranque (onboarding).
 * - Protege: abrir la app, cambiar ajustes sensibles y desactivar el administrador de
 *   dispositivo (paso previo a desinstalar).
 *
 * El PIN se guarda como hash salado dentro de EncryptedSharedPreferences (doble protección:
 * cifrado en reposo + hash). Para producción, considerar PBKDF2/Argon2 con más iteraciones.
 */
class PinManager(context: Context) {

    private val prefs = DeviceRepository(context).prefs()

    fun isPinSet(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash(pin, salt))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.fromHex() ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        return constantTimeEquals(hash(pin, salt), expected)
    }

    fun changePin(current: String, newPin: String): Boolean {
        if (!verify(current)) return false
        setPin(newPin)
        return true
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray()).toHex()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }

    // IMPORTANTE: enmascarar con 0xFF. Un Byte en Kotlin es con signo; sin la máscara, los
    // bytes >= 0x80 se convertían a 8 caracteres (ffffffXX) en vez de 2, corrompiendo la sal.
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val KEY_HASH = "master_pin_hash"
        private const val KEY_SALT = "master_pin_salt"
    }
}

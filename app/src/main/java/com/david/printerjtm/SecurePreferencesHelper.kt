package com.david.printerjtm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Helper class to securely manage application preferences using AndroidX EncryptedSharedPreferences.
 * Provides AES-256 encrypted storage for sensitive data like Bluetooth MAC addresses, paper configurations,
 * subscription status, and application settings.
 */
object SecurePreferencesHelper {

    private const val TAG = "SecurePreferences"
    private const val SECURE_PREFS_NAME = "printer_jtm_secure_prefs"
    private const val MASTER_KEY_ALIAS = MasterKey.DEFAULT_MASTER_KEY_ALIAS

    @Volatile
    private var encryptedPrefsInstance: SharedPreferences? = null
    @Volatile
    private var storageRecovered = false
    private val recoveryNoticePending = AtomicBoolean(false)
    private val recoveryListeners = CopyOnWriteArrayList<() -> Unit>()

    val wasRecoveredFromCorruption: Boolean
        get() = storageRecovered

    fun consumeRecoveryNotice(): Boolean = recoveryNoticePending.getAndSet(false)

    fun addRecoveryListener(listener: () -> Unit) {
        recoveryListeners.add(listener)
    }

    fun removeRecoveryListener(listener: () -> Unit) {
        recoveryListeners.remove(listener)
    }

    fun getPreferences(context: Context): SharedPreferences {
        return encryptedPrefsInstance ?: synchronized(this) {
            encryptedPrefsInstance ?: openEncryptedPreferences(context.applicationContext).also {
                encryptedPrefsInstance = it
            }
        }
    }

    private fun openEncryptedPreferences(context: Context): SharedPreferences {
        return EncryptedStorageRecovery(
            openStorage = { createEncryptedPreferences(context, migrateLegacy = !storageRecovered) },
            resetStorage = { resetEncryptedStorage(context) },
            isRecoverableFailure = ::isRecoverableStorageFailure,
            onRecovered = {
                clearLegacyPlaintextPreferences(context)
                storageRecovered = true
                recoveryNoticePending.set(true)
                recoveryListeners.forEach { listener ->
                    try {
                        listener()
                    } catch (_: RuntimeException) {
                        Log.w(TAG, "A secure-storage recovery listener failed")
                    }
                }
                Log.w(TAG, "Encrypted settings were reset after a key or data integrity failure")
            }
        ).open()
    }

    private fun createEncryptedPreferences(context: Context, migrateLegacy: Boolean): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val securePrefs = EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        if (migrateLegacy) {
            migrateLegacyPreferences(context, securePrefs)
        }
        return securePrefs
    }

    private fun resetEncryptedStorage(context: Context) {
        context.deleteSharedPreferences(SECURE_PREFS_NAME)
        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$SECURE_PREFS_NAME.xml")
        check(!prefsFile.exists()) {
            "Unable to remove encrypted preferences for secure recovery"
        }

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            keyStore.deleteEntry(MASTER_KEY_ALIAS)
        }
    }

    private fun isRecoverableStorageFailure(error: Exception): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is GeneralSecurityException || cause is KeyStoreException || cause is IOException) {
                return true
            }
            if (cause is IllegalStateException &&
                cause.message.orEmpty().contains("decrypt", ignoreCase = true)
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun clearLegacyPlaintextPreferences(context: Context) {
        listOf(SettingsActivity.LEGACY_PREFS_NAME, SettingsActivity.PREFS_NAME).forEach { name ->
            val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            check(preferences.edit().clear().commit()) {
                "Unable to clear legacy plaintext preferences after secure recovery"
            }
        }
    }

    /**
     * Safely migrates keys and values from legacy unencrypted SharedPreferences to the encrypted file.
     */
    private fun migrateLegacyPreferences(context: Context, securePrefs: SharedPreferences) {
        val legacyNames = listOf(SettingsActivity.LEGACY_PREFS_NAME, SettingsActivity.PREFS_NAME)
        for (legacyName in legacyNames) {
            val legacyPrefs = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
            val allLegacy = legacyPrefs.all
            if (allLegacy.isNotEmpty()) {
                val editor = securePrefs.edit()
                for ((key, value) in allLegacy) {
                    if (securePrefs.contains(key)) continue
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                check(editor.commit()) {
                    "Unable to migrate legacy preferences to encrypted storage"
                }
                check(legacyPrefs.edit().clear().commit()) {
                    "Unable to clear legacy plaintext preferences after migration"
                }
            }
        }
    }
}

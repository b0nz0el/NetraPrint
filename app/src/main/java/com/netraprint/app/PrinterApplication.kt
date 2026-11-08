package com.netraprint.app

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import java.io.File
import java.util.concurrent.TimeUnit

class PrinterApplication : Application() {

    companion object {
        private const val TAG = "PrinterApplication"
        private val OCR_FILE_TTL_MILLIS = TimeUnit.DAYS.toMillis(1)
    }

    /**
     * Manual Dependency Injection container (tanpa Hilt/KSP agar build stabil).
     * Berisi instance tunggal service yang dibagikan ke semua Activity & ViewModel.
     */
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        purgeExpiredOcrCapture()
        appContainer = AppContainer(this)
        // Force crisp light theme for thermal POS receipt printing
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        // Material You (Dynamic Color) support for Android 12+ devices
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    private fun purgeExpiredOcrCapture() {
        val file = File(cacheDir, "temp_ocr_capture.jpg")
        if (file.isFile &&
            file.lastModified() < System.currentTimeMillis() - OCR_FILE_TTL_MILLIS &&
            !file.delete()
        ) {
            Log.w(TAG, "Unable to purge an expired temporary image")
        }
    }
}

package com.netraprint.app

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Production-grade Native Android PrintService for NetraPrint.
 *
 * Integrates NetraPrint directly with the Android System Print Spooler (`com.android.bips`),
 * allowing native printing from Chrome, Shopee (STD SPX, etc.), Tokopedia, WhatsApp,
 * PDF viewers, and all standard Android apps.
 */
class NetraPrintService : PrintService() {

    companion object {
        const val TAG = "NetraPrintService"
        const val ACTION_SYSTEM_PRINT_JOB = "com.netraprint.app.ACTION_SYSTEM_PRINT_JOB"
        const val EXTRA_PDF_PATH = "com.netraprint.app.EXTRA_PDF_PATH"
        const val EXTRA_PRINT_COPIES = "com.netraprint.app.EXTRA_PRINT_COPIES"
        const val EXTRA_TARGET_PRINTER_ID = "com.netraprint.app.EXTRA_TARGET_PRINTER_ID"
        private val TEMP_PDF_TTL_MILLIS = TimeUnit.DAYS.toMillis(7)
    }

    override fun onCreate() {
        super.onCreate()
        purgeExpiredPrintFiles()
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {
            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                publishPrinters()
            }

            override fun onStopPrinterDiscovery() {
                // Discovery stopped by Android OS
            }

            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
                publishPrinters()
            }

            override fun onStartPrinterStateTracking(printerId: PrinterId) {
                publishPrinters()
            }

            override fun onStopPrinterStateTracking(printerId: PrinterId) {
                // Tracking stopped
            }

            override fun onDestroy() {
                // Cleanup session
            }

            private fun publishPrinters() {
                val printers = mutableListOf<PrinterInfo>()
                val localized = AppLocaleContext.wrap(this@NetraPrintService)
                val prefs = SecurePreferencesHelper.getPreferences(this@NetraPrintService)
                val defaultName = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_NAME, "") ?: ""
                val printWidth = prefs.getInt(SettingsActivity.KEY_PRINT_WIDTH, 384)
                val is80Default = (printWidth == 576)

                val mediaSize58 = PrintAttributes.MediaSize("THERMAL_58MM", localized.getString(R.string.kt_media_thermal_58), 2280, 11000)
                val mediaSize80 = PrintAttributes.MediaSize("THERMAL_80MM", localized.getString(R.string.kt_media_thermal_80), 3150, 11000)
                val mediaSizeCustom = PrintAttributes.MediaSize("CUSTOM_LABEL", localized.getString(R.string.kt_media_thermal_custom), 100, 400)
                val resolution203 = PrintAttributes.Resolution(
                    "203DPI",
                    localized.getString(R.string.kt_resolution_thermal_203),
                    203,
                    203
                )
                val resolutionDefault = PrintAttributes.Resolution(
                    "DEFAULT_RES",
                    localized.getString(R.string.kt_resolution_default_150),
                    150,
                    150
                )
                val resolution300 = PrintAttributes.Resolution(
                    "300DPI",
                    localized.getString(R.string.kt_resolution_thermal_300),
                    300,
                    300
                )

                fun buildCapabilities(printerId: PrinterId): PrinterCapabilitiesInfo {
                    return PrinterCapabilitiesInfo.Builder(printerId).apply {
                        addMediaSize(mediaSize58, !is80Default)
                        addMediaSize(mediaSize80, is80Default)
                        addMediaSize(PrintAttributes.MediaSize.ISO_A6, false) // Shopee STD SPX & shipping labels
                        addMediaSize(PrintAttributes.MediaSize.NA_INDEX_4X6, false) // 100x150mm standard shipping labels
                        addMediaSize(PrintAttributes.MediaSize.ISO_A5, false)
                        addMediaSize(PrintAttributes.MediaSize.ISO_A4, false)
                        addMediaSize(PrintAttributes.MediaSize.ISO_A7, false)
                        addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
                        addMediaSize(mediaSizeCustom, false)
                        addResolution(resolution203, true)
                        addResolution(resolutionDefault, false)
                        addResolution(resolution300, false)
                        setColorModes(
                            PrintAttributes.COLOR_MODE_MONOCHROME or PrintAttributes.COLOR_MODE_COLOR,
                            PrintAttributes.COLOR_MODE_MONOCHROME
                        )
                        setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    }.build()
                }

                // 1. Primary Virtual Thermal Service Entry
                val mainPrinterId = generatePrinterId("netraprint_thermal_main")
                val mainCaps = buildCapabilities(mainPrinterId)

                val mainDisplayName = if (defaultName.isNotBlank()) {
                    localized.getString(R.string.kt_system_printer_named, defaultName)
                } else {
                    localized.getString(R.string.kt_system_printer_name)
                }
                val mainPrinter = PrinterInfo.Builder(mainPrinterId, mainDisplayName, PrinterInfo.STATUS_IDLE)
                    .setDescription(localized.getString(R.string.kt_system_printer_description))
                    .setCapabilities(mainCaps)
                    .build()
                printers.add(mainPrinter)

                // 2. Discover Paired Bluetooth Printers
                try {
                    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val bluetoothAdapter = bluetoothManager?.adapter
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                        val bonded = bluetoothAdapter.bondedDevices
                        bonded?.forEach { device ->
                            val localId = "bt_${device.address.replace(":", "_")}"
                            val btPrinterId = generatePrinterId(localId)
                            val btCaps = buildCapabilities(btPrinterId)

                            val dName = try { device.name ?: device.address } catch (_: Exception) { device.address }
                            val btPrinter = PrinterInfo.Builder(
                                btPrinterId,
                                localized.getString(R.string.kt_system_printer_bluetooth_name, dName),
                                PrinterInfo.STATUS_IDLE
                            )
                                .setDescription(localized.getString(R.string.kt_system_printer_bluetooth_description))
                                .setCapabilities(btCaps)
                                .build()
                            printers.add(btPrinter)
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Bluetooth printer discovery permission was denied")
                } catch (e: Exception) {
                    Log.w(TAG, "Bluetooth printer discovery failed")
                }

                // 3. Saved Wi-Fi Printer (if configured)
                val wifiIp = prefs.getString(SettingsActivity.KEY_WIFI_IP, "") ?: ""
                if (wifiIp.isNotBlank()) {
                    val wifiPort = prefs.getString(SettingsActivity.KEY_WIFI_PORT, "9100") ?: "9100"
                    val wifiPrinterId = generatePrinterId("wifi_${wifiIp.replace(".", "_")}")
                    val wifiCaps = buildCapabilities(wifiPrinterId)

                    val wifiPrinter = PrinterInfo.Builder(
                        wifiPrinterId,
                        localized.getString(R.string.kt_system_printer_wifi_name, wifiIp, wifiPort),
                        PrinterInfo.STATUS_IDLE
                    )
                        .setDescription(localized.getString(R.string.kt_system_printer_wifi_description, wifiIp, wifiPort))
                        .setCapabilities(wifiCaps)
                        .build()
                    printers.add(wifiPrinter)
                }

                addPrinters(printers)
            }
        }
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        if (!printJob.isQueued) return

        if (!printJob.start()) {
            Log.e(TAG, "Unable to start Android system print job")
            return
        }

        var tempPdf: File? = null
        try {
            val localized = AppLocaleContext.wrap(this)
            val pfd = printJob.document?.data
            if (pfd == null) {
                printJob.fail(localized.getString(R.string.kt_system_print_empty))
                return
            }

            val createdPdf = File.createTempFile("sys_print_", ".pdf", cacheDir)
            tempPdf = createdPdf
            FileInputStream(pfd.fileDescriptor).use { input ->
                FileOutputStream(createdPdf).use { output ->
                    input.copyTo(output)
                }
            }

            val copies = printJob.info.copies
            val targetPrinterId = printJob.info.printerId?.localId ?: ""

            // Intent for MainActivity
            val intent = Intent(this, MainActivity::class.java).apply {
                action = ACTION_SYSTEM_PRINT_JOB
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_PDF_PATH, createdPdf.absolutePath)
                putExtra(EXTRA_PRINT_COPIES, copies)
                putExtra(EXTRA_TARGET_PRINTER_ID, targetPrinterId)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                (System.currentTimeMillis() % 100000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 1. Try launching with Background Activity Launch (BAL) exemption
            var directLaunchSuccess = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic()
                    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pendingIntent.send(this, 0, null, null, null, null, options.toBundle())
                    directLaunchSuccess = true
                } else {
                    startActivity(intent)
                    directLaunchSuccess = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct system print handoff was unavailable")
            }

            // 2. Also create/show high-priority Heads-up Notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channelId = "netraprint_system_print"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
                val channel = NotificationChannel(
                    channelId,
                    localized.getString(R.string.kt_system_print_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = localized.getString(R.string.kt_system_print_channel_description)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            if (notificationManager != null) {
                val notification = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(localized.getString(R.string.kt_system_print_received, copies))
                    .setContentText(localized.getString(R.string.kt_system_print_tap_to_continue))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setContentIntent(pendingIntent)
                    .setFullScreenIntent(pendingIntent, true)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(2001, notification)
            }

            // Complete the system print job so the Android print notification clears
            printJob.complete()
            Log.i(TAG, "Android system print job handed off (direct launch: $directLaunchSuccess)")
        } catch (e: Exception) {
            tempPdf?.let { file ->
                if (file.exists() && !file.delete()) {
                    Log.w(TAG, "Unable to remove a failed system print cache file")
                }
            }
            Log.e(TAG, "Unable to process Android system print job")
            printJob.fail(AppLocaleContext.wrap(this).getString(R.string.kt_system_print_processing_failed))
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        if (printJob.isStarted) {
            printJob.cancel()
        }
    }

    private fun purgeExpiredPrintFiles() {
        val cutoff = System.currentTimeMillis() - TEMP_PDF_TTL_MILLIS
        cacheDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("sys_print_") && it.extension == "pdf" }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { file ->
                if (!file.delete()) {
                    Log.w(TAG, "Unable to purge an expired system print cache file")
                }
            }
    }
}

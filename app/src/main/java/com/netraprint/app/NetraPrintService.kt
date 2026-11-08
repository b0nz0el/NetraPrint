package com.netraprint.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
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

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        purgeExpiredPrintFiles()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
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
                val defaultName = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_NAME, "")?.trim() ?: ""
                val defaultAddress = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, "")?.trim() ?: ""
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

                // 1. Discover Paired Bluetooth Printers (Clean, Deduplicated, Filtered)
                val distinctDevices = mutableListOf<BluetoothDevice>()
                try {
                    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val bluetoothAdapter = bluetoothManager?.adapter
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                        val bonded = bluetoothAdapter.bondedDevices
                        if (!bonded.isNullOrEmpty()) {
                            // Filter out non-printers (audio, phone, wearable)
                            val candidates = bonded.filter { device ->
                                val majorClass = try { device.bluetoothClass?.majorDeviceClass } catch (_: Exception) { null }
                                majorClass != android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO &&
                                majorClass != android.bluetooth.BluetoothClass.Device.Major.PHONE &&
                                majorClass != android.bluetooth.BluetoothClass.Device.Major.WEARABLE
                            }

                            val seenNames = mutableSetOf<String>()

                            // Priority 1: User's saved default printer
                            val defaultDevice = candidates.find { device ->
                                (defaultAddress.isNotBlank() && device.address.equals(defaultAddress, ignoreCase = true)) ||
                                (defaultName.isNotBlank() && (device.name ?: "").equals(defaultName, ignoreCase = true))
                            }
                            if (defaultDevice != null) {
                                distinctDevices.add(defaultDevice)
                                val dName = try { defaultDevice.name ?: defaultDevice.address } catch (_: Exception) { defaultDevice.address }
                                seenNames.add(dName.trim().lowercase())
                            }

                            // Priority 2: Other distinct printers
                            for (device in candidates) {
                                if (defaultDevice != null && device.address.equals(defaultDevice.address, ignoreCase = true)) {
                                    continue
                                }
                                val dName = try { device.name ?: device.address } catch (_: Exception) { device.address }
                                val nameKey = dName.trim().lowercase()
                                if (!seenNames.contains(nameKey)) {
                                    seenNames.add(nameKey)
                                    distinctDevices.add(device)
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Bluetooth printer discovery permission was denied")
                } catch (e: Exception) {
                    Log.w(TAG, "Bluetooth printer discovery failed")
                }

                for (device in distinctDevices) {
                    val localId = "bt_${device.address.replace(":", "_")}"
                    val btPrinterId = generatePrinterId(localId)
                    val btCaps = buildCapabilities(btPrinterId)

                    val dName = try { device.name ?: device.address } catch (_: Exception) { device.address }
                    val btPrinter = PrinterInfo.Builder(
                        btPrinterId,
                        localized.getString(R.string.kt_system_printer_named, dName),
                        PrinterInfo.STATUS_IDLE
                    )
                        .setDescription(localized.getString(R.string.kt_system_printer_description))
                        .setCapabilities(btCaps)
                        .build()
                    printers.add(btPrinter)
                }

                // 2. Saved Wi-Fi Printer (if configured)
                val wifiIp = prefs.getString(SettingsActivity.KEY_WIFI_IP, "")?.trim() ?: ""
                if (wifiIp.isNotBlank()) {
                    val wifiPort = prefs.getString(SettingsActivity.KEY_WIFI_PORT, "9100")?.trim() ?: "9100"
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

                // 3. Fallback virtual entry ONLY if no Bluetooth or Wi-Fi printers are available
                if (printers.isEmpty()) {
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

        val localized = AppLocaleContext.wrap(this)
        val pfd = printJob.document?.data
        if (pfd == null) {
            printJob.fail(localized.getString(R.string.kt_system_print_empty))
            return
        }

        val prefs = SecurePreferencesHelper.getPreferences(this)
        val copies = printJob.info.copies.coerceAtLeast(1)
        val targetPrinterId = printJob.info.printerId?.localId ?: ""
        val mediaSize = printJob.info.attributes?.mediaSize

        // Save PDF to temp file for seekable PdfRenderer access
        val tempPdf: File
        try {
            tempPdf = File.createTempFile("sys_print_", ".pdf", cacheDir)
            FileInputStream(pfd.fileDescriptor).use { input ->
                FileOutputStream(tempPdf).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy system print PDF stream", e)
            printJob.fail(localized.getString(R.string.kt_system_print_processing_failed))
            return
        }

        val appContainer = (application as? PrinterApplication)?.appContainer ?: AppContainer(this)
        val bluetoothService = appContainer.bluetoothService
        val spoolerManager = appContainer.printSpoolerManager

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        val bondedDevices = try { bluetoothAdapter?.bondedDevices?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }

        var isWifi = false
        var wifiIp = ""
        var wifiPort = 9100
        var targetDevice: BluetoothDevice? = null
        var targetPrinterDisplayName = ""

        if (targetPrinterId.startsWith("wifi_")) {
            isWifi = true
            wifiIp = targetPrinterId.removePrefix("wifi_").replace("_", ".")
            wifiPort = prefs.getString(SettingsActivity.KEY_WIFI_PORT, "9100")?.toIntOrNull() ?: 9100
            targetPrinterDisplayName = "Wi-Fi ($wifiIp:$wifiPort)"
        } else if (targetPrinterId.startsWith("bt_")) {
            val mac = targetPrinterId.removePrefix("bt_").replace("_", ":")
            targetDevice = bondedDevices.find { it.address.equals(mac, ignoreCase = true) }
            targetPrinterDisplayName = targetDevice?.let { bluetoothService.getDeviceName(it) ?: it.address } ?: mac
        } else {
            // "netraprint_thermal_main" or fallback: check saved default printer
            val defaultAddress = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, "") ?: ""
            val defaultName = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_NAME, "") ?: ""

            if (defaultAddress.isNotBlank()) {
                targetDevice = bondedDevices.find { it.address.equals(defaultAddress, ignoreCase = true) }
            }
            if (targetDevice == null && defaultName.isNotBlank()) {
                targetDevice = bondedDevices.find {
                    val dName = try { it.name } catch (_: Exception) { null }
                    dName.equals(defaultName, ignoreCase = true)
                }
            }
            if (targetDevice == null && bondedDevices.size == 1) {
                targetDevice = bondedDevices.first()
            }
            targetPrinterDisplayName = targetDevice?.let { bluetoothService.getDeviceName(it) ?: it.address }
                ?: if (defaultName.isNotBlank()) defaultName else localized.getString(R.string.kt_system_printer_name)
        }

        if (!isWifi && targetDevice == null) {
            Log.w(TAG, "No target printer found for system print job")
            printJob.fail(localized.getString(R.string.kt_system_print_no_printer))
            showNotification(
                notificationId = 2002,
                title = localized.getString(R.string.kt_system_print_failed_title, targetPrinterDisplayName),
                content = localized.getString(R.string.kt_system_print_no_printer),
                isError = true,
                pdfFile = tempPdf,
                copies = copies,
                targetPrinterId = targetPrinterId
            )
            return
        }

        // Keep default printer updated if resolved
        if (targetDevice != null) {
            prefs.edit()
                .putString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, targetDevice.address)
                .putString(SettingsActivity.KEY_DEFAULT_PRINTER_NAME, targetPrinterDisplayName)
                .apply()
        }

        val configWidth = prefs.getInt(SettingsActivity.KEY_PRINT_WIDTH, 384)
        val printWidth = when {
            mediaSize?.id == "THERMAL_80MM" || mediaSize == PrintAttributes.MediaSize.ISO_A6 || mediaSize == PrintAttributes.MediaSize.NA_INDEX_4X6 -> 576
            mediaSize?.id == "THERMAL_58MM" -> 384
            else -> configWidth
        }
        val autoCut = prefs.getBoolean(SettingsActivity.KEY_AUTO_CUT, true)
        val ditherMode = DitheringEngine.DitherMode.FLOYD_STEINBERG

        serviceScope.launch(Dispatchers.IO) {
            var pfdSeekable: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                showNotification(
                    notificationId = 2001,
                    title = localized.getString(R.string.kt_system_print_printing, targetPrinterDisplayName),
                    content = localized.getString(R.string.kt_system_print_received, copies),
                    isOngoing = true
                )

                pfdSeekable = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfdSeekable)
                val totalPages = renderer.pageCount
                if (totalPages == 0) {
                    dismissNotification(2001)
                    printJob.fail(localized.getString(R.string.kt_system_print_empty))
                    return@launch
                }

                val pagesItems = mutableListOf<BluetoothService.PrintPageItem>()
                for (pageIndex in 0 until totalPages) {
                    var page: PdfRenderer.Page? = null
                    var bitmap: Bitmap? = null
                    try {
                        val openedPage = renderer.openPage(pageIndex)
                        page = openedPage
                        val renderWidth = (openedPage.width * 2).coerceAtLeast(printWidth)
                        val renderHeight = (openedPage.height * 2).coerceAtLeast(1)
                        val renderedBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                        bitmap = renderedBitmap
                        val canvas = Canvas(renderedBitmap)
                        canvas.drawColor(Color.WHITE)
                        openedPage.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                        val pageRaster = bluetoothService.createImageBytes(
                            bitmap = renderedBitmap,
                            customWidth = printWidth,
                            autoCut = (pageIndex == totalPages - 1) && autoCut,
                            ditherMode = ditherMode
                        )
                        pagesItems.add(BluetoothService.PrintPageItem(pageIndex + 1, pageRaster))
                    } finally {
                        page?.close()
                        bitmap?.recycle()
                    }
                }

                val printItems = if (copies <= 1) {
                    pagesItems
                } else {
                    val expanded = mutableListOf<BluetoothService.PrintPageItem>()
                    for (c in 1..copies) {
                        for (item in pagesItems) {
                            expanded.add(BluetoothService.PrintPageItem(item.pageNumber, item.bytes))
                        }
                    }
                    expanded
                }

                val printResult = if (isWifi) {
                    if (printItems.size == 1) {
                        bluetoothService.printDataWifi(wifiIp, wifiPort, printItems[0].bytes)
                    } else {
                        bluetoothService.printMultiplePagesWifi(wifiIp, wifiPort, printItems) { _, _, _ -> }
                    }
                } else {
                    if (printItems.size == 1) {
                        bluetoothService.printData(targetDevice!!, printItems[0].bytes)
                    } else {
                        bluetoothService.printMultiplePages(targetDevice!!, printItems) { _, _, _ -> }
                    }
                }

                dismissNotification(2001)

                if (printResult.isSuccess) {
                    printJob.complete()
                    showNotification(
                        notificationId = 2003,
                        title = localized.getString(R.string.kt_system_print_success, targetPrinterDisplayName),
                        content = localized.getString(R.string.kt_system_print_success_body, totalPages, copies),
                        autoCancel = true
                    )
                    if (tempPdf.exists()) {
                        tempPdf.delete()
                    }
                    Log.i(TAG, "Direct system print succeeded for $targetPrinterDisplayName")
                } else {
                    try {
                        val combinedStream = ByteArrayOutputStream()
                        for (item in printItems) {
                            combinedStream.write(item.bytes)
                        }
                        val combinedBytes = combinedStream.toByteArray()
                        val jobTitle = printJob.document?.info?.name?.ifBlank { null }
                            ?: localized.getString(R.string.kt_job_pdf)
                        spoolerManager.enqueueData(
                            title = jobTitle,
                            printType = "PDF",
                            data = combinedBytes,
                            copies = copies,
                            targetDeviceAddress = targetDevice?.address,
                            targetWifiIp = if (isWifi) wifiIp else null,
                            targetWifiPort = wifiPort
                        )
                    } catch (spoolError: Exception) {
                        Log.e(TAG, "Failed to enqueue failed system print to offline spooler", spoolError)
                    }

                    printJob.fail(printResult.exceptionOrNull()?.message ?: localized.getString(R.string.kt_system_print_processing_failed))
                    showNotification(
                        notificationId = 2002,
                        title = localized.getString(R.string.kt_system_print_failed_title, targetPrinterDisplayName),
                        content = localized.getString(R.string.kt_system_print_failed_spooled),
                        isError = true,
                        pdfFile = tempPdf,
                        copies = copies,
                        targetPrinterId = targetPrinterId
                    )
                    Log.w(TAG, "Direct system print failed, job enqueued to offline spooler")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing direct system print", e)
                dismissNotification(2001)
                printJob.fail(localized.getString(R.string.kt_system_print_processing_failed))
                showNotification(
                    notificationId = 2002,
                    title = localized.getString(R.string.kt_system_print_failed_title, targetPrinterDisplayName),
                    content = localized.getString(R.string.kt_system_print_processing_failed),
                    isError = true,
                    pdfFile = tempPdf,
                    copies = copies,
                    targetPrinterId = targetPrinterId
                )
            } finally {
                try { renderer?.close() } catch (_: Exception) {}
                try { pfdSeekable?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun showNotification(
        notificationId: Int,
        title: String,
        content: String,
        isOngoing: Boolean = false,
        isError: Boolean = false,
        autoCancel: Boolean = true,
        pdfFile: File? = null,
        copies: Int = 1,
        targetPrinterId: String = ""
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channelId = "netraprint_system_print"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val localized = AppLocaleContext.wrap(this)
            val channel = NotificationChannel(
                channelId,
                localized.getString(R.string.kt_system_print_channel_name),
                if (isOngoing) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = localized.getString(R.string.kt_system_print_channel_description)
                enableVibration(!isOngoing)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SYSTEM_PRINT_JOB
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (pdfFile != null) {
                putExtra(EXTRA_PDF_PATH, pdfFile.absolutePath)
                putExtra(EXTRA_PRINT_COPIES, copies)
                putExtra(EXTRA_TARGET_PRINTER_ID, targetPrinterId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(isOngoing)
            .setAutoCancel(autoCancel)
            .setContentIntent(pendingIntent)

        if (isError) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
        } else if (isOngoing) {
            builder.setProgress(0, 0, true)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun dismissNotification(notificationId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(notificationId)
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

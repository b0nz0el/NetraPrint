package com.netraprint.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BluetoothService(private val context: Context) {

    private val localizedContext: Context
        get() = AppLocaleContext.wrap(context)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    companion object {
        // Standard SPP (Serial Port Profile) UUID for Bluetooth Thermal Printers
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Default Raw ESC/POS Wi-Fi Thermal Printer Port
        const val DEFAULT_WIFI_PORT = 9100

        // ESC/POS Commands
        val CMD_INIT: ByteArray = byteArrayOf(0x1B, 0x40)                  // Initialize printer
        val CMD_ALIGN_LEFT: ByteArray = byteArrayOf(0x1B, 0x61, 0x00)     // Align Left
        val CMD_ALIGN_CENTER: ByteArray = byteArrayOf(0x1B, 0x61, 0x01)   // Align Center
        val CMD_ALIGN_RIGHT: ByteArray = byteArrayOf(0x1B, 0x61, 0x02)    // Align Right
        val CMD_BOLD_ON: ByteArray = byteArrayOf(0x1B, 0x45, 0x01)        // Bold ON
        val CMD_BOLD_OFF: ByteArray = byteArrayOf(0x1B, 0x45, 0x00)       // Bold OFF
        val CMD_DOUBLE_SIZE: ByteArray = byteArrayOf(0x1D, 0x21, 0x11)    // 2x Width & Height
        val CMD_NORMAL_SIZE: ByteArray = byteArrayOf(0x1D, 0x21, 0x00)    // 1x Normal size
        val CMD_FEED_3_LINES: ByteArray = byteArrayOf(0x1B, 0x64, 0x03)   // Feed 3 lines
        val CMD_CUT_PAPER: ByteArray = byteArrayOf(0x1D, 0x56, 0x42, 0x00)// Partial cut paper
        val CMD_FONT_A: ByteArray = byteArrayOf(0x1B, 0x4D, 0x00)         // Standard Font A
        val CMD_FONT_B: ByteArray = byteArrayOf(0x1B, 0x4D, 0x01)         // Condensed Font B
    }

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            android.util.Log.e("BluetoothService", "Permission denied while reading paired printer devices")
            emptyList()
        } catch (e: Exception) {
            android.util.Log.e("BluetoothService", "Unable to read paired printer devices")
            emptyList()
        }
    }

    fun getDeviceName(device: BluetoothDevice): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return try {
            device.name
        } catch (e: SecurityException) {
            android.util.Log.w("BluetoothService", "Permission denied while reading printer name", e)
            null
        }
    }

    /**
     * Connects to Bluetooth printer, sends raw bytes, and closes the socket.
     * Runs asynchronously on Dispatchers.IO.
     */
    @SuppressLint("MissingPermission")
    suspend fun printData(device: BluetoothDevice, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        try {
            // Cancel discovery to avoid slow connection
            bluetoothAdapter?.cancelDiscovery()

            socket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                // Fallback for some printers requiring insecure socket
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                method.invoke(device, 1) as BluetoothSocket
            }

            socket.connect()
            outputStream = socket.outputStream

            // Chunked streaming to prevent Bluetooth serial buffer overrun on thermal printers
            val chunkSize = 1024
            if (data.size <= chunkSize) {
                outputStream.write(data)
                outputStream.flush()
            } else {
                var offset = 0
                while (offset < data.size) {
                    val length = (data.size - offset).coerceAtMost(chunkSize)
                    outputStream.write(data, offset, length)
                    outputStream.flush()
                    offset += length
                    if (offset < data.size) {
                        Thread.sleep(25) // Micro-pause to allow thermal printer buffer processing
                    }
                }
            }

            // Dynamic drain delay: grant adequate mechanical printing time before closing RFCOMM socket
            // Minimum 800ms, plus ~50ms per 1KB of data, capped at 4000ms
            val drainDelayMs = (800L + (data.size / 1024) * 50L).coerceIn(800L, 4000L)
            Thread.sleep(drainDelayMs)

            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: IOException) {
            Result.failure(Exception(localizedContext.getString(R.string.kt_print_bluetooth_connection_failed)))
        } catch (e: Exception) {
            Result.failure(Exception(localizedContext.getString(R.string.kt_print_bluetooth_failed)))
        } finally {
            try {
                outputStream?.close()
            } catch (_: Exception) {}
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    data class PrintPageItem(val pageNumber: Int, val bytes: ByteArray)

    /**
     * Connects to Bluetooth printer once, prints selected pages with a delay between them,
     * reporting progress with actual PDF page number, and closes the socket when complete.
     */
    @SuppressLint("MissingPermission")
    suspend fun printMultiplePages(
        device: BluetoothDevice,
        pages: List<PrintPageItem>,
        delayBetweenPagesMs: Long = 500,
        onProgress: suspend (currentStep: Int, totalSteps: Int, actualPdfPage: Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        try {
            bluetoothAdapter?.cancelDiscovery()

            socket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                method.invoke(device, 1) as BluetoothSocket
            }

            socket.connect()
            outputStream = socket.outputStream

            val total = pages.size
            for ((index, item) in pages.withIndex()) {
                val currentStep = index + 1
                onProgress(currentStep, total, item.pageNumber)

                outputStream.write(item.bytes)
                outputStream.flush()

                if (delayBetweenPagesMs > 0 && currentStep < total) {
                    Thread.sleep(delayBetweenPagesMs)
                }
            }

            // Final wait to ensure buffer is printed
            Thread.sleep(500)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: IOException) {
            Result.failure(Exception(localizedContext.getString(R.string.kt_print_bluetooth_connection_failed)))
        } catch (e: Exception) {
            Result.failure(Exception(localizedContext.getString(R.string.kt_print_bluetooth_multipage_failed)))
        } finally {
            try {
                outputStream?.close()
            } catch (_: Exception) {}
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Connects to a Wi-Fi / Network Thermal Printer via TCP/IP (Raw Port 9100),
     * sends raw ESC/POS bytes, and safely closes the socket.
     * Runs asynchronously on Dispatchers.IO.
     */
    suspend fun printDataWifi(ipAddress: String, port: Int = DEFAULT_WIFI_PORT, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var outputStream: OutputStream? = null

        try {
            socket = Socket()
            socket.soTimeout = 6000
            socket.connect(InetSocketAddress(ipAddress.trim(), port), 4000)

            outputStream = socket.getOutputStream()
            val chunkSize = 1024
            if (data.size <= chunkSize) {
                outputStream.write(data)
                outputStream.flush()
            } else {
                var offset = 0
                while (offset < data.size) {
                    val length = (data.size - offset).coerceAtMost(chunkSize)
                    outputStream.write(data, offset, length)
                    outputStream.flush()
                    offset += length
                    if (offset < data.size) {
                        Thread.sleep(15) // Micro-pause for Wi-Fi printer controller buffer
                    }
                }
            }

            val drainDelayMs = (600L + (data.size / 1024) * 40L).coerceIn(600L, 3000L)
            Thread.sleep(drainDelayMs)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: IOException) {
            Result.failure(Exception(localizedContext.getString(R.string.kt_print_wifi_connection_failed)))
        } catch (e: Exception) {
            Result.failure(Exception(localizedContext.getString(R.string.kt_print_wifi_failed)))
        } finally {
            try {
                outputStream?.close()
            } catch (_: Exception) {}
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Connects to a Wi-Fi / Network Thermal Printer via TCP/IP once, prints multiple pages with a delay,
     * reports progress, and closes the socket when complete.
     */
    suspend fun printMultiplePagesWifi(
        ipAddress: String,
        port: Int = DEFAULT_WIFI_PORT,
        pages: List<PrintPageItem>,
        delayBetweenPagesMs: Long = 500,
        onProgress: suspend (currentStep: Int, totalSteps: Int, actualPdfPage: Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var outputStream: OutputStream? = null

        try {
            socket = Socket()
            socket.soTimeout = 6000
            socket.connect(InetSocketAddress(ipAddress.trim(), port), 4000)

            outputStream = socket.getOutputStream()
            val total = pages.size

            for ((index, item) in pages.withIndex()) {
                val currentStep = index + 1
                onProgress(currentStep, total, item.pageNumber)

                outputStream.write(item.bytes)
                outputStream.flush()

                if (delayBetweenPagesMs > 0 && currentStep < total) {
                    Thread.sleep(delayBetweenPagesMs)
                }
            }

            Thread.sleep(400)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: IOException) {
            Result.failure(Exception(localizedContext.getString(R.string.kt_print_wifi_connection_failed)))
        } catch (e: Exception) {
            Result.failure(Exception(localizedContext.getString(R.string.kt_print_wifi_multipage_failed)))
        } finally {
            try {
                outputStream?.close()
            } catch (_: Exception) {}
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Scans the local network subnet (e.g. 192.168.1.1 to 192.168.1.254) in parallel
     * looking for active thermal printers responding on the specified raw port (default 9100).
     */
    suspend fun scanLocalWifiPrinters(port: Int = DEFAULT_WIFI_PORT): List<String> = withContext(Dispatchers.IO) {
        val localSubnetPrefix = getLocalSubnetPrefix() ?: "192.168.1."
        val activeIps = mutableListOf<String>()

        coroutineScope {
            val deferreds = (1..254).map { host ->
                val testIp = "$localSubnetPrefix$host"
                async {
                    var s: Socket? = null
                    try {
                        s = Socket()
                        s.connect(InetSocketAddress(testIp, port), 180)
                        testIp
                    } catch (_: Exception) {
                        null
                    } finally {
                        try { s?.close() } catch (_: Exception) {}
                    }
                }
            }
            val results = deferreds.awaitAll()
            results.filterNotNullTo(activeIps)
        }

        activeIps.sorted()
    }

    private fun getLocalSubnetPrefix(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address && addr.isSiteLocalAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        val lastDot = hostAddress.lastIndexOf('.')
                        if (lastDot > 0) {
                            return hostAddress.substring(0, lastDot + 1)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Creates ESC/POS bytes for a test receipt to verify connection (Bluetooth or Wi-Fi).
     */
    fun createTestReceiptBytes(connectionInfo: String, printWidthDots: Int = 384, autoCut: Boolean = true): ByteArray {
        val prefs = SecurePreferencesHelper.getPreferences(context)
        val useCustomHeader = prefs.getBoolean("use_custom_header", false)
        val storeName = EscPosSanitizer.sanitize(prefs.getString("store_name", "") ?: "")
        val storeAddress = EscPosSanitizer.sanitize(prefs.getString("store_address", "") ?: "")
        val storeFooter = EscPosSanitizer.sanitize(prefs.getString("store_footer", "") ?: "")

        val charset = Charset.forName("GBK")
        val buffer = ByteArrayOutputStream()

        fun write(bytes: ByteArray) = buffer.write(bytes)
        fun writeLine(text: String = "") = buffer.write((text + "\n").toByteArray(charset))

        val separatorLength = when {
            printWidthDots >= 720 -> 64
            printWidthDots >= 576 -> 48
            else -> 32
        }
        val separator = "-".repeat(separatorLength)

        write(CMD_INIT)

        if (useCustomHeader && storeName.isNotBlank()) {
            write(CMD_ALIGN_CENTER)
            write(CMD_BOLD_ON)
            write(CMD_DOUBLE_SIZE)
            writeLine(storeName)
            write(CMD_NORMAL_SIZE)
            if (storeAddress.isNotBlank()) {
                writeLine(storeAddress)
            }
            write(CMD_BOLD_OFF)
            writeLine(separator)
        }

        write(CMD_ALIGN_CENTER)
        write(CMD_BOLD_ON)
        write(CMD_DOUBLE_SIZE)
        writeLine(localizedContext.getString(R.string.kt_receipt_test_title))
        write(CMD_NORMAL_SIZE)
        write(CMD_BOLD_OFF)
        writeLine(separator)

        write(CMD_ALIGN_LEFT)
        writeLine(localizedContext.getString(R.string.kt_receipt_connection, connectionInfo))
        writeLine(localizedContext.getString(R.string.kt_receipt_width, printWidthDots))
        val receiptLocale = localizedContext.resources.configuration.locales[0] ?: Locale.getDefault()
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", receiptLocale).format(Date())
        writeLine(localizedContext.getString(R.string.kt_receipt_printed_at, now))
        writeLine(separator)

        write(CMD_ALIGN_CENTER)
        write(CMD_BOLD_ON)
        writeLine(localizedContext.getString(R.string.kt_receipt_connection_ready))
        write(CMD_BOLD_OFF)
        writeLine(separator)

        if (useCustomHeader && storeFooter.isNotBlank()) {
            write(CMD_ALIGN_CENTER)
            writeLine(storeFooter)
            writeLine(separator)
        }

        write(CMD_FEED_3_LINES)
        if (autoCut) {
            write(CMD_CUT_PAPER)
        }

        return buffer.toByteArray()
    }

    /**
     * Constructs ESC/POS formatted receipt bytes for standard 58mm/80mm thermal printers.
     */
    fun createShippingLabelBytes(
        penerima: String,
        pengirim: String,
        kurir: String,
        kodeBooking: String,
        isiPaket: String,
        catatan: String,
        autoCut: Boolean = true,
        printWidthDots: Int = 384,
        fontName: String = "Default"
    ): ByteArray {
        val sPenerima = EscPosSanitizer.sanitize(penerima)
        val sPengirim = EscPosSanitizer.sanitize(pengirim)
        val sKurir = EscPosSanitizer.sanitize(kurir)
        val sKodeBooking = EscPosSanitizer.sanitize(kodeBooking)
        val sIsiPaket = EscPosSanitizer.sanitize(isiPaket)
        val sCatatan = EscPosSanitizer.sanitize(catatan)

        val charset = Charset.forName("GBK") // Common fallback supporting ASCII & UTF symbols
        val buffer = ByteArrayOutputStream()

        fun write(bytes: ByteArray) = buffer.write(bytes)
        fun writeText(text: String) = buffer.write(text.toByteArray(charset))
        fun writeLine(text: String = "") {
            buffer.write((text + "\n").toByteArray(charset))
        }

        val separatorLength = when {
            printWidthDots >= 720 -> 64
            printWidthDots >= 576 -> 48
            else -> 32
        }
        val separator = "-".repeat(separatorLength)

        // Initialize printer
        write(CMD_INIT)
        if (fontName.contains("Condensed", ignoreCase = true) || fontName.contains("Font B", ignoreCase = true)) {
            write(CMD_FONT_B)
        } else {
            write(CMD_FONT_A)
        }

        // Store Logo (if saved in filesDir/store_logo.png)
        val logoFile = java.io.File(context.filesDir, "store_logo.png")
        if (logoFile.exists()) {
            try {
                val logoBitmap = android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
                if (logoBitmap != null) {
                    val targetWidth = if (printWidthDots >= 576) 384 else 256
                    val maxLogoHeight = if (printWidthDots >= 576) 140 else 110
                    val logoRaster = DitheringEngine.ditherToEscPosRaster(
                        sourceBitmap = logoBitmap,
                        targetWidth = targetWidth,
                        maxHeight = maxLogoHeight,
                        autoCut = false,
                        mode = DitheringEngine.DitherMode.FLOYD_STEINBERG,
                        includeInitAndFeed = false
                    )
                    write(CMD_ALIGN_CENTER)
                    write(logoRaster)
                    writeLine() // Clean spacing after logo
                }
            } catch (e: Exception) {
                android.util.Log.e("BluetoothService", "Unable to process the configured store logo")
            }
        }

        // Store Header (if configured)
        val prefs = SecurePreferencesHelper.getPreferences(context)
        val useCustomHeader = prefs.getBoolean("use_custom_header", false)
        val storeName = EscPosSanitizer.sanitize(prefs.getString("store_name", "") ?: "")
        val storeAddress = EscPosSanitizer.sanitize(prefs.getString("store_address", "") ?: "")
        val storeFooter = EscPosSanitizer.sanitize(prefs.getString("store_footer", "") ?: "")

        if (useCustomHeader && storeName.isNotBlank()) {
            write(CMD_ALIGN_CENTER)
            write(CMD_BOLD_ON)
            write(CMD_DOUBLE_SIZE)
            writeLine(storeName)
            write(CMD_NORMAL_SIZE)
            if (storeAddress.isNotBlank()) {
                writeLine(storeAddress)
            }
            write(CMD_BOLD_OFF)
            writeLine(separator)
        }

        // Header
        write(CMD_ALIGN_CENTER)
        write(CMD_NORMAL_SIZE)
        write(CMD_BOLD_OFF)
        writeLine(localizedContext.getString(R.string.kt_receipt_brand))
        write(CMD_BOLD_ON)
        write(CMD_NORMAL_SIZE)
        writeLine(localizedContext.getString(R.string.kt_receipt_shipping_label))
        write(CMD_BOLD_OFF)
        writeLine(separator)

        // Courier & Booking Code
        if (sKurir.isNotBlank()) {
            write(CMD_ALIGN_CENTER)
            write(CMD_BOLD_ON)
            writeLine(localizedContext.getString(R.string.kt_receipt_courier, sKurir.uppercase()))
            write(CMD_BOLD_OFF)
        }
        if (sKodeBooking.isNotBlank()) {
            write(CMD_ALIGN_CENTER)
            write(CMD_BOLD_ON)
            writeLine(localizedContext.getString(R.string.kt_receipt_tracking_number, sKodeBooking))
            write(CMD_BOLD_OFF)
        }
        write(CMD_ALIGN_LEFT)
        writeLine(separator)

        // Penerima
        write(CMD_BOLD_ON)
        writeLine(localizedContext.getString(R.string.kt_receipt_recipient))
        write(CMD_BOLD_OFF)
        writeLine(if (sPenerima.isNotBlank()) sPenerima else "-")
        writeLine(separator)

        // Pengirim
        write(CMD_BOLD_ON)
        writeLine(localizedContext.getString(R.string.kt_receipt_sender))
        write(CMD_BOLD_OFF)
        writeLine(if (sPengirim.isNotBlank()) sPengirim else "-")
        writeLine(separator)

        // Isi Paket
        if (sIsiPaket.isNotBlank()) {
            write(CMD_BOLD_ON)
            writeText(localizedContext.getString(R.string.kt_receipt_package_contents))
            write(CMD_BOLD_OFF)
            writeLine(sIsiPaket)
        }

        // Catatan
        if (sCatatan.isNotBlank()) {
            write(CMD_BOLD_ON)
            writeText(localizedContext.getString(R.string.kt_receipt_notes))
            write(CMD_BOLD_OFF)
            writeLine(sCatatan)
        }

        writeLine(separator)
        write(CMD_ALIGN_CENTER)
        writeLine(localizedContext.getString(R.string.kt_receipt_thank_you))
        writeLine()

        if (useCustomHeader && storeFooter.isNotBlank()) {
            write(CMD_ALIGN_CENTER)
            writeLine(storeFooter)
            writeLine(separator)
        }

        // Feed & Cut
        write(CMD_FEED_3_LINES)
        if (autoCut) {
            write(CMD_CUT_PAPER)
        }

        return buffer.toByteArray()
    }

    /**
     * Constructs ESC/POS formatted raw text bytes for thermal printing with input sanitization.
     */
    fun createFreeTextBytes(text: String, autoCut: Boolean = true): ByteArray {
        val sText = EscPosSanitizer.sanitize(text)
        val charset = Charset.forName("GBK")
        val buffer = ByteArrayOutputStream()

        val prefs = SecurePreferencesHelper.getPreferences(context)
        val useCustomHeader = prefs.getBoolean("use_custom_header", false)
        val storeName = EscPosSanitizer.sanitize(prefs.getString("store_name", "") ?: "")
        val storeAddress = EscPosSanitizer.sanitize(prefs.getString("store_address", "") ?: "")
        val storeFooter = EscPosSanitizer.sanitize(prefs.getString("store_footer", "") ?: "")

        buffer.write(CMD_INIT)

        if (useCustomHeader && storeName.isNotBlank()) {
            buffer.write(CMD_ALIGN_CENTER)
            buffer.write(CMD_BOLD_ON)
            buffer.write(CMD_DOUBLE_SIZE)
            buffer.write((storeName + "\n").toByteArray(charset))
            buffer.write(CMD_NORMAL_SIZE)
            if (storeAddress.isNotBlank()) {
                buffer.write((storeAddress + "\n").toByteArray(charset))
            }
            buffer.write(CMD_BOLD_OFF)
            buffer.write("--------------------------------\n".toByteArray(charset))
        }

        buffer.write(CMD_ALIGN_LEFT)
        buffer.write(CMD_NORMAL_SIZE)
        buffer.write(sText.toByteArray(charset))
        buffer.write("\n\n".toByteArray(charset))

        if (useCustomHeader && storeFooter.isNotBlank()) {
            buffer.write("--------------------------------\n".toByteArray(charset))
            buffer.write(CMD_ALIGN_CENTER)
            buffer.write((storeFooter + "\n").toByteArray(charset))
        }

        buffer.write(CMD_FEED_3_LINES)
        if (autoCut) {
            buffer.write(CMD_CUT_PAPER)
        }
        return buffer.toByteArray()
    }

    /**
     * Converts a Bitmap into ESC/POS GS v 0 raster image bytes for thermal printing
     * using the advanced 3-mode DitheringEngine (Floyd-Steinberg, Atkinson, or Sharp Threshold).
     */
    fun createImageBytes(
        bitmap: android.graphics.Bitmap,
        customWidth: Int = 384,
        autoCut: Boolean = true,
        ditherMode: DitheringEngine.DitherMode = DitheringEngine.DitherMode.FLOYD_STEINBERG
    ): ByteArray {
        return DitheringEngine.ditherToEscPosRaster(
            sourceBitmap = bitmap,
            targetWidth = customWidth,
            autoCut = autoCut,
            mode = ditherMode
        )
    }
}

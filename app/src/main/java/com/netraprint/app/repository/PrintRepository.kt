package com.netraprint.app.repository

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.graphics.Bitmap
import com.netraprint.app.BluetoothService
import com.netraprint.app.DitheringEngine
import com.netraprint.app.PrintSpoolerManager

/**
 * Repository yang mengenkapsulasi semua logika pencetakan thermal (ESC/POS),
 * spooler offline, dithering, dan sanitasi.
 *
 * Di-instansiasi sekali oleh [com.netraprint.app.AppContainer] dan dipakai
 * bersama oleh MainActivity (via ViewModel) sehingga semua pihak berbagi
 * satu instance BluetoothService dan PrintSpoolerManager yang sama.
 */
class PrintRepository(
    private val context: Context,
    private val bluetoothService: BluetoothService,
    private val printSpoolerManager: PrintSpoolerManager
) {

    /**
     * Mengembalikan daftar printer Bluetooth yang sudah di-pair.
     */
    fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        return bluetoothService.getPairedDevices()
    }

    /**
     * Mengembalikan apakah Bluetooth didukung oleh perangkat.
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothService.isBluetoothSupported
    }

    /**
     * Mengembalikan apakah Bluetooth sudah diaktifkan.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothService.isBluetoothEnabled
    }

    /**
     * Mengirim data ESC/POS ke printer Bluetooth.
     */
    suspend fun printBluetooth(device: BluetoothDevice, data: ByteArray): Result<Unit> {
        return bluetoothService.printData(device, data)
    }

    /**
     * Mengirim beberapa halaman PDF ke printer Wi-Fi.
     */
    suspend fun printPdfWifi(
        ipAddress: String,
        port: Int,
        pages: List<BluetoothService.PrintPageItem>,
        delayBetweenPagesMs: Long = 500,
        onProgress: suspend (Int, Int, Int) -> Unit
    ): Result<Unit> {
        return bluetoothService.printMultiplePagesWifi(
            ipAddress = ipAddress,
            port = port,
            pages = pages,
            delayBetweenPagesMs = delayBetweenPagesMs,
            onProgress = onProgress
        )
    }

    /**
     * Membuat bytes ESC/POS untuk shipping label (form mode).
     */
    fun createShippingLabelBytes(
        penerima: String,
        pengirim: String,
        kurir: String,
        kodeBooking: String,
        isiPaket: String,
        catatan: String,
        autoCut: Boolean,
        printWidthDots: Int,
        fontName: String
    ): ByteArray {
        return bluetoothService.createShippingLabelBytes(
            penerima = penerima,
            pengirim = pengirim,
            kurir = kurir,
            kodeBooking = kodeBooking,
            isiPaket = isiPaket,
            catatan = catatan,
            autoCut = autoCut,
            printWidthDots = printWidthDots,
            fontName = fontName
        )
    }

    /**
     * Membuat bytes ESC/POS untuk teks bebas (free text mode).
     */
    fun createFreeTextBytes(text: String, autoCut: Boolean = true): ByteArray {
        return bluetoothService.createFreeTextBytes(text, autoCut)
    }

    /**
     * Membuat bytes ESC/POS untuk gambar (image mode) dengan dithering.
     */
    fun createImageBytes(
        bitmap: Bitmap,
        customWidth: Int = 384,
        autoCut: Boolean = true,
        ditherMode: DitheringEngine.DitherMode = DitheringEngine.DitherMode.FLOYD_STEINBERG
    ): ByteArray {
        return bluetoothService.createImageBytes(
            bitmap = bitmap,
            customWidth = customWidth,
            autoCut = autoCut,
            ditherMode = ditherMode
        )
    }

    /**
     * Menambahkan pekerjaan cetak ke antrean offline spooler.
     */
    fun enqueueSpoolJob(
        title: String,
        printType: String,
        data: ByteArray,
        copies: Int = 1,
        targetDeviceAddress: String? = null,
        targetWifiIp: String? = null,
        targetWifiPort: Int = 9100
    ) {
        printSpoolerManager.enqueueData(
            title = title,
            printType = printType,
            data = data,
            copies = copies,
            targetDeviceAddress = targetDeviceAddress,
            targetWifiIp = targetWifiIp,
            targetWifiPort = targetWifiPort
        )
    }

    /**
     * Mengembalikan jumlah pekerjaan dalam antrean spooler.
     */
    fun getSpoolerQueueCount(): Int {
        return printSpoolerManager.getQueueCount()
    }

    /**
     * Mengembalikan semua pekerjaan dalam antrean spooler.
     */
    fun getAllSpoolerJobs(): List<PrintSpoolerManager.SpoolJob> {
        return printSpoolerManager.getAllJobs()
    }

    /**
     * Melakukan flush antrean spooler secara sequential.
     */
    suspend fun flushSpoolerQueue(
        onProgress: (Int, Int, String) -> Unit
    ): Result<Int> {
        return printSpoolerManager.flushQueue(bluetoothService) { current, total, job ->
            onProgress(current, total, job.title)
        }
    }
}
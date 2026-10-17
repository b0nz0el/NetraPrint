package com.david.printerjtm.viewmodel

import android.bluetooth.BluetoothDevice
import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.david.printerjtm.DitheringEngine
import com.david.printerjtm.PrintSpoolerManager
import com.david.printerjtm.repository.PrintRepository

enum class PrintMode {
    FORM, FREE_TEXT, PDF, IMAGE, PLACEHOLDER
}

/**
 * ViewModel untuk MainActivity. Mengelola state UI dan delegasikan logika
 * pencetakan ke PrintRepository. MainActivity hanya amati LiveData dari sini.
 */
class MainViewModel(private val printRepository: PrintRepository) : ViewModel() {

    // ========== UI State ==========
    val currentMode = MutableLiveData<PrintMode>(PrintMode.FORM)
    val currentDitherMode = MutableLiveData<DitheringEngine.DitherMode>(
        DitheringEngine.DitherMode.FLOYD_STEINBERG
    )
    val currentSelectedFont = MutableLiveData<String>("Default")
    val printWidthDots = MutableLiveData<Int>(384)
    val autoCut = MutableLiveData<Boolean>(true)
    val spoolerQueueCount = MutableLiveData<Int>(0)
    val isBluetoothEnabled = MutableLiveData<Boolean>(false)
    val pairedDevices = MutableLiveData<List<BluetoothDevice>>(emptyList())

    // ========== Actions (dipanggil oleh Activity) ==========

    fun setPrintMode(mode: PrintMode) {
        currentMode.value = mode
    }

    fun setDitherMode(mode: DitheringEngine.DitherMode) {
        currentDitherMode.value = mode
    }

    fun setSelectedFont(font: String) {
        currentSelectedFont.value = font
    }

    fun setPrintWidth(dots: Int) {
        printWidthDots.value = dots
    }

    fun setAutoCut(enabled: Boolean) {
        autoCut.value = enabled
    }

    fun refreshSpoolerCount() {
        spoolerQueueCount.value = printRepository.getSpoolerQueueCount()
    }

    fun refreshPairedDevices() {
        pairedDevices.value = printRepository.getPairedBluetoothDevices()
    }

    fun refreshBluetoothState() {
        isBluetoothEnabled.value = printRepository.isBluetoothEnabled()
    }

    // ========== Printing ==========

    fun createShippingLabelBytes(
        penerima: String,
        pengirim: String,
        kurir: String,
        kodeBooking: String,
        isiPaket: String,
        catatan: String,
        fontName: String
    ): ByteArray {
        return printRepository.createShippingLabelBytes(
            penerima = penerima,
            pengirim = pengirim,
            kurir = kurir,
            kodeBooking = kodeBooking,
            isiPaket = isiPaket,
            catatan = catatan,
            autoCut = autoCut.value ?: true,
            printWidthDots = printWidthDots.value ?: 384,
            fontName = fontName
        )
    }

    fun createFreeTextBytes(text: String): ByteArray {
        return printRepository.createFreeTextBytes(text, autoCut.value ?: true)
    }

    fun createImageBytes(
        bitmap: Bitmap,
        ditherMode: DitheringEngine.DitherMode = DitheringEngine.DitherMode.FLOYD_STEINBERG
    ): ByteArray {
        return printRepository.createImageBytes(
            bitmap = bitmap,
            customWidth = printWidthDots.value ?: 384,
            autoCut = autoCut.value ?: true,
            ditherMode = ditherMode
        )
    }

    fun enqueueSpoolJob(
        title: String,
        printType: String,
        data: ByteArray,
        targetDeviceAddress: String? = null,
        targetWifiIp: String? = null,
        targetWifiPort: Int = 9100
    ) {
        printRepository.enqueueSpoolJob(
            title = title,
            printType = printType,
            data = data,
            targetDeviceAddress = targetDeviceAddress,
            targetWifiIp = targetWifiIp,
            targetWifiPort = targetWifiPort
        )
        refreshSpoolerCount()
    }

    fun getAllSpoolerJobs(): List<PrintSpoolerManager.SpoolJob> {
        return printRepository.getAllSpoolerJobs()
    }

    companion object {
        /**
         * Manual DI factory — menghindari code generation Hilt/KSP agar build stabil.
         */
        fun factory(printRepository: PrintRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
                        "Unknown ViewModel class: " + modelClass.name
                    }
                    return MainViewModel(printRepository) as T
                }
            }
    }
}
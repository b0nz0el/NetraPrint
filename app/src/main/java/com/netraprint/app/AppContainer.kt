package com.netraprint.app

import android.content.Context
import com.netraprint.app.repository.PrintRepository

/**
 * Manual Dependency Injection container.
 *
 * Menggantikan Hilt agar tidak membutuhkan KSP/kapt code generation
 * (risiko inkompatibilitas versi dengan AGP 9). Setiap dependency
 * di-instantiate lazy dan hanya sekali per proses aplikasi.
 *
 * Semua instance memakai applicationContext sehingga aman dari memory leak
 * dan tetap hidup melewati rotasi/perubahan konfigurasi Activity.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val bluetoothService: BluetoothService by lazy {
        BluetoothService(appContext)
    }

    val printSpoolerManager: PrintSpoolerManager by lazy {
        PrintSpoolerManager.getInstance(appContext)
    }

    val printRepository: PrintRepository by lazy {
        PrintRepository(appContext, bluetoothService, printSpoolerManager)
    }

    val printHistoryManager: PrintHistoryManager by lazy {
        PrintHistoryManager.getInstance(appContext)
    }
}
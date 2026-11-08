package com.netraprint.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class QrGeneratorActivity : AppCompatActivity() {

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            permissions[Manifest.permission.BLUETOOTH] != false
        }
        if (granted) {
            printQrCode()
        } else {
            if (!isFinishing && !isDestroyed) {
                StatusBottomSheetDialog(this).showError(
                    title = getString(R.string.kt_dialog_bt_denied_title),
                    message = getString(R.string.kt_bt_denied_msg),
                    actionText = getString(R.string.kt_btn_grant_permission)
                ) {
                    checkAndRequestBluetoothPermissions()
                }
            }
        }
    }

    private fun checkAndRequestBluetoothPermissions(): Boolean {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        return if (permissionsToRequest.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            false
        } else {
            true
        }
    }

    private lateinit var etContent: EditText
    private lateinit var ivPreview: ImageView
    private lateinit var tvHint: TextView
    private lateinit var btnPrint: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var bluetoothService: BluetoothService
    private lateinit var printHistoryManager: PrintHistoryManager

    private var currentQrBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_generator)

        bluetoothService = BluetoothService(this)
        printHistoryManager = (application as? PrinterApplication)?.appContainer?.printHistoryManager ?: PrintHistoryManager.getInstance(applicationContext)

        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayoutQr)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        findViewById<MaterialToolbar>(R.id.toolbarQr).setNavigationOnClickListener {
            finish()
        }

        etContent = findViewById(R.id.etQrContent)
        ivPreview = findViewById(R.id.ivQrPreview)
        tvHint = findViewById(R.id.tvQrHint)
        btnPrint = findViewById(R.id.btnPrintQr)
        btnSave = findViewById(R.id.btnSaveQr)

        findViewById<MaterialButton>(R.id.btnGenerateQr).setOnClickListener {
            generateQrCode()
        }

        etContent.addTextChangedListener {
            generateQrCode()
        }

        btnPrint.setOnClickListener {
            printQrCode()
        }

        btnSave.setOnClickListener {
            saveQrCodeToGallery()
        }

        // Initial default QR content
        etContent.setText(getString(R.string.kt_qr_default_content))
    }

    private fun generateQrCode() {
        val text = etContent.text.toString().trim()
        if (text.isEmpty()) {
            ivPreview.setImageBitmap(null)
            tvHint.setText(R.string.kt_qr_type_to_generate)
            currentQrBitmap = null
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val size = 512
                val hints = mapOf(
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                    EncodeHintType.MARGIN to 1
                )
                val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val pixels = IntArray(width * height)

                for (y in 0 until height) {
                    val offset = y * width
                    for (x in 0 until width) {
                        pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                    }
                }

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

                withContext(Dispatchers.Main) {
                    currentQrBitmap = bitmap
                    ivPreview.setImageBitmap(bitmap)
                    tvHint.text = getString(R.string.kt_qr_ready, text.length)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvHint.text = getString(R.string.kt_qr_generation_failed)
                }
            }
        }
    }

    private fun printQrCode() {
        val bitmap = currentQrBitmap
        if (bitmap == null) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.kt_qr_empty_title))
                .setMessage(getString(R.string.kt_qr_empty_msg))
                .setPositiveButton(getString(R.string.kt_btn_ok), null)
                .show()
            return
        }

        val prefs = SecurePreferencesHelper.getPreferences(this)
        val connType = prefs.getString(SettingsActivity.KEY_CONNECTION_TYPE, "Bluetooth")
        val printWidth = prefs.getInt(SettingsActivity.KEY_PRINT_WIDTH, 384)
        val autoCut = prefs.getBoolean(SettingsActivity.KEY_AUTO_CUT, true)

        if (connType == "Wi-Fi") {
            val ip = prefs.getString(SettingsActivity.KEY_WIFI_IP, "")?.trim() ?: ""
            val port = prefs.getString(SettingsActivity.KEY_WIFI_PORT, "9100")?.trim()?.toIntOrNull() ?: 9100

            if (ip.isBlank()) {
                StatusBottomSheetDialog(this).showError(
                    title = getString(R.string.kt_dialog_wifi_ip_title),
                    message = getString(R.string.kt_dialog_wifi_ip_msg),
                    actionText = getString(R.string.kt_btn_grant_permission)
                ) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                return
            }

            val statusDialog = StatusBottomSheetDialog(this)
            statusDialog.showLoading(
                title = getString(R.string.kt_loading_qr_wifi_title),
                message = getString(R.string.kt_loading_connect_msg, ip, port),
                showProgress = false
            )

            lifecycleScope.launch {
                val printBytes = bluetoothService.createImageBytes(bitmap, printWidth, autoCut)
                try {
                    val result = bluetoothService.printDataWifi(ip, port, printBytes)
                    if (isFinishing || isDestroyed) return@launch
                    statusDialog.dismiss()
                    result.fold(
                        onSuccess = {
                            printHistoryManager.addEntry(
                                title = getString(R.string.kode_qr),
                                printType = "QR",
                                targetName = ip,
                                targetInfo = port.toString(),
                                status = PrintHistoryItem.Status.SUCCESS,
                                copies = 1
                            )
                            StatusBottomSheetDialog(this@QrGeneratorActivity).showSuccess(
                                title = getString(R.string.kt_success_print_done_title),
                                message = getString(R.string.kt_qr_wifi_print_success_msg, ip, port)
                            )
                        },
                        onFailure = {
                            printHistoryManager.addEntry(
                                title = getString(R.string.kode_qr),
                                printType = "QR",
                                targetName = ip,
                                targetInfo = port.toString(),
                                status = PrintHistoryItem.Status.FAILED,
                                copies = 1
                            )
                            StatusBottomSheetDialog(this@QrGeneratorActivity).showError(
                                title = getString(R.string.kt_dialog_wifi_error_title),
                                message = getString(R.string.kt_print_wifi_connection_failed),
                                actionText = getString(R.string.kt_btn_retry),
                                secondaryText = getString(R.string.kt_btn_close)
                            ) {
                                printQrCode()
                            }
                        }
                    )
                } finally {
                    printBytes.fill(0)
                }
            }
            return
        }

        if (!checkAndRequestBluetoothPermissions()) {
            return
        }

        lifecycleScope.launch {
            val pairedDevices = withContext(Dispatchers.IO) {
                bluetoothService.getPairedDevices()
            }

            if (isFinishing || isDestroyed) return@launch

            if (pairedDevices.isEmpty()) {
                StatusBottomSheetDialog(this@QrGeneratorActivity).showError(
                    title = getString(R.string.kt_dialog_no_printer_title),
                    message = getString(R.string.kt_no_paired_msg),
                    actionText = getString(R.string.kt_btn_grant_permission)
                ) {
                    startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                return@launch
            }

            val directPrint = prefs.getBoolean(SettingsActivity.KEY_DIRECT_PRINT, false)
            val defaultAddress = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, "") ?: ""

            fun sendPrint(device: android.bluetooth.BluetoothDevice) {
                lifecycleScope.launch {
                    val statusDialog = StatusBottomSheetDialog(this@QrGeneratorActivity)
                    statusDialog.showLoading(
                        title = getString(R.string.kt_loading_qr_title),
                        message = getString(
                            R.string.kt_loading_connect_device_msg,
                            bluetoothService.getDeviceName(device) ?: getString(R.string.kt_thermal_printer)
                        ),
                        showProgress = false
                    )
                    val printBytes = bluetoothService.createImageBytes(bitmap, printWidth, autoCut)
                    try {
                        val result = withContext(Dispatchers.IO) {
                            bluetoothService.printData(device, printBytes)
                        }
                        val deviceName = bluetoothService.getDeviceName(device) ?: getString(R.string.kt_thermal_printer)
                        if (result.isSuccess) {
                            printHistoryManager.addEntry(
                                title = getString(R.string.kode_qr),
                                printType = "QR",
                                targetName = deviceName,
                                targetInfo = device.address,
                                status = PrintHistoryItem.Status.SUCCESS,
                                copies = 1
                            )
                            statusDialog.showSuccess(
                                title = getString(R.string.kt_success_print_done_title),
                                message = getString(R.string.kt_qr_thermal_print_success_msg)
                            )
                        } else {
                            printHistoryManager.addEntry(
                                title = getString(R.string.kode_qr),
                                printType = "QR",
                                targetName = deviceName,
                                targetInfo = device.address,
                                status = PrintHistoryItem.Status.FAILED,
                                copies = 1
                            )
                            statusDialog.showError(
                                title = getString(R.string.kt_bluetooth_printer_unreachable_title),
                                message = getString(R.string.kt_print_bluetooth_connection_failed),
                                actionText = getString(R.string.kt_btn_retry),
                                secondaryText = getString(R.string.kt_btn_close)
                            ) {
                                sendPrint(device)
                            }
                        }
                    } finally {
                        printBytes.fill(0)
                    }
                }
            }

            if (directPrint && defaultAddress.isNotBlank()) {
                val target = pairedDevices.find { it.address == defaultAddress }
                if (target != null) {
                    sendPrint(target)
                    return@launch
                }
            }

            PrinterPickerBottomSheetDialog(this@QrGeneratorActivity, pairedDevices, defaultAddress, includeAllPrintersOption = false) { _, address ->
                val selected = if (address.isNotBlank()) {
                    pairedDevices.find { it.address == address } ?: pairedDevices.first()
                } else {
                    pairedDevices.first()
                }
                sendPrint(selected)
            }.show()
        }
    }

    private fun saveQrCodeToGallery() {
        val bitmap = currentQrBitmap ?: return
        try {
            val filename = "QRCode_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NetraPrint")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                outputStream?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }

                StatusBottomSheetDialog(this).showSuccess(
                    title = getString(R.string.kt_qr_saved_to_gallery_title),
                    message = getString(R.string.kt_qr_saved_to_gallery_msg)
                )
            }
        } catch (e: Exception) {
            StatusBottomSheetDialog(this).showError(
                title = getString(R.string.kt_dialog_save_fail_title),
                message = getString(R.string.kt_save_image_error_msg)
            )
        }
    }
}

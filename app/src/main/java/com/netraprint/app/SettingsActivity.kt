package com.netraprint.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebStorage
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "netraprint_settings"
        const val LEGACY_PREFS_NAME = "netraprint_settings"
        const val KEY_CONNECTION_TYPE = "connection_type"
        const val KEY_DEFAULT_PRINTER_NAME = "default_printer_name"
        const val KEY_DEFAULT_PRINTER_ADDRESS = "default_printer_address"
        const val KEY_PAPER_SIZE = "paper_size"
        const val KEY_PRINT_WIDTH = "print_width"
        const val KEY_DIRECT_PRINT = "direct_print"
        const val KEY_AUTO_CUT = "auto_cut"
        const val KEY_LANGUAGE = "language"

        // New keys for Wi-Fi, API, Cloud Sync, Custom Header, & Security
        const val KEY_WIFI_IP = "wifi_ip"
        const val KEY_WIFI_PORT = "wifi_port"
        const val KEY_CLOUD_SYNC_KEY = "cloud_sync_key"
        const val KEY_AUTO_CLOUD_SYNC = "auto_cloud_sync"
        const val KEY_LAST_CLOUD_SYNC = "last_cloud_sync"
        const val KEY_USE_CUSTOM_HEADER = "use_custom_header"
        const val KEY_STORE_NAME = "store_name"
        const val KEY_STORE_ADDRESS = "store_address"
        const val KEY_STORE_FOOTER = "store_footer"
        const val KEY_ADMIN_PIN_ENABLED = "admin_pin_enabled"
        const val KEY_ADMIN_PIN_CODE = "admin_pin_code"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var bluetoothService: BluetoothService

    // Konfigurasi Printer
    private lateinit var rgConnectionType: MaterialButtonToggleGroup
    private lateinit var btnConnBluetooth: MaterialButton
    private lateinit var btnConnWifi: MaterialButton
    private lateinit var btnConnUsb: MaterialButton

    private lateinit var layoutPrinterSelector: View
    private lateinit var tvPrinterName: TextView
    private lateinit var btnChangePrinter: MaterialButton

    private lateinit var layoutWifiConfig: LinearLayout
    private lateinit var etWifiIp: TextInputEditText
    private lateinit var etWifiPort: TextInputEditText
    private lateinit var btnScanWifiPrinter: MaterialButton
    private lateinit var btnTestPrint: MaterialButton

    private lateinit var rgPaperSize: MaterialButtonToggleGroup
    private lateinit var btnSize58: MaterialButton
    private lateinit var btnSize80: MaterialButton
    private lateinit var btnSize100: MaterialButton

    private lateinit var tvPrintWidth: TextView
    private lateinit var switchDirectPrint: MaterialSwitch
    private lateinit var switchAutoCut: MaterialSwitch


    // Cloud Sync & Backup
    private lateinit var tvCloudSyncStatus: TextView
    private lateinit var etCloudSyncKey: TextInputEditText
    private lateinit var switchAutoCloudSync: MaterialSwitch
    private lateinit var btnBackupCloud: MaterialButton
    private lateinit var btnRestoreCloud: MaterialButton

    // Bahasa & Profil Struk
    private lateinit var tvLanguage: TextView
    private lateinit var switchUseCustomHeader: MaterialSwitch
    private lateinit var etStoreName: TextInputEditText
    private lateinit var etStoreAddress: TextInputEditText
    private lateinit var etStoreFooter: TextInputEditText

    // Keamanan & Legalitas
    private lateinit var tvSecurityStatus: TextView
    private lateinit var switchAdminPin: MaterialSwitch
    private lateinit var layoutClearCache: View

    private lateinit var btnSaveSettings: MaterialButton

    private var selectedPrinterName = ""
    private var selectedPrinterAddress = ""
    private var currentPrintWidth = 384

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        prefs = SecurePreferencesHelper.getPreferences(this)
        bluetoothService = BluetoothService(this)

        val appBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayoutSettings)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        initViews()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarSettings)
        val versionLabel = findViewById<TextView>(R.id.tvSettingsVersion)
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            versionLabel.text = versionName
        } catch (e: Exception) {
            e.printStackTrace()
        }
        toolbar.setNavigationOnClickListener {
            if (saveSettings()) finish()
        }

        // Konfigurasi Printer
        rgConnectionType = findViewById(R.id.rgConnectionType)
        btnConnBluetooth = findViewById(R.id.btnConnBluetooth)
        btnConnWifi = findViewById(R.id.btnConnWifi)
        btnConnUsb = findViewById(R.id.btnConnUsb)

        layoutPrinterSelector = findViewById(R.id.layoutPrinterSelector)
        tvPrinterName = findViewById(R.id.tvPrinterName)
        btnChangePrinter = findViewById(R.id.btnChangePrinter)

        layoutWifiConfig = findViewById(R.id.layoutWifiConfig)
        etWifiIp = findViewById(R.id.etWifiIp)
        etWifiPort = findViewById(R.id.etWifiPort)
        btnScanWifiPrinter = findViewById(R.id.btnScanWifiPrinter)
        btnTestPrint = findViewById(R.id.btnTestPrint)

        rgPaperSize = findViewById(R.id.rgPaperSize)
        btnSize58 = findViewById(R.id.btnSize58)
        btnSize80 = findViewById(R.id.btnSize80)
        btnSize100 = findViewById(R.id.btnSize100)

        tvPrintWidth = findViewById(R.id.tvPrintWidth)
        switchDirectPrint = findViewById(R.id.switchDirectPrint)
        switchAutoCut = findViewById(R.id.switchAutoCut)


        // Cloud Sync & Backup
        tvCloudSyncStatus = findViewById(R.id.tvCloudSyncStatus)
        etCloudSyncKey = findViewById(R.id.etCloudSyncKey)
        switchAutoCloudSync = findViewById(R.id.switchAutoCloudSync)
        btnBackupCloud = findViewById(R.id.btnBackupCloud)
        btnRestoreCloud = findViewById(R.id.btnRestoreCloud)

        // Bahasa & Profil Struk
        tvLanguage = findViewById(R.id.tvLanguage)
        switchUseCustomHeader = findViewById(R.id.switchUseCustomHeader)
        etStoreName = findViewById(R.id.etStoreName)
        etStoreAddress = findViewById(R.id.etStoreAddress)
        etStoreFooter = findViewById(R.id.etStoreFooter)

        // Keamanan & Legalitas
        tvSecurityStatus = findViewById(R.id.tvSecurityStatus)
        switchAdminPin = findViewById(R.id.switchAdminPin)
        layoutClearCache = findViewById(R.id.layoutClearCache)

        btnSaveSettings = findViewById(R.id.btnSaveSettings)
    }

    private fun loadSettings() {
        val connType = prefs.getString(KEY_CONNECTION_TYPE, "Bluetooth") ?: "Bluetooth"
        if (connType == "Wi-Fi") {
            btnConnWifi.isChecked = true
            layoutWifiConfig.visibility = View.VISIBLE
            layoutPrinterSelector.visibility = View.GONE
        } else {
            btnConnBluetooth.isChecked = true
            layoutWifiConfig.visibility = View.GONE
            layoutPrinterSelector.visibility = View.VISIBLE
        }

        selectedPrinterName = prefs.getString(KEY_DEFAULT_PRINTER_NAME, "") ?: ""
        selectedPrinterAddress = prefs.getString(KEY_DEFAULT_PRINTER_ADDRESS, "") ?: ""
        updatePrinterDisplay()

        etWifiIp.setText(prefs.getString(KEY_WIFI_IP, ""))
        etWifiPort.setText(prefs.getString(KEY_WIFI_PORT, "9100"))

        val paperSize = prefs.getInt(KEY_PAPER_SIZE, 58)
        when (paperSize) {
            80 -> btnSize80.isChecked = true
            100 -> btnSize100.isChecked = true
            else -> btnSize58.isChecked = true
        }

        currentPrintWidth = prefs.getInt(KEY_PRINT_WIDTH, if (paperSize == 80) 576 else if (paperSize == 100) 720 else 384)
        updatePrintWidthDisplay()

        switchDirectPrint.isChecked = prefs.getBoolean(KEY_DIRECT_PRINT, false)
        switchAutoCut.isChecked = prefs.getBoolean(KEY_AUTO_CUT, true)


        // Cloud Sync settings
        val lastSync = prefs.getString(KEY_LAST_CLOUD_SYNC, "")
        if (!lastSync.isNullOrBlank()) {
            tvCloudSyncStatus.text = getString(R.string.kt_cloud_sync_last, lastSync)
        } else {
            tvCloudSyncStatus.setText(R.string.kt_cloud_sync_never)
        }
        etCloudSyncKey.setText(prefs.getString(KEY_CLOUD_SYNC_KEY, ""))
        switchAutoCloudSync.isChecked = prefs.getBoolean(KEY_AUTO_CLOUD_SYNC, false)

        // Bahasa & Profil Toko
        tvLanguage.text = prefs.getString(KEY_LANGUAGE, getString(R.string.bahasa_indonesia))
        switchUseCustomHeader.isChecked = prefs.getBoolean(KEY_USE_CUSTOM_HEADER, false)
        etStoreName.setText(prefs.getString(KEY_STORE_NAME, ""))
        etStoreAddress.setText(prefs.getString(KEY_STORE_ADDRESS, ""))
        etStoreFooter.setText(prefs.getString(KEY_STORE_FOOTER, ""))

        // Keamanan
        val isRooted = RootDetectionHelper.isDeviceRooted()
        tvSecurityStatus.text = getString(
            R.string.kt_security_status,
            getString(if (isRooted) R.string.kt_security_rooted else R.string.kt_security_not_rooted)
        )
        switchAdminPin.isChecked = prefs.getBoolean(KEY_ADMIN_PIN_ENABLED, false)
    }

    private fun setupListeners() {
        rgConnectionType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnConnWifi -> {
                    layoutWifiConfig.visibility = View.VISIBLE
                    layoutPrinterSelector.visibility = View.GONE
                    prefs.edit().putString(KEY_CONNECTION_TYPE, "Wi-Fi").apply()
                }
                R.id.btnConnBluetooth -> {
                    layoutWifiConfig.visibility = View.GONE
                    layoutPrinterSelector.visibility = View.VISIBLE
                    prefs.edit().putString(KEY_CONNECTION_TYPE, "Bluetooth").apply()
                }
                R.id.btnConnUsb -> {
                    showUpcomingConnectionDialog("USB")
                }
            }
        }

        btnScanWifiPrinter.setOnClickListener {
            scanWifiPrinters()
        }

        btnTestPrint.setOnClickListener {
            executeTestPrint()
        }

        rgPaperSize.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val paperSize = when (checkedId) {
                R.id.btnSize80 -> 80
                R.id.btnSize100 -> 100
                else -> 58
            }
            currentPrintWidth = when (paperSize) {
                80 -> 576
                100 -> 720
                else -> 384
            }
            updatePrintWidthDisplay()
            prefs.edit()
                .putInt(KEY_PAPER_SIZE, paperSize)
                .putInt(KEY_PRINT_WIDTH, currentPrintWidth)
                .apply()
        }

        findViewById<View>(R.id.layoutPrintWidth)?.setOnClickListener {
            showCustomWidthDialog()
        }

        layoutPrinterSelector.setOnClickListener {
            showPrinterPickerDialog()
        }

        btnChangePrinter.setOnClickListener {
            showPrinterPickerDialog()
        }

        // Cloud Sync Buttons
        btnBackupCloud.setOnClickListener {
            backupSettingsToClipboard()
        }

        btnRestoreCloud.setOnClickListener {
            showRestoreDialog()
        }

        // Language Dialog
        tvLanguage.setOnClickListener {
            showLanguageDialog()
        }

        // Security PIN toggle
        switchAdminPin.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !prefs.getBoolean(KEY_ADMIN_PIN_ENABLED, false)) {
                promptSetAdminPin()
            } else if (!isChecked && prefs.getBoolean(KEY_ADMIN_PIN_ENABLED, false)) {
                promptVerifyDisableAdminPin()
            }
        }

        // Clear Cache
        layoutClearCache.setOnClickListener {
            clearAppCache()
        }

        // Cek Pembaruan
        findViewById<View>(R.id.layoutCheckUpdate)?.setOnClickListener {
            checkForUpdates()
        }

        // Legal Links
        findViewById<View>(R.id.layoutPrivacyPolicy)?.setOnClickListener {
            val intent = Intent(this, LegalActivity::class.java).apply {
                putExtra(LegalActivity.EXTRA_TYPE, LegalActivity.TYPE_PRIVACY_POLICY)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.layoutTermsOfService)?.setOnClickListener {
            val intent = Intent(this, LegalActivity::class.java).apply {
                putExtra(LegalActivity.EXTRA_TYPE, LegalActivity.TYPE_TERMS_OF_SERVICE)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.layoutOpenSourceLicenses)?.setOnClickListener {
            val intent = Intent(this, LegalActivity::class.java).apply {
                putExtra(LegalActivity.EXTRA_TYPE, LegalActivity.TYPE_OPEN_SOURCE_LICENSES)
            }
            startActivity(intent)
        }

        btnSaveSettings.setOnClickListener {
            if (!saveSettings()) return@setOnClickListener
            StatusBottomSheetDialog(this).showSuccess(
                title = getString(R.string.kt_success_settings_saved_title),
                message = getString(R.string.kt_settings_saved_msg)
            ) {
                finish()
            }
        }
    }

    private fun scanWifiPrinters() {
        val port = etWifiPort.text.toString().toIntOrNull() ?: 9100
        val dialog = StatusBottomSheetDialog(this)
        dialog.showLoading(
            getString(R.string.kt_wifi_scan_title),
            getString(R.string.kt_wifi_scan_message, port)
        )

        lifecycleScope.launch {
            try {
                val foundIps = bluetoothService.scanLocalWifiPrinters(port)
                if (isFinishing || isDestroyed) return@launch

                dialog.dismiss()

                if (foundIps.isEmpty()) {
                    StatusBottomSheetDialog(this@SettingsActivity).showError(
                        title = getString(R.string.kt_wifi_printer_not_found_title),
                        message = getString(R.string.kt_wifi_printer_not_found_message, port),
                        actionText = getString(R.string.kt_btn_close)
                    )
                } else {
                    val ipArray = foundIps.toTypedArray()
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle(getString(R.string.kt_wifi_choose_printer, foundIps.size))
                        .setItems(ipArray) { _, which ->
                            val selectedIp = ipArray[which]
                            etWifiIp.setText(selectedIp)
                            prefs.edit().putString(KEY_WIFI_IP, selectedIp).apply()
                            StatusBottomSheetDialog(this@SettingsActivity).showSuccess(
                                title = getString(R.string.kt_wifi_printer_selected_title),
                                message = getString(R.string.kt_wifi_printer_selected_message, selectedIp)
                            )
                        }
                        .setNegativeButton(getString(R.string.batal), null)
                        .show()
                }
            } catch (_: Exception) {
                dialog.dismiss()
                StatusBottomSheetDialog(this@SettingsActivity).showError(
                    title = getString(R.string.kt_wifi_scan_title),
                    message = getString(R.string.kt_scan_network_failed),
                    actionText = getString(R.string.kt_btn_understood)
                )
            }
        }
    }

    private fun executeTestPrint() {
        val isWifi = btnConnWifi.isChecked
        val autoCut = switchAutoCut.isChecked

        if (isWifi) {
            val ip = etWifiIp.text.toString().trim()
            val port = etWifiPort.text.toString().toIntOrNull() ?: 9100
            if (ip.isBlank()) {
                StatusBottomSheetDialog(this).showError(
                    title = getString(R.string.kt_wifi_ip_missing_title),
                    message = getString(R.string.kt_wifi_ip_missing_message),
                    actionText = getString(R.string.kt_btn_understood)
                )
                return
            }

            val testBytes = bluetoothService.createTestReceiptBytes(
                connectionInfo = "Wi-Fi TCP/IP",
                printWidthDots = currentPrintWidth,
                autoCut = autoCut
            )

            val dialog = StatusBottomSheetDialog(this)
            dialog.showLoading(
                getString(R.string.kt_test_print_sending),
                getString(R.string.kt_test_print_wifi_connecting)
            )

            lifecycleScope.launch {
                try {
                    val result = bluetoothService.printDataWifi(ip, port, testBytes)
                    if (isFinishing || isDestroyed) return@launch
                    dialog.dismiss()
                    result.fold(
                        onSuccess = {
                            StatusBottomSheetDialog(this@SettingsActivity).showSuccess(
                                title = getString(R.string.kt_test_print_success_title),
                                message = getString(R.string.kt_test_print_success_message)
                            )
                        },
                        onFailure = {
                            StatusBottomSheetDialog(this@SettingsActivity).showError(
                                title = getString(R.string.kt_test_print_failed_title),
                                message = getString(R.string.kt_test_print_failed_message),
                                actionText = getString(R.string.kt_btn_retry),
                                secondaryText = getString(R.string.kt_btn_close)
                            ) {
                                executeTestPrint()
                            }
                        }
                    )
                } finally {
                    testBytes.fill(0)
                }
            }
        } else {
            // Bluetooth Test Print
            if (selectedPrinterAddress.isBlank()) {
                StatusBottomSheetDialog(this).showError(
                    title = getString(R.string.kt_printer_not_selected_title),
                    message = getString(R.string.kt_printer_not_selected_message),
                    actionText = getString(R.string.kt_choose_printer)
                ) {
                    showPrinterPickerDialog()
                }
                return
            }

            if (!checkAndRequestBluetoothPermissions()) return

            val pairedDevices = bluetoothService.getPairedDevices()
            val device = pairedDevices.find { it.address == selectedPrinterAddress }
            if (device == null) {
                StatusBottomSheetDialog(this).showError(
                    title = getString(R.string.kt_dialog_load_printer_fail_title),
                    message = getString(R.string.kt_load_printer_failed),
                    actionText = getString(R.string.kt_choose_printer)
                ) {
                    showPrinterPickerDialog()
                }
                return
            }

            val testBytes = bluetoothService.createTestReceiptBytes(
                connectionInfo = "Bluetooth",
                printWidthDots = currentPrintWidth,
                autoCut = autoCut
            )

            val dialog = StatusBottomSheetDialog(this)
            dialog.showLoading(
                getString(R.string.kt_test_print_sending),
                getString(R.string.kt_test_print_bt_connecting)
            )

            lifecycleScope.launch {
                try {
                    val result = bluetoothService.printData(device, testBytes)
                    if (isFinishing || isDestroyed) return@launch
                    dialog.dismiss()
                    result.fold(
                        onSuccess = {
                            StatusBottomSheetDialog(this@SettingsActivity).showSuccess(
                                title = getString(R.string.kt_test_print_success_title),
                                message = getString(R.string.kt_test_print_success_message)
                            )
                        },
                        onFailure = {
                            StatusBottomSheetDialog(this@SettingsActivity).showError(
                                title = getString(R.string.kt_test_print_failed_title),
                                message = getString(R.string.kt_test_print_failed_message),
                                actionText = getString(R.string.kt_btn_retry),
                                secondaryText = getString(R.string.kt_btn_close)
                            ) {
                                executeTestPrint()
                            }
                        }
                    )
                } finally {
                    testBytes.fill(0)
                }
            }
        }
    }

    private fun backupSettingsToClipboard() {
        if (!saveSettings()) return
        val json = JSONObject().apply {
            put(KEY_CONNECTION_TYPE, prefs.getString(KEY_CONNECTION_TYPE, "Bluetooth"))
            put(KEY_WIFI_IP, prefs.getString(KEY_WIFI_IP, ""))
            put(KEY_WIFI_PORT, prefs.getString(KEY_WIFI_PORT, "9100"))
            put(KEY_DEFAULT_PRINTER_NAME, prefs.getString(KEY_DEFAULT_PRINTER_NAME, ""))
            put(KEY_DEFAULT_PRINTER_ADDRESS, prefs.getString(KEY_DEFAULT_PRINTER_ADDRESS, ""))
            put(KEY_PAPER_SIZE, prefs.getInt(KEY_PAPER_SIZE, 58))
            put(KEY_PRINT_WIDTH, prefs.getInt(KEY_PRINT_WIDTH, 384))
            put(KEY_DIRECT_PRINT, prefs.getBoolean(KEY_DIRECT_PRINT, false))
            put(KEY_AUTO_CUT, prefs.getBoolean(KEY_AUTO_CUT, true))
            put(KEY_USE_CUSTOM_HEADER, prefs.getBoolean(KEY_USE_CUSTOM_HEADER, false))
            put(KEY_STORE_NAME, prefs.getString(KEY_STORE_NAME, ""))
            put(KEY_STORE_ADDRESS, prefs.getString(KEY_STORE_ADDRESS, ""))
            put(KEY_STORE_FOOTER, prefs.getString(KEY_STORE_FOOTER, ""))
            put("backup_timestamp", SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()))
        }

        val jsonString = json.toString(2)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("NetraPrint_Backup", jsonString)
        clipboard.setPrimaryClip(clip)

        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        prefs.edit().putString(KEY_LAST_CLOUD_SYNC, timestamp).apply()
        tvCloudSyncStatus.text = getString(R.string.kt_cloud_sync_last_text, timestamp)

        StatusBottomSheetDialog(this).showSuccess(
            title = getString(R.string.kt_success_backup_copied_title),
            message = getString(R.string.kt_backup_copied_msg)
        )
    }

    private fun showRestoreDialog() {
        val input = EditText(this).apply {
            hint = "Tempelkan (paste) teks JSON backup di sini..."
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.kt_dialog_restore_title))
            .setMessage(getString(R.string.kt_dialog_restore_msg))
            .setView(input)
            .setPositiveButton(getString(R.string.kt_btn_restore)) { _, _ ->
                val jsonStr = input.text.toString().trim()
                if (jsonStr.isNotBlank()) {
                    try {
                        val json = JSONObject(jsonStr)
                        prefs.edit().apply {
                            if (json.has(KEY_CONNECTION_TYPE)) putString(KEY_CONNECTION_TYPE, json.getString(KEY_CONNECTION_TYPE))
                            if (json.has(KEY_WIFI_IP)) putString(KEY_WIFI_IP, json.getString(KEY_WIFI_IP))
                            if (json.has(KEY_WIFI_PORT)) putString(KEY_WIFI_PORT, json.getString(KEY_WIFI_PORT))
                            if (json.has(KEY_DEFAULT_PRINTER_NAME)) putString(KEY_DEFAULT_PRINTER_NAME, json.getString(KEY_DEFAULT_PRINTER_NAME))
                            if (json.has(KEY_DEFAULT_PRINTER_ADDRESS)) putString(KEY_DEFAULT_PRINTER_ADDRESS, json.getString(KEY_DEFAULT_PRINTER_ADDRESS))
                            if (json.has(KEY_PAPER_SIZE)) putInt(KEY_PAPER_SIZE, json.getInt(KEY_PAPER_SIZE))
                            if (json.has(KEY_PRINT_WIDTH)) putInt(KEY_PRINT_WIDTH, json.getInt(KEY_PRINT_WIDTH))
                            if (json.has(KEY_DIRECT_PRINT)) putBoolean(KEY_DIRECT_PRINT, json.getBoolean(KEY_DIRECT_PRINT))
                            if (json.has(KEY_AUTO_CUT)) putBoolean(KEY_AUTO_CUT, json.getBoolean(KEY_AUTO_CUT))
                            if (json.has(KEY_USE_CUSTOM_HEADER)) putBoolean(KEY_USE_CUSTOM_HEADER, json.getBoolean(KEY_USE_CUSTOM_HEADER))
                            if (json.has(KEY_STORE_NAME)) putString(KEY_STORE_NAME, json.getString(KEY_STORE_NAME))
                            if (json.has(KEY_STORE_ADDRESS)) putString(KEY_STORE_ADDRESS, json.getString(KEY_STORE_ADDRESS))
                            if (json.has(KEY_STORE_FOOTER)) putString(KEY_STORE_FOOTER, json.getString(KEY_STORE_FOOTER))
                            apply()
                        }
                        loadSettings()
                        StatusBottomSheetDialog(this).showSuccess(
                            title = getString(R.string.kt_success_restore_title),
                            message = getString(R.string.kt_restore_success_msg)
                        )
                    } catch (_: Exception) {
                        StatusBottomSheetDialog(this).showError(
                            title = getString(R.string.kt_backup_invalid),
                            message = getString(R.string.kt_backup_invalid),
                            actionText = getString(R.string.kt_btn_close)
                        )
                    }
                }
            }
            .setNegativeButton(getString(R.string.batal), null)
            .show()
    }

    private fun promptSetAdminPin() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Masukkan 4 digit PIN baru"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.kt_dialog_pin_lock_title))
            .setMessage(getString(R.string.kt_dialog_pin_lock_msg))
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.kt_btn_save_pin)) { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length == 4) {
                    prefs.edit()
                        .putBoolean(KEY_ADMIN_PIN_ENABLED, true)
                        .putString(KEY_ADMIN_PIN_CODE, pin)
                        .apply()
                    StatusBottomSheetDialog(this).showSuccess(
                        title = getString(R.string.kt_success_pin_active_title),
                        message = getString(R.string.kt_pin_active_msg)
                    )
                } else {
                    switchAdminPin.isChecked = false
                    StatusBottomSheetDialog(this).showError(
                        title = getString(R.string.kt_dialog_pin_invalid_title),
                        message = getString(R.string.kt_pin_invalid_msg),
                        actionText = getString(R.string.kt_btn_close)
                    )
                }
            }
            .setNegativeButton(getString(R.string.batal)) { _, _ ->
                switchAdminPin.isChecked = false
            }
            .show()
    }

    private fun promptVerifyDisableAdminPin() {
        val currentPin = prefs.getString(KEY_ADMIN_PIN_CODE, "") ?: ""
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Masukkan PIN saat ini"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.kt_dialog_pin_confirm_title))
            .setMessage(getString(R.string.kt_dialog_pin_confirm_msg))
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.kt_btn_confirm)) { _, _ ->
                val entered = input.text.toString().trim()
                if (entered == currentPin) {
                    prefs.edit()
                        .putBoolean(KEY_ADMIN_PIN_ENABLED, false)
                        .putString(KEY_ADMIN_PIN_CODE, "")
                        .apply()
                    StatusBottomSheetDialog(this).showSuccess(
                        title = getString(R.string.kt_success_pin_disabled_title),
                        message = getString(R.string.kt_pin_disabled_msg)
                    )
                } else {
                    switchAdminPin.isChecked = true
                    StatusBottomSheetDialog(this).showError(
                        title = getString(R.string.kt_dialog_pin_wrong_title),
                        message = getString(R.string.kt_pin_wrong_msg),
                        actionText = getString(R.string.kt_btn_close)
                    )
                }
            }
            .setNegativeButton(getString(R.string.batal)) { _, _ ->
                switchAdminPin.isChecked = true
            }
            .show()
    }

    private fun clearAppCache() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.kt_dialog_clear_cache_title))
            .setMessage(getString(R.string.kt_dialog_clear_cache_msg))
            .setPositiveButton(getString(R.string.kt_btn_clear)) { _, _ ->
                try {
                    PrintSpoolerManager.getInstance(applicationContext).clearQueue()
                    check(cacheDir.deleteRecursively()) {
                        "Unable to remove app-private cache"
                    }
                    WebStorage.getInstance().deleteAllData()
                    StatusBottomSheetDialog(this).showSuccess(
                        title = getString(R.string.kt_success_cache_cleared_title),
                        message = getString(R.string.kt_cache_cleared_msg)
                    )
                } catch (_: Exception) {
                    StatusBottomSheetDialog(this).showError(
                        title = getString(R.string.kt_dialog_clear_cache_title),
                        message = getString(R.string.kt_cache_cleanup_failed),
                        actionText = getString(R.string.kt_btn_close)
                    )
                }
            }
            .setNegativeButton(getString(R.string.batal), null)
            .show()
    }

    private fun showUpcomingConnectionDialog(type: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.kt_dialog_upcoming_title, type))
            .setMessage(getString(R.string.kt_dialog_upcoming_msg, type))
            .setPositiveButton(getString(R.string.kt_btn_understood)) { _, _ ->
                val isWifi = prefs.getString(KEY_CONNECTION_TYPE, "Bluetooth") == "Wi-Fi"
                if (isWifi) btnConnWifi.isChecked = true else btnConnBluetooth.isChecked = true
            }
            .setOnCancelListener {
                val isWifi = prefs.getString(KEY_CONNECTION_TYPE, "Bluetooth") == "Wi-Fi"
                if (isWifi) btnConnWifi.isChecked = true else btnConnBluetooth.isChecked = true
            }
            .show()
    }

    private fun showCustomWidthDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentPrintWidth.toString())
            setSelection(text.length)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.kt_dialog_width_title))
            .setMessage(getString(R.string.kt_dialog_width_msg))
            .setView(input)
            .setPositiveButton(getString(R.string.kt_btn_save)) { _, _ ->
                val entered = input.text.toString().toIntOrNull()
                if (entered != null && entered in 128..1200) {
                    currentPrintWidth = (entered / 8) * 8
                    updatePrintWidthDisplay()
                    prefs.edit().putInt(KEY_PRINT_WIDTH, currentPrintWidth).apply()
                }
            }
            .setNegativeButton(getString(R.string.batal), null)
            .show()
    }

    private fun updatePrintWidthDisplay() {
        val label = when (currentPrintWidth) {
            384 -> "384 dots (Standar 58mm)"
            576 -> "576 dots (Standar 80mm)"
            720 -> "720 dots (Standar 100mm)"
            else -> getString(R.string.kt_width_custom, currentPrintWidth)
        }
        tvPrintWidth.text = label
    }

    private var updateDownloadJob: Job? = null

    private fun checkForUpdates() {
        val dialog = StatusBottomSheetDialog(this)
        dialog.showLoading(
            getString(R.string.cek_pembaruan),
            getString(R.string.kt_update_checking)
        )
        lifecycleScope.launch {
            try {
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).versionCode
                }
                val jsonText = withContext(Dispatchers.IO) {
                    val conn = (URL("https://netraprint-updater.yt-hackrandom.workers.dev/version.json").openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "NetraPrint-Android")
                        setRequestProperty("Accept", "application/json")
                        useCaches = false
                    }
                    try {
                        val code = conn.responseCode
                        if (code in 200..299) {
                            conn.inputStream.bufferedReader().use { it.readText() }
                        } else {
                            throw java.io.IOException("HTTP error $code")
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
                val json = JSONObject(jsonText)
                val latestVersionCode = json.getInt("latestVersionCode")
                val latestVersionName = json.getString("latestVersionName")
                val downloadUrl = json.optString("downloadUrl", "https://netraprint-updater.yt-hackrandom.workers.dev/download")
                val rawApkUrl = json.optString("apkUrl", "")
                val releasePageUrl = json.optString("releasePageUrl", "https://netraprint-updater.yt-hackrandom.workers.dev/download")
                val changelog = json.optString("changelog", "")
                val apkUrl = if (rawApkUrl.isNotBlank()) {
                    rawApkUrl
                } else if (downloadUrl.isNotBlank()) {
                    downloadUrl
                } else {
                    "https://netraprint-updater.yt-hackrandom.workers.dev/download"
                }

                if (isFinishing || isDestroyed) return@launch
                dialog.dismiss()

                if (latestVersionCode > currentVersionCode) {
                    val updateMessage = if (changelog.isNotBlank()) {
                        getString(R.string.kt_update_available_msg, latestVersionName) +
                                getString(R.string.kt_release_notes_header) + changelog
                    } else {
                        getString(R.string.kt_update_available_msg, latestVersionName)
                    }

                    StatusBottomSheetDialog(this@SettingsActivity).showSuccess(
                        title = getString(R.string.kt_update_available),
                        message = updateMessage,
                        actionText = getString(R.string.kt_btn_download),
                        secondaryText = getString(R.string.kt_btn_open_browser),
                        onDismiss = {
                            downloadApkUpdate(apkUrl, releasePageUrl, latestVersionName)
                        },
                        onSecondary = {
                            openInBrowser(releasePageUrl)
                        }
                    )
                } else {
                    StatusBottomSheetDialog(this@SettingsActivity).showSuccess(
                        title = getString(R.string.cek_pembaruan),
                        message = getString(R.string.kt_update_latest)
                    )
                }
            } catch (_: Exception) {
                if (isFinishing || isDestroyed) return@launch
                dialog.dismiss()
                StatusBottomSheetDialog(this@SettingsActivity).showError(
                    title = getString(R.string.cek_pembaruan),
                    message = getString(R.string.kt_update_failed),
                    actionText = getString(R.string.kt_btn_understood)
                )
            }
        }
    }

    private fun downloadApkUpdate(downloadUrl: String, releasePageUrl: String, versionName: String) {
        val fileName = "NetraPrint-v$versionName.apk"
        val targetApk = File(cacheDir, fileName)

        // If target file already exists and is completely downloaded (> 10MB), install directly
        if (targetApk.exists() && targetApk.length() > 10 * 1024 * 1024) {
            showInstallDialog(targetApk, versionName)
            return
        }

        try {
            if (targetApk.exists()) {
                targetApk.delete()
            }
        } catch (_: Exception) {}

        val statusDialog = StatusBottomSheetDialog(this)
        statusDialog.showLoading(
            title = getString(R.string.kt_downloading_update_title),
            message = getString(R.string.kt_downloading_update_desc),
            showProgress = true
        )

        updateDownloadJob?.cancel()
        statusDialog.setOnDismissListener {
            updateDownloadJob?.cancel()
        }

        updateDownloadJob = lifecycleScope.launch(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            var finalConnection: java.net.HttpURLConnection? = null
            try {
                var currentUrl = downloadUrl
                var redirectCount = 0
                val maxRedirects = 5

                while (redirectCount < maxRedirects) {
                    val urlObj = URL(currentUrl)
                    val conn = (urlObj.openConnection() as java.net.HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15000
                        readTimeout = 30000
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "NetraPrint-Android-Updater")
                        setRequestProperty("Accept", "*/*")
                        useCaches = false
                    }
                    conn.connect()

                    val code = conn.responseCode
                    if (code == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                        code == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                        code == java.net.HttpURLConnection.HTTP_SEE_OTHER ||
                        code == 307 || code == 308
                    ) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (location.isNullOrBlank()) {
                            throw IOException("Redirect without Location header")
                        }
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(urlObj, location).toString()
                        }
                        redirectCount++
                    } else if (code in 200..299) {
                        finalConnection = conn
                        break
                    } else {
                        conn.disconnect()
                        throw IOException("HTTP error $code")
                    }
                }

                val conn = finalConnection ?: throw IOException("Terlalu banyak pengalihan")
                val totalBytes = conn.contentLengthLong

                inputStream = BufferedInputStream(conn.inputStream)
                outputStream = FileOutputStream(targetApk)

                val buffer = ByteArray(8192)
                var downloadedBytes = 0L
                var bytesRead: Int
                var lastUpdateMs = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    ensureActive()
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdateMs >= 150 || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                        lastUpdateMs = now
                        val progressPercent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            null
                        }
                        val mbDownloaded = downloadedBytes / (1024.0 * 1024.0)
                        val progressMessage = if (totalBytes > 0) {
                            val mbTotal = totalBytes / (1024.0 * 1024.0)
                            String.format(Locale.US, "%.1f MB / %.1f MB (%d%%)", mbDownloaded, mbTotal, progressPercent ?: 0)
                        } else {
                            String.format(Locale.US, "%.1f MB...", mbDownloaded)
                        }
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                statusDialog.updateProgress(progressMessage, progressPercent)
                            }
                        }
                    }
                }
                outputStream.flush()

                if (targetApk.length() < 5 * 1024 * 1024) {
                    throw IOException("Ukuran file tidak lengkap (${targetApk.length()} bytes)")
                }

                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    statusDialog.showSuccess(
                        title = getString(R.string.kt_download_complete_title),
                        message = getString(R.string.kt_download_complete_msg, versionName),
                        actionText = getString(R.string.kt_btn_install_now),
                        secondaryText = getString(R.string.batal),
                        onDismiss = {
                            installDownloadedApk(targetApk)
                        }
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    try { targetApk.delete() } catch (_: Exception) {}
                    return@launch
                }
                Log.e("SettingsActivity", "In-app download failed", e)
                try { targetApk.delete() } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    statusDialog.showError(
                        title = getString(R.string.kt_download_failed_title),
                        message = getString(R.string.kt_download_failed_msg, e.localizedMessage ?: "Network error"),
                        actionText = getString(R.string.kt_btn_open_browser),
                        secondaryText = getString(R.string.batal),
                        onDismiss = {
                            openInBrowser(releasePageUrl.ifBlank { downloadUrl })
                        }
                    )
                }
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
                try { outputStream?.close() } catch (_: Exception) {}
                finalConnection?.disconnect()
            }
        }
    }

    private fun showInstallDialog(apkFile: File, versionName: String) {
        StatusBottomSheetDialog(this).showSuccess(
            title = getString(R.string.kt_download_complete_title),
            message = getString(R.string.kt_download_complete_msg, versionName),
            actionText = getString(R.string.kt_btn_install_now),
            secondaryText = getString(R.string.batal),
            onDismiss = {
                installDownloadedApk(apkFile)
            }
        )
    }

    private fun installDownloadedApk(apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(this, getString(R.string.kt_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                try {
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(permissionIntent)
                    Toast.makeText(
                        this,
                        getString(R.string.kt_install_permission_prompt),
                        Toast.LENGTH_LONG
                    ).show()
                    return
                } catch (e: Exception) {
                    Log.e("SettingsActivity", "Could not open manage unknown app sources", e)
                }
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Failed to launch installer", e)
            Toast.makeText(this, "Gagal membuka installer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(fallback)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.kt_cannot_open_browser), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        updateDownloadJob?.cancel()
        super.onDestroy()
    }

    private fun updatePrinterDisplay() {
        if (selectedPrinterName.isNotBlank()) {
            tvPrinterName.text = getString(R.string.kt_printer_name_format, selectedPrinterName, selectedPrinterAddress)
        } else {
            tvPrinterName.setText(R.string.kt_all_printers_choose_when_printing)
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            permissions[Manifest.permission.BLUETOOTH] != false
        }
        if (granted) {
            showPrinterPickerDialog()
        } else {
            if (!isFinishing && !isDestroyed) {
                StatusBottomSheetDialog(this).showError(
                    title = getString(R.string.kt_dialog_camera_perm_title),
                    message = getString(R.string.kt_dialog_camera_perm_msg),
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

    private fun showPrinterPickerDialog() {
        if (isFinishing || isDestroyed) return

        if (!checkAndRequestBluetoothPermissions()) {
            return
        }

        if (!bluetoothService.isBluetoothSupported) {
            StatusBottomSheetDialog(this).showError(
                title = getString(R.string.kt_dialog_bt_not_supported_title),
                message = getString(R.string.kt_bt_not_supported_msg),
                actionText = getString(R.string.kt_btn_close)
            )
            return
        }

        lifecycleScope.launch {
            try {
                val pairedDevices = withContext(Dispatchers.IO) {
                    bluetoothService.getPairedDevices()
                }

                if (isFinishing || isDestroyed) return@launch

                if (pairedDevices.isEmpty()) {
                    StatusBottomSheetDialog(this@SettingsActivity).showError(
                        title = getString(R.string.kt_dialog_no_paired_title),
                        message = getString(R.string.kt_no_paired_msg),
                        actionText = getString(R.string.kt_btn_open_bluetooth_settings)
                    ) {
                        startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    return@launch
                }

                PrinterPickerBottomSheetDialog(this@SettingsActivity, pairedDevices, selectedPrinterAddress) { name, address ->
                    selectedPrinterName = name
                    selectedPrinterAddress = address
                    updatePrinterDisplay()
                    prefs.edit()
                        .putString(KEY_DEFAULT_PRINTER_NAME, selectedPrinterName)
                        .putString(KEY_DEFAULT_PRINTER_ADDRESS, selectedPrinterAddress)
                        .apply()
                }.show()
            } catch (_: SecurityException) {
                Log.e("SettingsActivity", "Bluetooth permission denied while loading printers")
                if (!isFinishing && !isDestroyed) {
                    StatusBottomSheetDialog(this@SettingsActivity).showError(
                        title = getString(R.string.kt_dialog_bt_denied_title),
                        message = getString(R.string.kt_bt_denied_msg),
                        actionText = getString(R.string.kt_btn_grant_permission)
                    ) {
                        checkAndRequestBluetoothPermissions()
                    }
                }
            } catch (_: Exception) {
                Log.e("SettingsActivity", "Unable to load paired printers")
                if (!isFinishing && !isDestroyed) {
                    StatusBottomSheetDialog(this@SettingsActivity).showError(
                        title = getString(R.string.kt_dialog_load_printer_fail_title),
                        message = getString(R.string.kt_load_printer_failed),
                        actionText = getString(R.string.kt_btn_close)
                    )
                }
            }
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(getString(R.string.bahasa_indonesia), getString(R.string.kt_lang_english))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.kt_dialog_language_title))
            .setItems(languages) { _, which ->
                val chosen = languages[which]
                tvLanguage.text = chosen
                val langTag = if (which == 1) "en" else "id"
                if (resources.configuration.locales[0]?.language == langTag) return@setItems
                prefs.edit().putString(KEY_LANGUAGE, chosen).apply()
                val appLocales = LocaleListCompat.forLanguageTags(langTag)
                disableActivityTransitionForLocaleChange()
                AppCompatDelegate.setApplicationLocales(appLocales)
            }
            .setNegativeButton(getString(R.string.batal), null)
            .show()
    }

    private fun disableActivityTransitionForLocaleChange() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun saveSettings(): Boolean {

        val paperSize = when (rgPaperSize.checkedButtonId) {
            R.id.btnSize80 -> 80
            R.id.btnSize100 -> 100
            else -> 58
        }

        val connType = if (btnConnWifi.isChecked) "Wi-Fi" else "Bluetooth"

        prefs.edit()
            .putString(KEY_CONNECTION_TYPE, connType)
            .putString(KEY_WIFI_IP, etWifiIp.text.toString().trim())
            .putString(KEY_WIFI_PORT, etWifiPort.text.toString().trim())
            .putString(KEY_DEFAULT_PRINTER_NAME, selectedPrinterName)
            .putString(KEY_DEFAULT_PRINTER_ADDRESS, selectedPrinterAddress)
            .putInt(KEY_PAPER_SIZE, paperSize)
            .putInt(KEY_PRINT_WIDTH, currentPrintWidth)
            .putBoolean(KEY_DIRECT_PRINT, switchDirectPrint.isChecked)
            .putBoolean(KEY_AUTO_CUT, switchAutoCut.isChecked)
            .putString(KEY_CLOUD_SYNC_KEY, etCloudSyncKey.text.toString().trim())
            .putBoolean(KEY_AUTO_CLOUD_SYNC, switchAutoCloudSync.isChecked)
            .putBoolean(KEY_USE_CUSTOM_HEADER, switchUseCustomHeader.isChecked)
            .putString(KEY_STORE_NAME, etStoreName.text.toString().trim())
            .putString(KEY_STORE_ADDRESS, etStoreAddress.text.toString().trim())
            .putString(KEY_STORE_FOOTER, etStoreFooter.text.toString().trim())
            .apply()
        return true
    }
}

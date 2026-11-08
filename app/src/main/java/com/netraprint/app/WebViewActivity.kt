package com.netraprint.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebViewActivity : AppCompatActivity() {

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            permissions[Manifest.permission.BLUETOOTH] != false
        }
        if (granted) {
            printWebContent()
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

    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var bluetoothService: BluetoothService
    private lateinit var printHistoryManager: PrintHistoryManager

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        bluetoothService = BluetoothService(this)
        printHistoryManager = (application as? PrinterApplication)?.appContainer?.printHistoryManager ?: PrintHistoryManager.getInstance(applicationContext)

        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayoutWeb)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarWeb)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        webView = findViewById(R.id.webView)
        etUrl = findViewById(R.id.etWebUrl)
        progressBar = findViewById(R.id.webProgressBar)

        val btnBack = findViewById<ImageButton>(R.id.btnWebBack)
        val btnForward = findViewById<ImageButton>(R.id.btnWebForward)
        val btnRefresh = findViewById<ImageButton>(R.id.btnWebRefresh)
        val btnGo = findViewById<MaterialButton>(R.id.btnWebGo)
        val btnPrint = findViewById<MaterialButton>(R.id.btnPrintWebContent)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase()
                return scheme != "https" && scheme != "http"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) {
                    etUrl.setText(url)
                }
            }
        }

        btnBack.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }

        btnForward.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }

        btnRefresh.setOnClickListener {
            webView.reload()
        }

        btnGo.setOnClickListener {
            loadEnteredUrl()
        }

        etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadEnteredUrl()
                true
            } else {
                false
            }
        }

        btnPrint.setOnClickListener {
            printWebContent()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        // Default initial URL
        loadUrl("https://www.google.com")
    }

    private fun loadEnteredUrl() {
        val input = etUrl.text.toString().trim()
        if (input.isNotEmpty()) {
            val finalUrl = if (!input.startsWith("http://") && !input.startsWith("https://")) {
                "https://$input"
            } else {
                input
            }
            loadUrl(finalUrl)
        }
    }

    private fun loadUrl(url: String) {
        etUrl.setText(url)
        webView.loadUrl(url)
    }

    private fun printWebContent() {
        if (webView.width <= 0 || webView.height <= 0) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.kt_dialog_web_warning_title))
                .setMessage(getString(R.string.kt_dialog_web_warning_msg))
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
                title = getString(R.string.kt_loading_wifi_pdf_title),
                message = getString(R.string.kt_loading_connect_msg, ip, port),
                showProgress = false
            )

            lifecycleScope.launch {
                val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    webView.draw(canvas)
                    val printBytes = bluetoothService.createImageBytes(bitmap, printWidth, autoCut)
                    try {
                        val result = bluetoothService.printDataWifi(ip, port, printBytes)
                        if (isFinishing || isDestroyed) return@launch
                        statusDialog.dismiss()
                        result.fold(
                            onSuccess = {
                                printHistoryManager.addEntry(
                                    title = getString(R.string.halaman_web),
                                    printType = "WEB",
                                    targetName = ip,
                                    targetInfo = port.toString(),
                                    status = PrintHistoryItem.Status.SUCCESS,
                                    copies = 1
                                )
                                StatusBottomSheetDialog(this@WebViewActivity).showSuccess(
                                    title = getString(R.string.kt_success_print_done_title),
                                    message = getString(R.string.kt_wifi_print_success_msg, ip, port)
                                )
                            },
                            onFailure = {
                                printHistoryManager.addEntry(
                                    title = getString(R.string.halaman_web),
                                    printType = "WEB",
                                    targetName = ip,
                                    targetInfo = port.toString(),
                                    status = PrintHistoryItem.Status.FAILED,
                                    copies = 1
                                )
                                StatusBottomSheetDialog(this@WebViewActivity).showError(
                                    title = getString(R.string.kt_dialog_wifi_error_title),
                                    message = getString(R.string.kt_print_wifi_connection_failed),
                                    actionText = getString(R.string.kt_btn_retry),
                                    secondaryText = getString(R.string.kt_btn_close)
                                ) {
                                    printWebContent()
                                }
                            }
                        )
                    } finally {
                        printBytes.fill(0)
                    }
                } finally {
                    bitmap.recycle()
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
                StatusBottomSheetDialog(this@WebViewActivity).showError(
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

            fun sendPrint(device: BluetoothDevice) {
                lifecycleScope.launch {
                    val statusDialog = StatusBottomSheetDialog(this@WebViewActivity)
                    statusDialog.showLoading(
                        title = getString(R.string.kt_loading_qr_title),
                        message = getString(
                            R.string.kt_loading_connect_device_msg,
                            bluetoothService.getDeviceName(device) ?: getString(R.string.kt_thermal_printer)
                        ),
                        showProgress = false
                    )
                    val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        webView.draw(canvas)
                        val printBytes = bluetoothService.createImageBytes(bitmap, printWidth, autoCut)
                        try {
                            val result = withContext(Dispatchers.IO) {
                                bluetoothService.printData(device, printBytes)
                            }
                            val deviceName = bluetoothService.getDeviceName(device) ?: getString(R.string.kt_thermal_printer)
                            if (result.isSuccess) {
                                printHistoryManager.addEntry(
                                    title = getString(R.string.halaman_web),
                                    printType = "WEB",
                                    targetName = deviceName,
                                    targetInfo = device.address,
                                    status = PrintHistoryItem.Status.SUCCESS,
                                    copies = 1
                                )
                                statusDialog.showSuccess(
                                    title = getString(R.string.kt_success_print_done_title),
                                    message = getString(R.string.kt_thermal_print_success_msg)
                                )
                            } else {
                                printHistoryManager.addEntry(
                                    title = getString(R.string.halaman_web),
                                    printType = "WEB",
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
                    } finally {
                        bitmap.recycle()
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

            PrinterPickerBottomSheetDialog(this@WebViewActivity, pairedDevices, defaultAddress, includeAllPrintersOption = false) { _, address ->
                val selected = if (address.isNotBlank()) {
                    pairedDevices.find { it.address == address } ?: pairedDevices.first()
                } else {
                    pairedDevices.first()
                }
                sendPrint(selected)
            }.show()
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.removeJavascriptInterface("NetraPrint")
        webView.clearHistory()
        webView.clearFormData()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.destroy()
        super.onDestroy()
    }
}

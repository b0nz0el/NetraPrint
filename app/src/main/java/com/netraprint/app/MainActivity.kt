package com.netraprint.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.view.animation.AccelerateInterpolator
import android.bluetooth.BluetoothDevice
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import com.google.android.play.core.review.ReviewManagerFactory
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Filter
import androidx.core.content.FileProvider
import android.speech.tts.TextToSpeech
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.navigation.NavigationView
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.util.Locale
import org.json.JSONObject
import androidx.activity.viewModels
import com.netraprint.app.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    /**
     * ViewModel berisi state UI (mode, dithering, font, lebar cetak, antrean spooler).
     * Factory memakai PrintRepository dari AppContainer — tanpa Hilt.
     */
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.factory((application as PrinterApplication).appContainer.printRepository)
    }

    private enum class CurrentMode {
        FORM, FREE_TEXT, PDF, IMAGE, PLACEHOLDER
    }

    private enum class CameraActionTarget {
        HEADER_LOGO_AND_BARCODE,
        KODE_BOOKING_BARCODE,
        OCR_PENERIMA,
        OCR_PENGIRIM,
        OCR_KODE_BOOKING,
        OCR_ISI_PAKET,
        OCR_CATATAN
    }

    private var currentMode = CurrentMode.FORM
    private var activeCameraTarget: CameraActionTarget = CameraActionTarget.HEADER_LOGO_AND_BARCODE

    private lateinit var bluetoothService: BluetoothService
    private var textToSpeech: TextToSpeech? = null

    // Navigation Drawer & App Bar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var navigationView: NavigationView

    // Live Printer Status Badge & Spooler Alert Banner
    private var layoutPrinterStatusBadge: LinearLayout? = null
    private var dotPrinterStatus: View? = null
    private var tvToolbarPrinterStatus: TextView? = null
    private var layoutSpoolerBadge: LinearLayout? = null
    private var tvSpoolerQueueBadge: TextView? = null
    private var btnRetrySpooler: MaterialButton? = null

    // Thermal WYSIWYG Roll Preview Views
    private var cardThermalPreview: View? = null
    private var chipPaperWidth: Chip? = null
    private var chipDitherMode: Chip? = null
    private var tvThermalPreviewText: TextView? = null
    private var ivThermalPreviewImage: ImageView? = null
    private var tvThermalPreviewInfo: TextView? = null

    // Dithering & Print Spooler (state di MainViewModel)
    private var currentDitherMode = DitheringEngine.DitherMode.FLOYD_STEINBERG
    private lateinit var printSpoolerManager: PrintSpoolerManager
    private lateinit var printHistoryManager: PrintHistoryManager
    private val spoolerListener: (Int) -> Unit = { count ->
        // postValue aman dipanggil dari thread mana pun (spooler bisa notify dari thread IO)
        mainViewModel.spoolerQueueCount.postValue(count)
    }

    // Main Content Containers
    private lateinit var nestedScrollView: NestedScrollView
    private lateinit var layoutFreeText: View
    private lateinit var layoutPlaceholderFeature: View
    private lateinit var layoutPdfPreview: FrameLayout
    private lateinit var layoutImagePreview: FrameLayout

    // Placeholder Views
    private lateinit var tvPlaceholderTitle: TextView
    private lateinit var tvPlaceholderDesc: TextView

    // Free Text Views
    private lateinit var etFreeText: EditText

    // PDF Preview Views
    private lateinit var layoutPdfPlaceholder: LinearLayout
    private lateinit var layoutPdfContent: LinearLayout
    private lateinit var tvPdfFileName: TextView
    private lateinit var tvPdfPageIndicator: TextView
    private lateinit var btnPrevPdfPage: ImageButton
    private lateinit var btnNextPdfPage: ImageButton
    private lateinit var btnChangePdfDoc: MaterialButton
    private lateinit var ivPdfPreview: ImageView
    private lateinit var sliderPdfZoom: Slider
    private lateinit var tvPdfZoom: TextView
    private lateinit var btnChoosePdf: MaterialButton

    private var selectedPdfUri: Uri? = null
    private var pdfFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var managedSystemPrintPdf: File? = null
    private var currentPdfPageIndex = 0
    private var currentPdfPageBitmap: Bitmap? = null

    // Image Preview Views
    private lateinit var layoutImagePlaceholder: LinearLayout
    private lateinit var layoutImageContent: LinearLayout
    private lateinit var tvImageFileName: TextView
    private lateinit var btnChangeImageDoc: MaterialButton
    private lateinit var ivImagePreview: ImageView
    private lateinit var sliderImageZoom: Slider
    private lateinit var tvImageZoom: TextView
    private lateinit var btnChooseImage: MaterialButton

    private var selectedImageUri: Uri? = null
    private var originalImageBitmap: Bitmap? = null

    // Form Views
    private lateinit var ivLogoPlaceholder: ImageView
    private lateinit var etPenerima: EditText
    private lateinit var etPengirim: EditText
    private var layoutJasaKurir: TextInputLayout? = null
    private lateinit var actvJasaKurir: AutoCompleteTextView
    private lateinit var etKodeBooking: EditText
    private lateinit var etIsiPaket: EditText
    private lateinit var etCatatan: EditText
    private var layoutFont: TextInputLayout? = null
    private lateinit var actvFont: AutoCompleteTextView

    private var currentSelectedFont: String = "Default"

    // File Pickers
    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            loadPdfDocument(uri)
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            loadImageDocument(uri)
        }
    }

    private val logoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        saveStoreLogo(bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Unable to read store logo from selected image")
            }
        }
    }

    // Camera & ML Kit Full-Resolution Capture
    private var tempPhotoUri: Uri? = null

    private fun createTempImageUri(): Uri {
        val tempFile = File(cacheDir, "temp_ocr_capture.jpg")
        if (tempFile.exists()) tempFile.delete()
        tempFile.createNewFile()
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
    }

    private fun deleteTemporaryOcrPhoto() {
        val tempFile = File(cacheDir, "temp_ocr_capture.jpg")
        if (tempFile.exists() && !tempFile.delete()) {
            Log.w("MainActivity", "Unable to remove temporary OCR image")
        }
        tempPhotoUri = null
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempPhotoUri?.let { uri ->
                processOcrUri(uri)
            }
        } else {
            deleteTemporaryOcrPhoto()
            Toast.makeText(this, getString(R.string.kt_toast_photo_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    private val ocrGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processOcrUri(uri)
        }
    }

    private val barcodeScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scannedCode = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_SCANNED_CODE)
            if (!scannedCode.isNullOrBlank()) {
                etKodeBooking.setText(scannedCode)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraCapture()
        } else {
            showImportantError(getString(R.string.kt_camera_permission_required))
        }
    }

    // Bluetooth Permissions
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            if (currentMode == CurrentMode.PDF) {
                val renderer = pdfRenderer
                if (renderer != null && renderer.pageCount > 0) {
                    PageRangeDialog(this, renderer.pageCount) { selectedPages ->
                        handlePrintAction(selectedPages)
                    }.show()
                } else {
                    handlePrintAction(null)
                }
            } else {
                handlePrintAction(null)
            }
        } else {
            showImportantError(getString(R.string.kt_bluetooth_permission_required))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        val splashStartTime = System.currentTimeMillis()
        splashScreen.setKeepOnScreenCondition {
            System.currentTimeMillis() - splashStartTime < 1250L
        }
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val iconView = splashScreenViewProvider.iconView
            val splashView = splashScreenViewProvider.view

            val scaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.15f, 0.9f)
            val scaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.15f, 0.9f)
            val iconAlpha = ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f)
            val bgAlpha = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f)

            AnimatorSet().apply {
                duration = 350L
                interpolator = AccelerateInterpolator()
                playTogether(scaleX, scaleY, iconAlpha, bgAlpha)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        splashScreenViewProvider.remove()
                    }
                })
                start()
            }
        }

        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Satu instance service dibagikan dari AppContainer (Application-scoped),
        // sehingga ViewModel/Repository dan Activity memakai service yang sama.
        val appContainer = (application as PrinterApplication).appContainer
        bluetoothService = appContainer.bluetoothService
        printSpoolerManager = appContainer.printSpoolerManager
        printHistoryManager = appContainer.printHistoryManager

        val prefs = SecurePreferencesHelper.getPreferences(this)
        if (!prefs.getBoolean(OnboardingActivity.KEY_ONBOARDING_COMPLETED, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Security check: Root detection
        if (RootDetectionHelper.isDeviceRooted()) {
            android.util.Log.w("NetraPrint_Security", "Root risk detected")
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        printSpoolerManager.addListener(spoolerListener)

        // Observe ViewModel LiveData for UI state
        mainViewModel.spoolerQueueCount.observe(this) { count ->
            updateSpoolerBadge(count)
        }
        mainViewModel.currentMode.observe(this) { mode ->
            // Mode changes handled by switchToMode calls; kept for future reactive UI
        }

        initTextToSpeech()

        initViews()
        setupWindowInsets()
        setupToolbarAndDrawer()
        setupHeaderButtons()
        setupFormSection()
        loadStoreLogo()
        setupPdfModeViews()
        setupImageModeViews()
        setupBottomNavigation()
        setupFabPrint()

        updateToolbarStatusBadge()
        updateThermalPreview()
        handleIncomingIntent(intent)

        try {
            val navView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
            val headerV = navView.getHeaderView(0)
            val badge = headerV.findViewById<android.widget.TextView>(R.id.tvNavVersionBadge)
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            badge.text = "v$versionName"
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setupInAppUpdateChecker()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (currentMode != CurrentMode.FORM) {
                    switchToMode(CurrentMode.FORM)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (SecurePreferencesHelper.consumeRecoveryNotice() && !isFinishing && !isDestroyed) {
            StatusBottomSheetDialog(this).showError(
                title = getString(R.string.kt_secure_storage_reset_title),
                message = getString(R.string.kt_secure_storage_reset_message),
                actionText = getString(R.string.kt_btn_understood)
            )
        }
        if (::bluetoothService.isInitialized) {
            updateToolbarStatusBadge()
        }
        updateThermalPreview()
        if (::printSpoolerManager.isInitialized) {
            updateSpoolerBadge(printSpoolerManager.getQueueCount())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        deleteTemporaryOcrPhoto()
        if (::printSpoolerManager.isInitialized) {
            printSpoolerManager.removeListener(spoolerListener)
        }
        closePdfRenderer()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            Log.d("TTS_DEBUG", "TextToSpeech successfully shutdown")
        } catch (_: Exception) {}
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.isCtrlPressed == true) {
            when (keyCode) {
                KeyEvent.KEYCODE_P -> {
                    findViewById<View>(R.id.fabPrint)?.performClick()
                    return true
                }
                KeyEvent.KEYCODE_B -> {
                    layoutPrinterStatusBadge?.performClick()
                    return true
                }
                KeyEvent.KEYCODE_1 -> {
                    switchToMode(CurrentMode.FORM)
                    return true
                }
                KeyEvent.KEYCODE_2 -> {
                    switchToMode(CurrentMode.FREE_TEXT)
                    return true
                }
                KeyEvent.KEYCODE_3 -> {
                    if (selectedPdfUri != null) {
                        switchToMode(CurrentMode.PDF)
                    } else {
                        pdfPickerLauncher.launch("application/pdf")
                    }
                    return true
                }
                KeyEvent.KEYCODE_4 -> {
                    if (selectedImageUri != null) {
                        switchToMode(CurrentMode.IMAGE)
                    } else {
                        imagePickerLauncher.launch("image/*")
                    }
                    return true
                }
                KeyEvent.KEYCODE_S -> {
                    if (printSpoolerManager.getQueueCount() > 0) {
                        flushOfflineSpoolerQueue()
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private var isTtsInitialized = false

    private fun initTextToSpeech(onReady: (() -> Unit)? = null) {
        textToSpeech = TextToSpeech(this) { status ->
            Log.d("TTS_DEBUG", "TextToSpeech onInit status: $status")
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                val localeId = Locale.Builder().setLanguage("id").setRegion("ID").build()
                val result = textToSpeech?.setLanguage(localeId)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w("TTS_DEBUG", "Bahasa Indonesia tidak didukung mesin TTS, fallback ke Locale.getDefault(): ${Locale.getDefault()}")
                    textToSpeech?.setLanguage(Locale.getDefault())
                } else {
                    Log.d("TTS_DEBUG", "Bahasa Indonesia (id-ID) berhasil diaktifkan pada TTS")
                }
                onReady?.invoke()
            } else {
                isTtsInitialized = false
                Log.e("TTS_DEBUG", "Inisialisasi TextToSpeech gagal dengan kode: $status")
            }
        }
    }

    private fun speakText(text: String) {
        val cleanText = text.trim()
        Log.d("TTS_DEBUG", "Starting text-to-speech output")
        if (cleanText.isEmpty()) {
            Log.d("TTS_DEBUG", "Teks kosong, tidak ada suara yang diputar")
            return
        }

        if (textToSpeech == null || !isTtsInitialized) {
            Log.w("TTS_DEBUG", "TextToSpeech belum siap, inisialisasi ulang...")
            initTextToSpeech {
                val res = textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
                Log.d("TTS_DEBUG", "Speak result setelah inisialisasi: $res")
            }
            return
        }

        val res = textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
        Log.d("TTS_DEBUG", "Speak result: $res")
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        appBarLayout = findViewById(R.id.appBarLayout)
        toolbar = findViewById(R.id.toolbar)
        navigationView = findViewById(R.id.navigationView)

        nestedScrollView = findViewById(R.id.nestedScrollView)
        layoutFreeText = findViewById(R.id.layoutFreeText)
        layoutPlaceholderFeature = findViewById(R.id.layoutPlaceholderFeature)
        layoutPdfPreview = findViewById(R.id.layoutPdfPreview)
        layoutImagePreview = findViewById(R.id.layoutImagePreview)

        tvPlaceholderTitle = findViewById(R.id.tvPlaceholderTitle)
        tvPlaceholderDesc = findViewById(R.id.tvPlaceholderDesc)
        etFreeText = findViewById(R.id.etFreeText)

        // PDF Views
        layoutPdfPlaceholder = findViewById(R.id.layoutPdfPlaceholder)
        layoutPdfContent = findViewById(R.id.layoutPdfContent)
        tvPdfFileName = findViewById(R.id.tvPdfFileName)
        tvPdfPageIndicator = findViewById(R.id.tvPdfPageIndicator)
        btnPrevPdfPage = findViewById(R.id.btnPrevPdfPage)
        btnNextPdfPage = findViewById(R.id.btnNextPdfPage)
        btnChangePdfDoc = findViewById(R.id.btnChangePdfDoc)
        ivPdfPreview = findViewById(R.id.ivPdfPreview)
        sliderPdfZoom = findViewById(R.id.sliderPdfZoom)
        tvPdfZoom = findViewById(R.id.tvPdfZoom)
        btnChoosePdf = findViewById(R.id.btnChoosePdf)

        ivPdfPreview.scaleType = ImageView.ScaleType.FIT_CENTER

        // Image Views
        layoutImagePlaceholder = findViewById(R.id.layoutImagePlaceholder)
        layoutImageContent = findViewById(R.id.layoutImageContent)
        tvImageFileName = findViewById(R.id.tvImageFileName)
        btnChangeImageDoc = findViewById(R.id.btnChangeImageDoc)
        ivImagePreview = findViewById(R.id.ivImagePreview)
        sliderImageZoom = findViewById(R.id.sliderImageZoom)
        tvImageZoom = findViewById(R.id.tvImageZoom)
        btnChooseImage = findViewById(R.id.btnChooseImage)

        ivImagePreview.scaleType = ImageView.ScaleType.FIT_CENTER

        // Form Views
        ivLogoPlaceholder = findViewById(R.id.ivLogoPlaceholder)
        etPenerima = findViewById(R.id.etPenerima)
        etPengirim = findViewById(R.id.etPengirim)
        layoutJasaKurir = findViewById(R.id.layoutJasaKurir)
        actvJasaKurir = findViewById(R.id.actvJasaKurir)
        etKodeBooking = findViewById(R.id.etKodeBooking)
        etIsiPaket = findViewById(R.id.etIsiPaket)
        etCatatan = findViewById(R.id.etCatatan)
        layoutFont = findViewById(R.id.layoutFont)
        actvFont = findViewById(R.id.actvFont)

        // Status Badge & Spooler Alert
        layoutPrinterStatusBadge = findViewById(R.id.layoutPrinterStatusBadge)
        dotPrinterStatus = findViewById(R.id.dotPrinterStatus)
        tvToolbarPrinterStatus = findViewById(R.id.tvToolbarPrinterStatus)
        layoutSpoolerBadge = findViewById(R.id.layoutSpoolerBadge)
        tvSpoolerQueueBadge = findViewById(R.id.tvSpoolerQueueBadge)
        btnRetrySpooler = findViewById(R.id.btnRetrySpooler)

        // Thermal WYSIWYG Roll Preview Views
        cardThermalPreview = findViewById(R.id.layoutThermalPreviewCard) ?: findViewById(R.id.cardThermalPreview)
        chipPaperWidth = findViewById(R.id.chipPaperWidth)
        chipDitherMode = findViewById(R.id.chipDitherMode)
        tvThermalPreviewText = findViewById(R.id.tvThermalPreviewText)
        ivThermalPreviewImage = findViewById(R.id.ivThermalPreviewImage)
        tvThermalPreviewInfo = findViewById(R.id.tvThermalPreviewInfo)

        layoutPrinterStatusBadge?.setOnClickListener {
            val paired = bluetoothService.getPairedDevices()
            val prefs = SecurePreferencesHelper.getPreferences(this)
            val currentAddr = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, "") ?: ""
            PrinterPickerBottomSheetDialog(this, paired, currentAddr) { name, address ->
                prefs.edit()
                    .putString(SettingsActivity.KEY_DEFAULT_PRINTER_NAME, name)
                    .putString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, address)
                    .apply()
                updateToolbarStatusBadge()
            }.show()
        }

        chipPaperWidth?.setOnClickListener {
            togglePaperWidth()
        }

        chipDitherMode?.setOnClickListener {
            showDitherModeDialog()
        }

        btnRetrySpooler?.setOnClickListener {
            flushOfflineSpoolerQueue()
        }

        layoutSpoolerBadge?.setOnClickListener {
            showSpoolerQueueDialog()
        }
    }

    /**
     * Mengatur padding atas AppBarLayout sesuai status bar HP dan bottomAppBar sesuai gesture/nav bar
     * agar konten tidak tertutup atau terpotong pada semua tipe layar (HP dan Tablet).
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        val bottomAppBar = findViewById<BottomAppBar>(R.id.bottomAppBar)
        bottomAppBar?.let { bar ->
            ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
                val navInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val baseHeight = resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
                val totalBottomHeight = baseHeight + navInsets.bottom
                v.layoutParams.height = totalBottomHeight
                v.setPadding(0, 0, 0, navInsets.bottom)
                v.requestLayout()

                (nestedScrollView.layoutParams as? CoordinatorLayout.LayoutParams)?.let { lp ->
                    lp.bottomMargin = totalBottomHeight
                    nestedScrollView.layoutParams = lp
                }
                (layoutFreeText.layoutParams as? CoordinatorLayout.LayoutParams)?.let { lp ->
                    lp.bottomMargin = totalBottomHeight
                    layoutFreeText.layoutParams = lp
                }
                (layoutPdfPreview.layoutParams as? CoordinatorLayout.LayoutParams)?.let { lp ->
                    lp.bottomMargin = totalBottomHeight
                    layoutPdfPreview.layoutParams = lp
                }
                (layoutImagePreview.layoutParams as? CoordinatorLayout.LayoutParams)?.let { lp ->
                    lp.bottomMargin = totalBottomHeight
                    layoutImagePreview.layoutParams = lp
                }
                insets
            }
        }
    }

    private fun setupToolbarAndDrawer() {
        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_address -> {
                    switchToMode(CurrentMode.FORM)
                    true
                }
                R.id.nav_free_text -> {
                    switchToMode(CurrentMode.FREE_TEXT)
                    true
                }
                R.id.nav_web_page -> {
                    startActivity(Intent(this, WebViewActivity::class.java))
                    true
                }
                R.id.nav_pdf_file -> {
                    if (selectedPdfUri == null) {
                        pdfPickerLauncher.launch("application/pdf")
                    } else {
                        switchToMode(CurrentMode.PDF)
                    }
                    true
                }
                R.id.nav_image_file -> {
                    if (selectedImageUri == null) {
                        imagePickerLauncher.launch("image/*")
                    } else {
                        switchToMode(CurrentMode.IMAGE)
                    }
                    true
                }
                R.id.nav_word_file -> {
                    showWordFileGuideDialog()
                    true
                }
                R.id.nav_qr_code -> {
                    startActivity(Intent(this, QrGeneratorActivity::class.java))
                    true
                }
                R.id.nav_label -> {
                    switchToMode(CurrentMode.FORM)
                    true
                }
                R.id.nav_print_history -> {
                    startActivity(Intent(this, PrintHistoryActivity::class.java))
                    true
                }
                R.id.nav_setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.nav_rating -> {
                    requestAppRating()
                    true
                }
                R.id.nav_contact_us -> {
                    openContactUs()
                    true
                }
                R.id.nav_exit -> {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.kt_dialog_exit_title))
                        .setMessage(getString(R.string.kt_dialog_exit_msg))
                        .setPositiveButton(R.string.kt_btn_exit) { _, _ -> finishAffinity() }
                        .setNegativeButton(R.string.batal, null)
                        .show()
                    true
                }
                else -> false
            }
        }
    }

    private fun requestAppRating() {
        try {
            val manager = ReviewManagerFactory.create(this)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(this, reviewInfo)
                    flow.addOnCompleteListener { _ ->
                        // Review flow finished
                    }
                } else {
                    openPlayStoreForRating()
                }
            }
        } catch (_: Exception) {
            openPlayStoreForRating()
        }
    }

    private fun openPlayStoreForRating() {
        val appPackageName = packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
            } catch (_: Exception) {
                showImportantError(getString(R.string.kt_play_store_unavailable))
            }
        }
    }

    private fun openContactUs() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@netraprint.com"))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.kt_contact_subject))
            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (_: Exception) {
                "1.0.0"
            }
            val deviceInfo = "\n\n---\nPerangkat: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nAndroid OS: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\nApp Version: $versionName"
            putExtra(Intent.EXTRA_TEXT, "Halo Tim Support NetraPrint,\n\n(Tuliskan kendala atau pertanyaan Anda di sini)\n$deviceInfo")
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.kt_contact_chooser)))
        } catch (_: Exception) {
            showImportantError(getString(R.string.kt_contact_email_unavailable))
        }
    }

    private fun showWordFileGuideDialog() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_status_bottom_sheet, null)
        bottomSheet.setContentView(view)

        val lottie = view.findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.lottieAnimationView)
        lottie.setAnimation("lottie_loading.json")
        lottie.repeatCount = 0
        lottie.pauseAnimation()

        view.findViewById<TextView>(R.id.tvStatusTitle).setText(R.string.kt_word_print_title)
        view.findViewById<TextView>(R.id.tvStatusMessage).setText(R.string.kt_word_print_message)

        val btn = view.findViewById<MaterialButton>(R.id.btnStatusAction)
        btn.visibility = View.VISIBLE
        btn.setText(R.string.kt_word_choose_pdf)
        btn.setOnClickListener {
            bottomSheet.dismiss()
            pdfPickerLauncher.launch("application/pdf")
        }
        bottomSheet.show()
    }

    private fun setupHeaderButtons() {
        val cardLogo = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardLogo)
        cardLogo?.setOnClickListener {
            // Subtle M3 micro-interaction animation
            cardLogo.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                cardLogo.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                logoPickerLauncher.launch("image/*")
            }.start()
        }
        cardLogo?.setOnLongClickListener {
            val logoFile = File(filesDir, "store_logo.png")
            if (logoFile.exists()) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.kt_dialog_delete_logo_title))
                    .setMessage(getString(R.string.kt_dialog_delete_logo_msg))
                    .setPositiveButton(R.string.kt_btn_delete) { _, _ ->
                        deleteStoreLogo()
                    }
                    .setNegativeButton(R.string.batal, null)
                    .show()
                true
            } else {
                false
            }
        }

        findViewById<ImageButton>(R.id.btnCameraLogo).setOnClickListener {
            launchCameraForTarget(CameraActionTarget.HEADER_LOGO_AND_BARCODE)
        }

        findViewById<ImageButton>(R.id.btnGalleryLogo).setOnClickListener {
            logoPickerLauncher.launch("image/*")
        }
    }

    private fun saveStoreLogo(bitmap: Bitmap) {
        try {
            val maxW = 512
            val maxH = 256
            val scaledBitmap = if (bitmap.width > maxW || bitmap.height > maxH) {
                val scale = minOf(maxW.toFloat() / bitmap.width, maxH.toFloat() / bitmap.height)
                val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val logoFile = File(filesDir, "store_logo.png")
            java.io.FileOutputStream(logoFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            displayStoreLogo(scaledBitmap)
            Toast.makeText(this, getString(R.string.kt_toast_logo_saved), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Log.e("MainActivity", "Unable to save store logo")
        }
    }

    private fun loadStoreLogo() {
        try {
            val logoFile = File(filesDir, "store_logo.png")
            if (logoFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
                if (bitmap != null) {
                    displayStoreLogo(bitmap)
                }
            }
        } catch (_: Exception) {
            Log.e("MainActivity", "Unable to load store logo")
        }
    }

    private fun displayStoreLogo(bitmap: Bitmap) {
        ivLogoPlaceholder.setImageBitmap(bitmap)
        ivLogoPlaceholder.alpha = 1.0f
        ivLogoPlaceholder.imageTintList = null
    }

    private fun deleteStoreLogo() {
        val logoFile = File(filesDir, "store_logo.png")
        if (logoFile.exists()) {
            logoFile.delete()
        }
        ivLogoPlaceholder.setImageResource(R.drawable.ic_action_camera)
        ivLogoPlaceholder.alpha = 1.0f
        ivLogoPlaceholder.imageTintList = ColorStateList.valueOf(Color.parseColor("#0F766E"))
        Toast.makeText(this, getString(R.string.kt_toast_logo_deleted), Toast.LENGTH_SHORT).show()
    }

    private fun launchCameraForTarget(target: CameraActionTarget) {
        activeCameraTarget = target
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraCapture() {
        try {
            val uri = createTempImageUri()
            tempPhotoUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("MainActivity", "Unable to prepare camera capture")
            Toast.makeText(this, getString(R.string.kt_toast_camera_open_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOcrSourceChooser(target: CameraActionTarget, targetTitle: String) {
        val options = arrayOf(
            getString(R.string.kt_ocr_take_photo),
            getString(R.string.kt_ocr_choose_gallery)
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.kt_ocr_scan_title, targetTitle))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCameraForTarget(target)
                    1 -> {
                        activeCameraTarget = target
                        ocrGalleryLauncher.launch("image/*")
                    }
                }
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun processOcrUri(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            if (bitmap != null) {
                processCameraResult(bitmap)
            } else {
                Toast.makeText(this, getString(R.string.kt_toast_image_process_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Log.e("MainActivity", "Unable to decode selected OCR image")
            Toast.makeText(this, getString(R.string.kt_toast_image_read_error), Toast.LENGTH_SHORT).show()
        } finally {
            if (uri == tempPhotoUri) {
                deleteTemporaryOcrPhoto()
            }
        }
    }

    private fun processCameraResult(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        when (activeCameraTarget) {
            CameraActionTarget.HEADER_LOGO_AND_BARCODE -> {
                saveStoreLogo(bitmap)
                val scanner = BarcodeScanning.getClient()
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        if (!barcodes.isNullOrEmpty()) {
                            val firstBarcode = barcodes[0]
                            val rawValue = firstBarcode.rawValue ?: firstBarcode.displayValue
                            if (!rawValue.isNullOrBlank()) {
                                etKodeBooking.setText(rawValue.trim())
                            }
                        }
                    }
            }
            CameraActionTarget.KODE_BOOKING_BARCODE -> {
                runOcrOrBarcodeOnBooking(inputImage)
            }
            CameraActionTarget.OCR_PENERIMA -> {
                runOcrOnImage(inputImage, etPenerima, "Penerima")
            }
            CameraActionTarget.OCR_PENGIRIM -> {
                runOcrOnImage(inputImage, etPengirim, "Pengirim")
            }
            CameraActionTarget.OCR_KODE_BOOKING -> {
                runOcrOrBarcodeOnBooking(inputImage)
            }
            CameraActionTarget.OCR_ISI_PAKET -> {
                runOcrOnImage(inputImage, etIsiPaket, "Isi Paket")
            }
            CameraActionTarget.OCR_CATATAN -> {
                runOcrOnImage(inputImage, etCatatan, "Catatan")
            }
        }
    }

    private fun runOcrOnImage(image: InputImage, targetEditText: EditText, fieldName: String) {
        val progressDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.kt_ocr_scanning_title, fieldName))
            .setMessage(getString(R.string.kt_ocr_processing_msg))
            .setCancelable(false)
            .create()
        progressDialog.show()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                progressDialog.dismiss()
                val text = visionText.text.trim()
                if (text.isNotBlank()) {
                    targetEditText.setText(text)
                    Toast.makeText(this, getString(R.string.kt_toast_scan_success, fieldName), Toast.LENGTH_SHORT).show()
                } else {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.kt_ocr_no_text_title))
                        .setMessage(getString(R.string.kt_ocr_no_text_msg))
                        .setPositiveButton(R.string.kt_btn_ok, null)
                        .show()
                }
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                Log.e("MainActivity", "Text recognition failed")
                Toast.makeText(this, getString(R.string.kt_toast_ocr_text_failed), Toast.LENGTH_LONG).show()
            }
    }

    private fun runOcrOrBarcodeOnBooking(image: InputImage) {
        val progressDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.kt_scan_resi_title))
            .setMessage(getString(R.string.kt_scan_resi_msg))
            .setCancelable(false)
            .create()
        progressDialog.show()

        val barcodeScanner = BarcodeScanning.getClient()
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val barcodeVal = barcodes.firstOrNull()?.let { it.rawValue ?: it.displayValue }?.trim()
                if (!barcodeVal.isNullOrBlank()) {
                    progressDialog.dismiss()
                    etKodeBooking.setText(barcodeVal)
                    Toast.makeText(this, getString(R.string.kt_toast_barcode_scanned, barcodeVal), Toast.LENGTH_SHORT).show()
                } else {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            progressDialog.dismiss()
                            val text = visionText.text.trim()
                            if (text.isNotBlank()) {
                                val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                                val candidate = lines.firstOrNull { it.matches(Regex("^[A-Za-z0-9\\-]+$")) } ?: lines.firstOrNull() ?: text
                                etKodeBooking.setText(candidate)
                                Toast.makeText(this, getString(R.string.kt_toast_resi_scanned, candidate), Toast.LENGTH_SHORT).show()
                            } else {
                                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                    .setTitle(getString(R.string.kt_resi_not_found_title))
                                    .setMessage(getString(R.string.kt_resi_not_found_msg))
                                    .setPositiveButton(R.string.kt_btn_ok, null)
                                    .show()
                            }
                        }
                        .addOnFailureListener {
                            progressDialog.dismiss()
                            Log.e("MainActivity", "Tracking number recognition failed")
                            Toast.makeText(this, getString(R.string.kt_toast_ocr_text_failed), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        progressDialog.dismiss()
                        val text = visionText.text.trim()
                        if (text.isNotBlank()) {
                            val candidate = text.lines().firstOrNull { it.isNotBlank() } ?: text
                            etKodeBooking.setText(candidate)
                            Toast.makeText(this, getString(R.string.kt_toast_resi_scanned, candidate), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, getString(R.string.kt_toast_no_text_barcode), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        progressDialog.dismiss()
                        Log.e("MainActivity", "Barcode and text scanning failed")
                        Toast.makeText(this, getString(R.string.kt_toast_scan_failed), Toast.LENGTH_LONG).show()
                    }
            }
    }

    private fun saveCurrentRecipient() {
        val text = etPenerima.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, getString(R.string.kt_toast_recipient_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = SecurePreferencesHelper.getPreferences(this)
        val rawSet = prefs.getStringSet("saved_recipients_list", emptySet()) ?: emptySet()
        val mutableList = rawSet.toMutableList()
        if (!mutableList.contains(text)) {
            mutableList.add(0, text)
            if (mutableList.size > 50) mutableList.removeAt(mutableList.size - 1)
            prefs.edit().putStringSet("saved_recipients_list", mutableList.toSet()).apply()
            Toast.makeText(this, getString(R.string.kt_toast_recipient_saved), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.kt_toast_recipient_exists), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSavedRecipientsDialog() {
        val prefs = SecurePreferencesHelper.getPreferences(this)
        val rawSet = prefs.getStringSet("saved_recipients_list", emptySet()) ?: emptySet()
        val list = rawSet.toList().sorted()

        if (list.isEmpty()) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.kt_recipient_book_title))
                .setMessage(getString(R.string.kt_recipient_book_empty_msg))
                .setPositiveButton(R.string.kt_btn_understood, null)
                .show()
            return
        }

        val items = list.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.kt_recipient_choose_title, items.size))
            .setItems(items) { _, which ->
                val selected = items[which]
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.kt_saved_address_title)
                    .setMessage(selected)
                    .setPositiveButton(R.string.kt_saved_address_use) { _, _ ->
                        etPenerima.setText(selected)
                        Toast.makeText(this, getString(R.string.kt_toast_address_loaded), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.kt_btn_delete) { _, _ ->
                        val updated = rawSet.toMutableSet().apply { remove(selected) }
                        prefs.edit().putStringSet("saved_recipients_list", updated).apply()
                        Toast.makeText(this, getString(R.string.kt_toast_address_removed), Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton(R.string.batal, null)
                    .show()
            }
            .setNeutralButton(R.string.kt_delete_all_action) { _, _ ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.kt_delete_all_title))
                    .setMessage(getString(R.string.kt_delete_all_msg))
                    .setPositiveButton(R.string.kt_delete_all_action) { _, _ ->
                        prefs.edit().remove("saved_recipients_list").apply()
                        Toast.makeText(this, getString(R.string.kt_toast_all_addresses_removed), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.batal, null)
                    .show()
            }
            .setPositiveButton(R.string.kt_btn_close, null)
            .show()
    }

    private fun setupFormSection() {
        // --- 1. Penerima ---
        findViewById<ImageButton>(R.id.btnSavePenerima)?.setOnClickListener {
            saveCurrentRecipient()
        }
        findViewById<ImageButton>(R.id.btnFolderPenerima)?.setOnClickListener {
            showSavedRecipientsDialog()
        }
        findViewById<ImageButton>(R.id.btnCopyPenerima).setOnClickListener {
            copyToClipboard("Penerima", etPenerima.text.toString())
        }
        findViewById<ImageButton>(R.id.btnPastePenerima).setOnClickListener {
            pasteFromClipboard(etPenerima)
        }
        findViewById<ImageButton>(R.id.btnSpeakerPenerima).setOnClickListener {
            speakText(etPenerima.text.toString())
        }
        findViewById<ImageButton>(R.id.btnOcrPenerima).setOnClickListener {
            showOcrSourceChooser(CameraActionTarget.OCR_PENERIMA, "Penerima")
        }

        // --- 2. Pengirim ---
        findViewById<ImageButton>(R.id.btnCopyPengirim).setOnClickListener {
            copyToClipboard("Pengirim", etPengirim.text.toString())
        }
        findViewById<ImageButton>(R.id.btnPastePengirim).setOnClickListener {
            pasteFromClipboard(etPengirim)
        }
        findViewById<ImageButton>(R.id.btnSpeakerPengirim).setOnClickListener {
            speakText(etPengirim.text.toString())
        }
        findViewById<ImageButton>(R.id.btnOcrPengirim).setOnClickListener {
            showOcrSourceChooser(CameraActionTarget.OCR_PENGIRIM, "Pengirim")
        }

        // --- 3. Jasa Kurir (Standard Dropdown) ---
        val courierOptions = resources.getStringArray(R.array.courier_options)
        val courierAdapter = object : ArrayAdapter<String>(this, R.layout.item_dropdown, courierOptions) {
            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        return FilterResults().apply {
                            values = courierOptions
                            count = courierOptions.size
                        }
                    }
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        notifyDataSetChanged()
                    }
                }
            }
        }
        actvJasaKurir.setAdapter(courierAdapter)
        actvJasaKurir.setOnClickListener {
            actvJasaKurir.showDropDown()
        }
        layoutJasaKurir?.setEndIconOnClickListener {
            actvJasaKurir.showDropDown()
        }
        actvJasaKurir.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position).toString()
            actvJasaKurir.setText(selected, false)
            actvJasaKurir.clearFocus()
            layoutJasaKurir?.clearFocus()
        }
        actvJasaKurir.setOnDismissListener {
            actvJasaKurir.clearFocus()
            layoutJasaKurir?.clearFocus()
        }

        // --- 4. No. Resi (Scan Barcode & OCR) ---
        findViewById<ImageButton>(R.id.btnBarcodeKodeBooking).setOnClickListener {
            barcodeScannerLauncher.launch(Intent(this, BarcodeScannerActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnPasteKodeBooking).setOnClickListener {
            pasteFromClipboard(etKodeBooking)
        }
        findViewById<ImageButton>(R.id.btnOcrKodeBooking).setOnClickListener {
            showOcrSourceChooser(CameraActionTarget.OCR_KODE_BOOKING, "No. Resi")
        }

        // --- 5. Isi Paket ---
        findViewById<ImageButton>(R.id.btnCopyIsiPaket).setOnClickListener {
            copyToClipboard("Isi Paket", etIsiPaket.text.toString())
        }
        findViewById<ImageButton>(R.id.btnPasteIsiPaket).setOnClickListener {
            pasteFromClipboard(etIsiPaket)
        }
        findViewById<ImageButton>(R.id.btnSpeakerIsiPaket).setOnClickListener {
            speakText(etIsiPaket.text.toString())
        }
        findViewById<ImageButton>(R.id.btnOcrIsiPaket).setOnClickListener {
            showOcrSourceChooser(CameraActionTarget.OCR_ISI_PAKET, "Isi Paket")
        }

        // --- 6. Catatan ---
        findViewById<ImageButton>(R.id.btnCopyCatatan).setOnClickListener {
            copyToClipboard("Catatan", etCatatan.text.toString())
        }
        findViewById<ImageButton>(R.id.btnPasteCatatan).setOnClickListener {
            pasteFromClipboard(etCatatan)
        }
        findViewById<ImageButton>(R.id.btnSpeakerCatatan).setOnClickListener {
            speakText(etCatatan.text.toString())
        }
        findViewById<ImageButton>(R.id.btnOcrCatatan).setOnClickListener {
            showOcrSourceChooser(CameraActionTarget.OCR_CATATAN, "Catatan")
        }

        // --- 7. Font Dropdown & Typeface Selection ---
        val fontOptions = resources.getStringArray(R.array.font_options)
        val fontAdapter = object : ArrayAdapter<String>(this, R.layout.item_dropdown, fontOptions) {
            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        return FilterResults().apply {
                            values = fontOptions
                            count = fontOptions.size
                        }
                    }
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        notifyDataSetChanged()
                    }
                }
            }
        }
        actvFont.setAdapter(fontAdapter)
        actvFont.setText(fontOptions[0], false)
        actvFont.setOnClickListener {
            actvFont.showDropDown()
        }
        layoutFont?.setEndIconOnClickListener {
            actvFont.showDropDown()
        }

        actvFont.setOnItemClickListener { parent, _, position, _ ->
            val fontName = parent.getItemAtPosition(position).toString()
            applySelectedFont(fontName)
            actvFont.clearFocus()
            layoutFont?.clearFocus()
        }
        actvFont.setOnDismissListener {
            actvFont.clearFocus()
            layoutFont?.clearFocus()
        }

        // --- 8. Font Size Slider (Material 3) ---
        val tvFontSize = findViewById<TextView>(R.id.tvFontSize)
        val sliderFontSize = findViewById<Slider>(R.id.sliderFontSize)
        val cbAutoFontSize = findViewById<CheckBox>(R.id.cbAutoFontSize)

        sliderFontSize.addOnChangeListener { _, value, _ ->
            tvFontSize.text = value.toInt().toString()
        }

        cbAutoFontSize.setOnCheckedChangeListener { _, isChecked ->
            sliderFontSize.isEnabled = !isChecked
        }

        val formPreviewWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (currentMode == CurrentMode.FORM) {
                    updateThermalPreview()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etPenerima.addTextChangedListener(formPreviewWatcher)
        etPengirim.addTextChangedListener(formPreviewWatcher)
        actvJasaKurir.addTextChangedListener(formPreviewWatcher)
        etKodeBooking.addTextChangedListener(formPreviewWatcher)
        etIsiPaket.addTextChangedListener(formPreviewWatcher)
        etCatatan.addTextChangedListener(formPreviewWatcher)

        etFreeText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (currentMode == CurrentMode.FREE_TEXT) {
                    updateThermalPreview()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applySelectedFont(fontName: String) {
        currentSelectedFont = fontName
        val lower = fontName.lowercase()
        val typeface: Typeface = when {
            lower.contains("roboto") -> Typeface.create("sans-serif", Typeface.NORMAL)
            lower.contains("inter") -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            lower.contains("monospace") || lower.contains("courier") -> Typeface.MONOSPACE
            lower.contains("oswald") || lower.contains("condensed header") -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
            lower.contains("condensed") || lower.contains("font b") -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            lower.contains("serif") || lower.contains("formal") -> Typeface.SERIF
            lower.contains("casual") || lower.contains("rounded") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Typeface.create("casual", Typeface.NORMAL) else Typeface.SANS_SERIF
            }
            else -> Typeface.DEFAULT
        }

        etPenerima.typeface = typeface
        etPengirim.typeface = typeface
        etKodeBooking.typeface = typeface
        etIsiPaket.typeface = typeface
        etCatatan.typeface = typeface
        if (::etFreeText.isInitialized) {
            etFreeText.typeface = typeface
        }
        tvThermalPreviewText?.typeface = typeface
        updateThermalPreview()
    }

    private fun setupPdfModeViews() {
        btnChoosePdf.setOnClickListener {
            pdfPickerLauncher.launch("application/pdf")
        }
        btnChangePdfDoc.setOnClickListener {
            pdfPickerLauncher.launch("application/pdf")
        }

        btnPrevPdfPage.setOnClickListener {
            val renderer = pdfRenderer ?: return@setOnClickListener
            if (currentPdfPageIndex > 0) {
                currentPdfPageIndex--
                renderCurrentPdfPage()
            }
        }

        btnNextPdfPage.setOnClickListener {
            val renderer = pdfRenderer ?: return@setOnClickListener
            if (currentPdfPageIndex < renderer.pageCount - 1) {
                currentPdfPageIndex++
                renderCurrentPdfPage()
            }
        }

        sliderPdfZoom.addOnChangeListener { _, value, _ ->
            val scale = (value.coerceAtLeast(30f)) / 100f
            tvPdfZoom.text = "${(scale * 100).toInt()}%"
            applyZoomToImageView(ivPdfPreview, currentPdfPageBitmap, scale)
        }
    }

    private fun setupImageModeViews() {
        btnChooseImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
        btnChangeImageDoc.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        sliderImageZoom.addOnChangeListener { _, value, _ ->
            val scale = (value.coerceAtLeast(30f)) / 100f
            tvImageZoom.text = "${(scale * 100).toInt()}%"
            applyZoomToImageView(ivImagePreview, originalImageBitmap, scale)
        }
    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.tabText).setOnClickListener {
            switchToMode(CurrentMode.FREE_TEXT)
        }
        findViewById<LinearLayout>(R.id.tabLabel).setOnClickListener {
            switchToMode(CurrentMode.FORM)
        }
        findViewById<LinearLayout>(R.id.tabImage).setOnClickListener {
            if (selectedImageUri == null) {
                imagePickerLauncher.launch("image/*")
            } else {
                switchToMode(CurrentMode.IMAGE)
            }
        }
        findViewById<LinearLayout>(R.id.tabPdf).setOnClickListener {
            if (selectedPdfUri == null) {
                pdfPickerLauncher.launch("application/pdf")
            } else {
                switchToMode(CurrentMode.PDF)
            }
        }
        switchToMode(CurrentMode.FORM)
    }

    private fun switchToMode(mode: CurrentMode, featureName: String = "") {
        currentMode = mode
        updateBottomNavState(mode)
        when (mode) {
            CurrentMode.FORM -> {
                toolbar.title = getString(R.string.kt_toolbar_shipping_address)
                navigationView.setCheckedItem(R.id.nav_label)
                nestedScrollView.visibility = View.VISIBLE
                layoutFreeText.visibility = View.GONE
                layoutPlaceholderFeature.visibility = View.GONE
                layoutPdfPreview.visibility = View.GONE
                layoutImagePreview.visibility = View.GONE
            }
            CurrentMode.FREE_TEXT -> {
                toolbar.title = getString(R.string.kt_toolbar_free_text)
                navigationView.setCheckedItem(R.id.nav_free_text)
                nestedScrollView.visibility = View.GONE
                layoutFreeText.visibility = View.VISIBLE
                layoutPlaceholderFeature.visibility = View.GONE
                layoutPdfPreview.visibility = View.GONE
                layoutImagePreview.visibility = View.GONE
            }
            CurrentMode.PLACEHOLDER -> {
                toolbar.title = featureName
                tvPlaceholderTitle.text = featureName
                tvPlaceholderDesc.text = getString(R.string.kt_placeholder_dev_msg, featureName)
                nestedScrollView.visibility = View.GONE
                layoutFreeText.visibility = View.GONE
                layoutPlaceholderFeature.visibility = View.VISIBLE
                layoutPdfPreview.visibility = View.GONE
                layoutImagePreview.visibility = View.GONE
            }
            CurrentMode.PDF -> {
                val fileName = selectedPdfUri?.let { getFileNameFromUri(it) }
                toolbar.title = if (fileName != null) getString(R.string.kt_toolbar_pdf_title, fileName) else getString(R.string.kt_toolbar_pdf_choose)
                navigationView.setCheckedItem(R.id.nav_pdf_file)
                nestedScrollView.visibility = View.GONE
                layoutFreeText.visibility = View.GONE
                layoutPlaceholderFeature.visibility = View.GONE
                layoutPdfPreview.visibility = View.VISIBLE
                layoutImagePreview.visibility = View.GONE
            }
            CurrentMode.IMAGE -> {
                val fileName = selectedImageUri?.let { getFileNameFromUri(it) }
                toolbar.title = if (fileName != null) getString(R.string.kt_toolbar_image_title, fileName) else getString(R.string.kt_toolbar_image_choose)
                navigationView.setCheckedItem(R.id.nav_image_file)
                nestedScrollView.visibility = View.GONE
                layoutFreeText.visibility = View.GONE
                layoutPlaceholderFeature.visibility = View.GONE
                layoutPdfPreview.visibility = View.GONE
                layoutImagePreview.visibility = View.VISIBLE
            }
        }
    }

    private fun updateBottomNavState(mode: CurrentMode) {
        val tabText = findViewById<LinearLayout>(R.id.tabText)
        val ivTabText = findViewById<ImageView>(R.id.ivTabText)
        val tvTabText = findViewById<TextView>(R.id.tvTabText)

        val tabLabel = findViewById<LinearLayout>(R.id.tabLabel)
        val ivTabLabel = findViewById<ImageView>(R.id.ivTabLabel)
        val tvTabLabel = findViewById<TextView>(R.id.tvTabLabel)

        val tabImage = findViewById<LinearLayout>(R.id.tabImage)
        val ivTabImage = findViewById<ImageView>(R.id.ivTabImage)
        val tvTabImage = findViewById<TextView>(R.id.tvTabImage)

        val tabPdf = findViewById<LinearLayout>(R.id.tabPdf)
        val ivTabPdf = findViewById<ImageView>(R.id.ivTabPdf)
        val tvTabPdf = findViewById<TextView>(R.id.tvTabPdf)

        val activeColor = getColor(R.color.nav_active)
        val inactiveColor = getColor(R.color.nav_inactive)
        listOf(tvTabText, tvTabLabel, tvTabImage, tvTabPdf).forEach { label ->
            setNavigationLabelWeight(label, 400)
        }
        listOf(tabText, tabLabel, tabImage, tabPdf).forEach {
            it?.setBackgroundResource(R.drawable.bg_nav_pill_inactive)
        }

        ivTabText?.setColorFilter(inactiveColor)
        tvTabText?.setTextColor(inactiveColor)

        ivTabLabel?.setColorFilter(inactiveColor)
        tvTabLabel?.setTextColor(inactiveColor)

        ivTabImage?.setColorFilter(inactiveColor)
        tvTabImage?.setTextColor(inactiveColor)

        ivTabPdf?.setColorFilter(inactiveColor)
        tvTabPdf?.setTextColor(inactiveColor)

        val activeTab = when (mode) {
            CurrentMode.FREE_TEXT -> {
                ivTabText?.setColorFilter(activeColor)
                tvTabText?.setTextColor(activeColor)
                setNavigationLabelWeight(tvTabText, 500)
                tabText
            }
            CurrentMode.FORM -> {
                ivTabLabel?.setColorFilter(activeColor)
                tvTabLabel?.setTextColor(activeColor)
                setNavigationLabelWeight(tvTabLabel, 500)
                tabLabel
            }
            CurrentMode.IMAGE -> {
                ivTabImage?.setColorFilter(activeColor)
                tvTabImage?.setTextColor(activeColor)
                setNavigationLabelWeight(tvTabImage, 500)
                tabImage
            }
            CurrentMode.PDF -> {
                ivTabPdf?.setColorFilter(activeColor)
                tvTabPdf?.setTextColor(activeColor)
                setNavigationLabelWeight(tvTabPdf, 500)
                tabPdf
            }
            else -> null
        }
        activeTab?.let { tab ->
            tab.setBackgroundResource(R.drawable.bg_nav_pill_active)
            tab.scaleX = 0.90f
            tab.scaleY = 0.90f
            tab.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(220)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
        }
    }

    private fun setNavigationLabelWeight(label: TextView?, weight: Int) {
        label ?: return
        label.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(label.typeface, weight, false)
        } else {
            Typeface.create(label.typeface, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    // ==========================================
    // PDF RENDERING & ZOOM LOGIC
    // ==========================================

    private fun loadPdfDocument(uri: Uri) {
        closePdfRenderer()
        selectedPdfUri = uri
        val fileName = getFileNameFromUri(uri) ?: "Dokumen.pdf"
        tvPdfFileName.text = fileName

        try {
            val pfd = if (uri.scheme == "file") {
                val filePath = uri.path ?: uri.toString().removePrefix("file://")
                ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                contentResolver.openFileDescriptor(uri, "r")
            }
            if (pfd == null) {
                showImportantError(getString(R.string.kt_pdf_open_failed))
                return
            }
            pdfFileDescriptor = pfd
            pdfRenderer = PdfRenderer(pfd)
            currentPdfPageIndex = 0

            layoutPdfPlaceholder.visibility = View.GONE
            layoutPdfContent.visibility = View.VISIBLE

            renderCurrentPdfPage()
            switchToMode(CurrentMode.PDF)
        } catch (_: Exception) {
            showImportantError(getString(R.string.kt_pdf_open_failed))
        }
    }

    private fun renderCurrentPdfPage() {
        val renderer = pdfRenderer ?: return
        if (renderer.pageCount == 0) return

        try {
            val page = renderer.openPage(currentPdfPageIndex)
            tvPdfPageIndicator.text = "${currentPdfPageIndex + 1} / ${renderer.pageCount}"

            val renderWidth = page.width * 2
            val renderHeight = page.height * 2
            val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            currentPdfPageBitmap = bitmap

            // Reset zoom to 100%
            sliderPdfZoom.value = 100f
            tvPdfZoom.text = "100%"
            applyZoomToImageView(ivPdfPreview, bitmap, 1.0f)
        } catch (_: Exception) {
            showImportantError(getString(R.string.kt_pdf_render_failed))
        }
    }

    private fun closePdfRenderer() {
        try {
            pdfRenderer?.close()
        } catch (_: Exception) {}
        pdfRenderer = null

        try {
            pdfFileDescriptor?.close()
        } catch (_: Exception) {}
        pdfFileDescriptor = null

        managedSystemPrintPdf?.let { file ->
            if (file.exists() && !file.delete()) {
                Log.w("MainActivity", "Unable to remove completed system print cache file")
            }
        }
        managedSystemPrintPdf = null
    }

    // ==========================================
    // IMAGE LOADING & ZOOM LOGIC
    // ==========================================

    private fun loadImageDocument(uri: Uri) {
        selectedImageUri = uri
        val fileName = getFileNameFromUri(uri) ?: "Gambar.png"
        tvImageFileName.text = fileName

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (_: Exception) {
                null
            }

            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    originalImageBitmap = bitmap
                    layoutImagePlaceholder.visibility = View.GONE
                    layoutImageContent.visibility = View.VISIBLE
                    sliderImageZoom.value = 100f
                    tvImageZoom.text = "100%"
                    applyZoomToImageView(ivImagePreview, bitmap, 1.0f)
                    switchToMode(CurrentMode.IMAGE)
                } else {
                    showImportantError(getString(R.string.kt_image_read_failed))
                }
            }
        }
    }

    private fun applyZoomToImageView(imageView: ImageView, bitmap: Bitmap?, scale: Float) {
        if (bitmap == null) return

        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setImageBitmap(bitmap)

        val params = imageView.layoutParams
        if (scale <= 1.0f) {
            params.width = FrameLayout.LayoutParams.WRAP_CONTENT
            params.height = FrameLayout.LayoutParams.WRAP_CONTENT
        } else {
            params.width = (bitmap.width * scale * 0.5f).toInt()
            params.height = (bitmap.height * scale * 0.5f).toInt()
        }
        if (params is FrameLayout.LayoutParams) {
            params.gravity = Gravity.CENTER
        }
        imageView.layoutParams = params
        imageView.requestLayout()
    }

    // ==========================================
    // PRINTING FLOW
    // ==========================================

    private fun setupFabPrint() {
        findViewById<View>(R.id.fabPrint).setOnClickListener {
            val prefs = SecurePreferencesHelper.getPreferences(this)
            val connType = prefs.getString(SettingsActivity.KEY_CONNECTION_TYPE, "Bluetooth")

            if (connType == "Wi-Fi") {
                if (currentMode == CurrentMode.PDF) {
                    val renderer = pdfRenderer
                    if (renderer == null || renderer.pageCount == 0) {
                        showImportantError(getString(R.string.kt_important_error_pdf_first))
                        return@setOnClickListener
                    }
                    PageRangeDialog(this, renderer.pageCount) { selectedPages ->
                        executeWifiPrint(selectedPages)
                    }.show()
                } else {
                    executeWifiPrint(null)
                }
            } else {
                if (checkAndRequestBluetoothPermissions()) {
                    if (currentMode == CurrentMode.PDF) {
                        val renderer = pdfRenderer
                        if (renderer == null || renderer.pageCount == 0) {
                            showImportantError(getString(R.string.kt_important_error_pdf_first))
                            return@setOnClickListener
                        }
                        PageRangeDialog(this, renderer.pageCount) { selectedPages ->
                            handlePrintAction(selectedPages)
                        }.show()
                    } else {
                        handlePrintAction(null)
                    }
                }
            }
        }
    }

    private fun executeWifiPrint(selectedPdfPages: List<Int>? = null) {
        val prefs = SecurePreferencesHelper.getPreferences(this)
        val ip = prefs.getString(SettingsActivity.KEY_WIFI_IP, "")?.trim() ?: ""
        val port = prefs.getString(SettingsActivity.KEY_WIFI_PORT, "9100")?.trim()?.toIntOrNull() ?: 9100
        val printWidth = prefs.getInt(SettingsActivity.KEY_PRINT_WIDTH, 384)
        val autoCut = prefs.getBoolean(SettingsActivity.KEY_AUTO_CUT, true)

        if (ip.isBlank()) {
            StatusBottomSheetDialog(this).showError(
                title = getString(R.string.kt_dialog_wifi_ip_title),
                message = getString(R.string.kt_dialog_wifi_ip_msg),
                actionText = getString(R.string.kt_btn_open_settings),
                secondaryText = getString(R.string.kt_btn_close)
            ) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            return
        }

        lifecycleScope.launch {
            val statusDialog = StatusBottomSheetDialog(this@MainActivity)
            try {
                if (currentMode == CurrentMode.PDF) {
                    val renderer = pdfRenderer
                    if (renderer == null || renderer.pageCount == 0) {
                        showImportantError(getString(R.string.kt_important_error_pdf_first))
                        return@launch
                    }

                    val pagesToPrint = selectedPdfPages ?: (0 until renderer.pageCount).toList()
                    val totalToPrint = pagesToPrint.size

                    statusDialog.showLoading(
                        title = getString(R.string.kt_sbs_default_loading_title),
                        message = getString(R.string.kt_sbs_default_loading_msg),
                        showProgress = true
                    )

                    val pagesItems = withContext(Dispatchers.IO) {
                        val list = mutableListOf<BluetoothService.PrintPageItem>()
                        var renderFailed = false
                        for (pageIndex in pagesToPrint) {
                            var page: PdfRenderer.Page? = null
                            var bitmap: Bitmap? = null
                            try {
                                val openedPage = renderer.openPage(pageIndex)
                                page = openedPage
                                val renderWidth = openedPage.width * 2
                                val renderHeight = openedPage.height * 2
                                val renderedBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                                bitmap = renderedBitmap
                                val canvas = Canvas(renderedBitmap)
                                canvas.drawColor(Color.WHITE)
                                openedPage.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val pageRaster = bluetoothService.createImageBytes(renderedBitmap, printWidth, autoCut, currentDitherMode)
                                list.add(BluetoothService.PrintPageItem(pageIndex + 1, pageRaster))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                renderFailed = true
                                Log.w("MainActivity", "Unable to render a selected PDF page")
                            } finally {
                                page?.close()
                                bitmap?.recycle()
                            }
                        }
                        if (renderFailed) {
                            list.forEach { it.bytes.fill(0) }
                            emptyList()
                        } else {
                            list
                        }
                    }

                    if (pagesItems.isEmpty()) {
                        statusDialog.dismiss()
                        showImportantError(getString(R.string.kt_important_error_pdf_render_fail))
                        return@launch
                    }

                    val result = bluetoothService.printMultiplePagesWifi(
                        ipAddress = ip,
                        port = port,
                        pages = pagesItems,
                        delayBetweenPagesMs = 500
                    ) { currentStep, totalSteps, actualPdfPage ->
                        withContext(Dispatchers.Main) {
                            val percent = (currentStep * 100) / totalSteps
                            statusDialog.updateProgress(
                                getString(R.string.kt_progress_pdf_page, currentStep, totalSteps, actualPdfPage),
                                percent
                            )
                        }
                    }

                    try {
                        if (result.isSuccess) {
                            printHistoryManager.addEntry(
                                title = getString(R.string.kt_job_pdf),
                                printType = "PDF",
                                targetName = ip,
                                targetInfo = port.toString(),
                                status = PrintHistoryItem.Status.SUCCESS,
                                copies = totalToPrint
                            )
                            statusDialog.showSuccess(
                                title = getString(R.string.kt_sbs_default_success_title),
                                message = getString(R.string.kt_pdf_print_success, totalToPrint)
                            )
                        } else {
                            val jobTitle = getString(R.string.kt_job_pdf)
                            printHistoryManager.addEntry(
                                title = jobTitle,
                                printType = "PDF",
                                targetName = ip,
                                targetInfo = port.toString(),
                                status = PrintHistoryItem.Status.QUEUED_OFFLINE,
                                copies = 1
                            )
                            val combinedBytes = ByteArrayOutputStream().apply {
                                for (item in pagesItems) {
                                    write(item.bytes)
                                }
                            }.toByteArray()
                            try {
                                printSpoolerManager.enqueueData(
                                    title = jobTitle,
                                    printType = "PDF",
                                    data = combinedBytes,
                                    copies = 1,
                                    targetWifiIp = ip,
                                    targetWifiPort = port
                                )
                            } finally {
                                combinedBytes.fill(0)
                            }
                            statusDialog.showError(
                                title = getString(R.string.kt_wifi_printer_unreachable_title),
                                message = getString(R.string.kt_print_failed_queued_message, jobTitle),
                                actionText = getString(R.string.kt_spooler_view_queue_action),
                                secondaryText = getString(R.string.kt_btn_close)
                            ) {
                                showSpoolerQueueDialog()
                            }
                        }
                    } finally {
                        pagesItems.forEach { it.bytes.fill(0) }
                    }
                    return@launch
                }

                val printBytes = when (currentMode) {
                    CurrentMode.FORM -> {
                        bluetoothService.createShippingLabelBytes(
                            penerima = etPenerima.text.toString(),
                            pengirim = etPengirim.text.toString(),
                            kurir = actvJasaKurir.text.toString(),
                            kodeBooking = etKodeBooking.text.toString(),
                            isiPaket = etIsiPaket.text.toString(),
                            catatan = etCatatan.text.toString(),
                            autoCut = autoCut,
                            printWidthDots = printWidth,
                            fontName = currentSelectedFont
                        )
                    }
                    CurrentMode.FREE_TEXT -> {
                        val text = etFreeText.text.toString().trim()
                        if (text.isEmpty()) {
                            showImportantError(getString(R.string.kt_important_error_text_first))
                            return@launch
                        }
                        bluetoothService.createFreeTextBytes(text, autoCut)
                    }
                    CurrentMode.IMAGE -> {
                        val bitmap = originalImageBitmap
                        if (bitmap == null) {
                            showImportantError(getString(R.string.kt_important_error_image_first))
                            return@launch
                        }
                        bluetoothService.createImageBytes(bitmap, printWidth, autoCut, currentDitherMode)
                    }
                    CurrentMode.PDF -> ByteArray(0)
                    CurrentMode.PLACEHOLDER -> {
                        showImportantError(getString(R.string.fitur_sedang_dalam_pengembangan))
                        return@launch
                    }
                }

                statusDialog.showLoading(
                    title = getString(R.string.kt_wifi_sending_title),
                    message = getString(R.string.kt_loading_connect_msg, ip, port),
                    showProgress = false
                )

                try {
                    val result = bluetoothService.printDataWifi(ip, port, printBytes)
                    if (result.isSuccess) {
                        val jobTitle = when (currentMode) {
                            CurrentMode.FORM -> getString(R.string.kt_job_shipping_label)
                            CurrentMode.FREE_TEXT -> getString(R.string.kt_job_free_text)
                            CurrentMode.IMAGE -> getString(R.string.kt_job_image)
                            else -> getString(R.string.kt_job_document)
                        }
                        printHistoryManager.addEntry(
                            title = jobTitle,
                            printType = currentMode.name,
                            targetName = ip,
                            targetInfo = port.toString(),
                            status = PrintHistoryItem.Status.SUCCESS,
                            copies = 1
                        )
                        statusDialog.showSuccess(
                            title = getString(R.string.kt_success_printed_title),
                            message = getString(R.string.kt_wifi_print_success_msg, ip, port)
                        )
                    } else {
                        val jobTitle = when (currentMode) {
                            CurrentMode.FORM -> getString(R.string.kt_job_shipping_label)
                            CurrentMode.FREE_TEXT -> getString(R.string.kt_job_free_text)
                            CurrentMode.IMAGE -> getString(R.string.kt_job_image)
                            CurrentMode.PDF -> getString(R.string.kt_job_pdf)
                            else -> getString(R.string.kt_job_document)
                        }
                        printHistoryManager.addEntry(
                            title = jobTitle,
                            printType = currentMode.name,
                            targetName = ip,
                            targetInfo = port.toString(),
                            status = PrintHistoryItem.Status.QUEUED_OFFLINE,
                            copies = 1
                        )
                        printSpoolerManager.enqueueData(
                            title = jobTitle,
                            printType = currentMode.name,
                            data = printBytes,
                            copies = 1,
                            targetWifiIp = ip,
                            targetWifiPort = port
                        )
                        statusDialog.showError(
                            title = getString(R.string.kt_wifi_printer_unreachable_title),
                            message = getString(R.string.kt_print_failed_queued_message, jobTitle),
                            actionText = getString(R.string.kt_spooler_view_queue_action),
                            secondaryText = getString(R.string.kt_btn_close)
                        ) {
                            showSpoolerQueueDialog()
                        }
                    }
                } finally {
                    printBytes.fill(0)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                statusDialog.showError(
                    title = getString(R.string.kt_dialog_wifi_error_title),
                    message = getString(R.string.kt_wifi_process_error)
                )
            }
        }
    }

    private fun handlePrintAction(selectedPdfPages: List<Int>? = null) {
        if (!bluetoothService.isBluetoothSupported) {
            showImportantError(getString(R.string.kt_bt_not_supported_msg))
            return
        }

        if (!bluetoothService.isBluetoothEnabled) {
            StatusBottomSheetDialog(this).showError(
                title = getString(R.string.kt_dialog_bluetooth_off_title),
                message = getString(R.string.kt_dialog_bluetooth_off_msg),
                actionText = getString(R.string.kt_btn_open_settings),
                secondaryText = getString(R.string.kt_btn_close)
            ) {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            return
        }

        val prefs = SecurePreferencesHelper.getPreferences(this)
        val directPrint = prefs.getBoolean(SettingsActivity.KEY_DIRECT_PRINT, false)
        val defaultAddress = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, "") ?: ""

        val pairedDevices: List<BluetoothDevice> = bluetoothService.getPairedDevices()
        if (pairedDevices.isEmpty()) {
            StatusBottomSheetDialog(this).showError(
                title = getString(R.string.kt_dialog_no_paired_title),
                message = getString(R.string.kt_no_paired_msg),
                actionText = getString(R.string.kt_btn_open_settings),
                secondaryText = getString(R.string.kt_btn_close)
            ) {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            return
        }

        // Direct Print jika diaktifkan atau jika hanya ada 1 printer yang cocok dengan default printer
        if ((directPrint || pairedDevices.size == 1) && defaultAddress.isNotBlank()) {
            val targetDevice = pairedDevices.find { it.address == defaultAddress }
            if (targetDevice != null) {
                executePrint(targetDevice, selectedPdfPages)
                return
            }
        }

        showPrinterSelectionDialog(pairedDevices, selectedPdfPages)
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        return if (permissionsToRequest.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            false
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPrinterSelectionDialog(pairedDevices: List<BluetoothDevice>, selectedPdfPages: List<Int>? = null) {
        val prefs = SecurePreferencesHelper.getPreferences(this)
        val defaultAddress = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, "") ?: ""
        PrinterPickerBottomSheetDialog(
            context = this,
            pairedDevices = pairedDevices,
            currentAddress = defaultAddress,
            includeAllPrintersOption = false
        ) { name, address ->
            val selectedDevice = if (address.isNotBlank()) {
                pairedDevices.firstOrNull { it.address == address } ?: pairedDevices.firstOrNull()
            } else {
                pairedDevices.firstOrNull()
            }
            if (selectedDevice != null) {
                if (defaultAddress.isBlank() && address.isNotBlank()) {
                    prefs.edit()
                        .putString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, address)
                        .putString(SettingsActivity.KEY_DEFAULT_PRINTER_NAME, name)
                        .apply()
                    updateToolbarStatusBadge()
                }
                executePrint(selectedDevice, selectedPdfPages)
            }
        }.show()
    }

    private fun executePrint(device: BluetoothDevice, selectedPdfPages: List<Int>? = null) {
        val prefs = SecurePreferencesHelper.getPreferences(this)
        val printWidth = prefs.getInt(SettingsActivity.KEY_PRINT_WIDTH, 384)
        val autoCut = prefs.getBoolean(SettingsActivity.KEY_AUTO_CUT, true)

        lifecycleScope.launch {
            val statusDialog = StatusBottomSheetDialog(this@MainActivity)
            try {
                if (currentMode == CurrentMode.PDF) {
                    val renderer = pdfRenderer
                    if (renderer == null || renderer.pageCount == 0) {
                        showImportantError(getString(R.string.kt_important_error_pdf_first))
                        return@launch
                    }

                    val pagesToPrint = selectedPdfPages ?: (0 until renderer.pageCount).toList()
                    val totalToPrint = pagesToPrint.size

                    statusDialog.showLoading(
                        title = getString(R.string.kt_sbs_default_loading_title),
                        message = getString(R.string.kt_sbs_default_loading_msg),
                        showProgress = true
                    )

                    val pagesItems = withContext(Dispatchers.IO) {
                        val list = mutableListOf<BluetoothService.PrintPageItem>()
                        var renderFailed = false
                        for (pageIndex in pagesToPrint) {
                            var page: PdfRenderer.Page? = null
                            var bitmap: Bitmap? = null
                            try {
                                val openedPage = renderer.openPage(pageIndex)
                                page = openedPage
                                val renderWidth = openedPage.width * 2
                                val renderHeight = openedPage.height * 2
                                val renderedBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                                bitmap = renderedBitmap
                                val canvas = Canvas(renderedBitmap)
                                canvas.drawColor(Color.WHITE)
                                openedPage.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val pageRaster = bluetoothService.createImageBytes(renderedBitmap, printWidth, autoCut, currentDitherMode)
                                list.add(BluetoothService.PrintPageItem(pageIndex + 1, pageRaster))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                renderFailed = true
                                Log.w("MainActivity", "Unable to render a selected PDF page")
                            } finally {
                                page?.close()
                                bitmap?.recycle()
                            }
                        }
                        if (renderFailed) {
                            list.forEach { it.bytes.fill(0) }
                            emptyList()
                        } else {
                            list
                        }
                    }

                    if (pagesItems.isEmpty()) {
                        statusDialog.dismiss()
                        showImportantError(getString(R.string.kt_important_error_pdf_render_fail))
                        return@launch
                    }

                    val result = bluetoothService.printMultiplePages(
                        device = device,
                        pages = pagesItems,
                        delayBetweenPagesMs = 500
                    ) { currentStep, totalSteps, actualPdfPage ->
                        withContext(Dispatchers.Main) {
                            val percent = (currentStep * 100) / totalSteps
                            statusDialog.updateProgress(
                                getString(R.string.kt_progress_pdf_page, currentStep, totalSteps, actualPdfPage),
                                percent
                            )
                        }
                    }

                    try {
                        val deviceName = bluetoothService.getDeviceName(device) ?: getString(R.string.kt_thermal_printer)
                        val jobTitle = getString(R.string.kt_job_pdf)
                        if (result.isSuccess) {
                            printHistoryManager.addEntry(
                                title = jobTitle,
                                printType = "PDF",
                                targetName = deviceName,
                                targetInfo = device.address,
                                status = PrintHistoryItem.Status.SUCCESS,
                                copies = totalToPrint
                            )
                            statusDialog.showSuccess(
                                title = getString(R.string.kt_sbs_default_success_title),
                                message = getString(R.string.kt_pdf_print_success, totalToPrint)
                            )
                        } else {
                            printHistoryManager.addEntry(
                                title = jobTitle,
                                printType = "PDF",
                                targetName = deviceName,
                                targetInfo = device.address,
                                status = PrintHistoryItem.Status.QUEUED_OFFLINE,
                                copies = totalToPrint
                            )
                            val combinedBytes = ByteArrayOutputStream().apply {
                                for (item in pagesItems) {
                                    write(item.bytes)
                                }
                            }.toByteArray()
                            try {
                                printSpoolerManager.enqueueData(
                                    title = jobTitle,
                                    printType = "PDF",
                                    data = combinedBytes,
                                    copies = 1,
                                    targetDeviceAddress = device.address
                                )
                            } finally {
                                combinedBytes.fill(0)
                            }
                            statusDialog.showError(
                                title = getString(R.string.kt_bluetooth_printer_unreachable_title),
                                message = getString(R.string.kt_print_failed_queued_message, jobTitle),
                                actionText = getString(R.string.kt_spooler_view_queue_action),
                                secondaryText = getString(R.string.kt_btn_close)
                            ) {
                                showSpoolerQueueDialog()
                            }
                        }
                    } finally {
                        pagesItems.forEach { it.bytes.fill(0) }
                    }
                    return@launch
                }

                val printBytes = when (currentMode) {
                    CurrentMode.FORM -> {
                        bluetoothService.createShippingLabelBytes(
                            penerima = etPenerima.text.toString(),
                            pengirim = etPengirim.text.toString(),
                            kurir = actvJasaKurir.text.toString(),
                            kodeBooking = etKodeBooking.text.toString(),
                            isiPaket = etIsiPaket.text.toString(),
                            catatan = etCatatan.text.toString(),
                            autoCut = autoCut,
                            printWidthDots = printWidth,
                            fontName = currentSelectedFont
                        )
                    }
                    CurrentMode.FREE_TEXT -> {
                        val text = etFreeText.text.toString().trim()
                        if (text.isEmpty()) {
                            showImportantError(getString(R.string.kt_important_error_text_first))
                            return@launch
                        }
                        bluetoothService.createFreeTextBytes(text, autoCut)
                    }
                    CurrentMode.IMAGE -> {
                        val bitmap = originalImageBitmap
                        if (bitmap == null) {
                            showImportantError(getString(R.string.kt_important_error_image_first))
                            return@launch
                        }
                        bluetoothService.createImageBytes(bitmap, printWidth, autoCut, currentDitherMode)
                    }
                    CurrentMode.PDF -> ByteArray(0)
                    CurrentMode.PLACEHOLDER -> {
                        showImportantError(getString(R.string.fitur_sedang_dalam_pengembangan))
                        return@launch
                    }
                }

                statusDialog.showLoading(
                    title = getString(R.string.kt_bluetooth_sending_title),
                    message = getString(
                        R.string.kt_loading_connect_device_msg,
                        bluetoothService.getDeviceName(device) ?: getString(R.string.kt_thermal_printer)
                    ),
                    showProgress = false
                )

                try {
                    val result = withContext(Dispatchers.IO) {
                        bluetoothService.printData(device, printBytes)
                    }
                    val deviceName = bluetoothService.getDeviceName(device) ?: getString(R.string.kt_thermal_printer)
                    val jobTitle = when (currentMode) {
                        CurrentMode.FORM -> getString(R.string.kt_job_shipping_label)
                        CurrentMode.FREE_TEXT -> getString(R.string.kt_job_free_text)
                        CurrentMode.IMAGE -> getString(R.string.kt_job_image)
                        CurrentMode.PDF -> getString(R.string.kt_job_pdf)
                        else -> getString(R.string.kt_job_document)
                    }
                    if (result.isSuccess) {
                        printHistoryManager.addEntry(
                            title = jobTitle,
                            printType = currentMode.name,
                            targetName = deviceName,
                            targetInfo = device.address,
                            status = PrintHistoryItem.Status.SUCCESS,
                            copies = 1
                        )
                        statusDialog.showSuccess(
                            title = getString(R.string.kt_success_printed_title),
                            message = getString(R.string.kt_thermal_print_success_msg)
                        )
                    } else {
                        printHistoryManager.addEntry(
                            title = jobTitle,
                            printType = currentMode.name,
                            targetName = deviceName,
                            targetInfo = device.address,
                            status = PrintHistoryItem.Status.QUEUED_OFFLINE,
                            copies = 1
                        )
                        printSpoolerManager.enqueueData(
                            title = jobTitle,
                            printType = currentMode.name,
                            data = printBytes,
                            copies = 1,
                            targetDeviceAddress = device.address
                        )
                        statusDialog.showError(
                            title = getString(R.string.kt_bluetooth_printer_unreachable_title),
                            message = getString(R.string.kt_print_failed_queued_message, jobTitle),
                            actionText = getString(R.string.kt_spooler_view_queue_action),
                            secondaryText = getString(R.string.kt_btn_close)
                        ) {
                            showSpoolerQueueDialog()
                        }
                    }
                } finally {
                    printBytes.fill(0)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                statusDialog.showError(
                    title = getString(R.string.kt_dialog_error_title),
                    message = getString(R.string.kt_print_bluetooth_failed)
                )
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun copyToClipboard(label: String, text: String) {
        if (text.isBlank()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun pasteFromClipboard(editText: EditText) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val textToPaste = clip.getItemAt(0).coerceToText(this).toString()
            if (textToPaste.isNotBlank()) {
                editText.setText(textToPaste)
                editText.setSelection(textToPaste.length)
            }
        }
    }

    private fun showImportantError(message: String) {
        StatusBottomSheetDialog(this).showError(
            title = getString(R.string.kt_notification_title),
            message = message
        )
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == NetraPrintService.ACTION_SYSTEM_PRINT_JOB) {
            val pdfPath = intent.getStringExtra(NetraPrintService.EXTRA_PDF_PATH)
            val copies = intent.getIntExtra(NetraPrintService.EXTRA_PRINT_COPIES, 1)
            val targetPrinterId = intent.getStringExtra(NetraPrintService.EXTRA_TARGET_PRINTER_ID)
            if (!targetPrinterId.isNullOrBlank() && targetPrinterId.startsWith("bt_")) {
                val mac = targetPrinterId.removePrefix("bt_").replace("_", ":")
                val prefs = SecurePreferencesHelper.getPreferences(this)
                prefs.edit().putString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, mac).apply()
                if (::bluetoothService.isInitialized) {
                    updateToolbarStatusBadge()
                }
            }
            if (!pdfPath.isNullOrBlank()) {
                val pdfFile: File
                val privateCache: File
                try {
                    pdfFile = File(pdfPath).canonicalFile
                    privateCache = cacheDir.canonicalFile
                } catch (_: Exception) {
                    Log.w("MainActivity", "Rejected an invalid system print file path")
                    return
                }
                if (pdfFile.parentFile == privateCache &&
                    pdfFile.name.startsWith("sys_print_") &&
                    pdfFile.extension == "pdf" &&
                    pdfFile.isFile
                ) {
                    val pdfUri = Uri.fromFile(pdfFile)
                    switchToMode(CurrentMode.PDF)
                    loadPdfDocument(pdfUri)
                    managedSystemPrintPdf = pdfFile
                    Toast.makeText(
                        this,
                        getString(R.string.kt_toast_system_print_received, copies),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            return
        }

        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            val mimeType = intent.type ?: contentResolver.getType(uri) ?: ""
            val uriString = uri.toString().lowercase()
            val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || uriString.endsWith(".pdf")
            val isImage = mimeType.startsWith("image/") || uriString.endsWith(".png") || uriString.endsWith(".jpg") || uriString.endsWith(".jpeg") || uriString.endsWith(".webp")
            val isText = mimeType.startsWith("text/") || uriString.endsWith(".txt")

            when {
                isPdf -> {
                    switchToMode(CurrentMode.PDF)
                    loadPdfDocument(uri)
                    Toast.makeText(this, getString(R.string.shared_content_ready), Toast.LENGTH_SHORT).show()
                }
                isImage -> {
                    switchToMode(CurrentMode.IMAGE)
                    loadImageDocument(uri)
                    Toast.makeText(this, getString(R.string.shared_content_ready), Toast.LENGTH_SHORT).show()
                }
                isText -> {
                    val text = try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).readText()
                        }
                    } catch (_: Exception) {
                        null
                    }
                    if (!text.isNullOrBlank()) {
                        switchToMode(CurrentMode.FREE_TEXT)
                        etFreeText.setText(text)
                        updateThermalPreview()
                        Toast.makeText(this, getString(R.string.shared_content_ready), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }

        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            val firstUri = uris?.firstOrNull()
            if (firstUri != null) {
                val mimeType = intent.type ?: contentResolver.getType(firstUri) ?: ""
                val uriString = firstUri.toString().lowercase()
                when {
                    mimeType.startsWith("image/") || uriString.endsWith(".png") || uriString.endsWith(".jpg") || uriString.endsWith(".jpeg") -> {
                        switchToMode(CurrentMode.IMAGE)
                        loadImageDocument(firstUri)
                        Toast.makeText(this, getString(R.string.shared_content_ready), Toast.LENGTH_SHORT).show()
                    }
                    mimeType == "application/pdf" || uriString.endsWith(".pdf") -> {
                        switchToMode(CurrentMode.PDF)
                        loadPdfDocument(firstUri)
                        Toast.makeText(this, getString(R.string.shared_content_ready), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }

        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type ?: return
            when {
                type.startsWith("text/") -> {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        switchToMode(CurrentMode.FREE_TEXT)
                        etFreeText.setText(sharedText)
                        updateThermalPreview()
                        Toast.makeText(this, getString(R.string.shared_content_ready), Toast.LENGTH_SHORT).show()
                    }
                }
                type.startsWith("image/") -> {
                    val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    }
                    if (imageUri != null) {
                        switchToMode(CurrentMode.IMAGE)
                        loadImageDocument(imageUri)
                        Toast.makeText(this, getString(R.string.shared_content_ready), Toast.LENGTH_SHORT).show()
                    }
                }
                type == "application/pdf" -> {
                    val pdfUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    }
                    if (pdfUri != null) {
                        switchToMode(CurrentMode.PDF)
                        loadPdfDocument(pdfUri)
                        Toast.makeText(this, getString(R.string.shared_content_ready), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }
    }

    private fun updateToolbarStatusBadge() {
        val prefs = SecurePreferencesHelper.getPreferences(this)
        val defaultName = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_NAME, "") ?: ""
        val printWidth = prefs.getInt(SettingsActivity.KEY_PRINT_WIDTH, 384)
        val widthStr = if (printWidth == 576) "80mm" else "58mm"

        if (defaultName.isNotBlank()) {
            tvToolbarPrinterStatus?.text = "$defaultName • $widthStr"
            dotPrinterStatus?.backgroundTintList = ColorStateList.valueOf(getColor(R.color.printer_connected))
        } else {
            val paired = bluetoothService.getPairedDevices()
            if (paired.isNotEmpty()) {
                val first = paired.first()
                val name = bluetoothService.getDeviceName(first) ?: "Printer"
                tvToolbarPrinterStatus?.text = "$name • $widthStr"
                dotPrinterStatus?.backgroundTintList = ColorStateList.valueOf(getColor(R.color.printer_connected))
            } else {
                tvToolbarPrinterStatus?.setText(R.string.kt_choose_printer)
                dotPrinterStatus?.backgroundTintList = ColorStateList.valueOf(getColor(R.color.printer_disconnected))
            }
        }
    }

    private fun togglePaperWidth() {
        val prefs = SecurePreferencesHelper.getPreferences(this)
        val currentWidth = prefs.getInt(SettingsActivity.KEY_PRINT_WIDTH, 384)
        val newWidth = if (currentWidth == 576) 384 else 576
        prefs.edit().putInt(SettingsActivity.KEY_PRINT_WIDTH, newWidth).apply()
        updateToolbarStatusBadge()
        updateThermalPreview()
    }

    private fun setupInAppUpdateChecker() {
        val layoutUpdateBanner = findViewById<View>(R.id.layoutUpdateBanner) ?: return
        val tvUpdateBannerText = findViewById<TextView>(R.id.tvUpdateBannerText)
        val btnUpdateBannerAction = findViewById<View>(R.id.btnUpdateBannerAction)
        val btnUpdateBannerClose = findViewById<View>(R.id.btnUpdateBannerClose)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = SecurePreferencesHelper.getPreferences(this@MainActivity)
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).versionCode
                }

                val lastCheck = prefs.getLong("key_last_auto_update_check", 0L)
                val now = System.currentTimeMillis()
                if (now - lastCheck < 24 * 60 * 60 * 1000L && lastCheck > 0L) {
                    return@launch
                }

                val conn = (URL("https://netraprint-updater.yt-hackrandom.workers.dev/version.json").openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "NetraPrint-Android")
                    setRequestProperty("Accept", "application/json")
                    useCaches = false
                }
                val jsonText = try {
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        null
                    }
                } finally {
                    conn.disconnect()
                } ?: return@launch

                prefs.edit().putLong("key_last_auto_update_check", now).apply()

                val json = JSONObject(jsonText)
                val latestVersionCode = json.getInt("latestVersionCode")
                val latestVersionName = json.getString("latestVersionName")
                val downloadUrl = json.optString("downloadUrl", "https://netraprint-updater.yt-hackrandom.workers.dev/download")
                val releasePageUrl = json.optString("releasePageUrl", "https://github.com/b0nz0el/NetraPrint/releases/latest")
                val rawApkUrl = json.optString("apkUrl", "")
                val targetUrl = releasePageUrl.ifBlank { if (rawApkUrl.isNotBlank()) rawApkUrl else downloadUrl }

                val dismissedVersion = prefs.getInt("key_dismissed_update_version", 0)
                if (latestVersionCode > currentVersionCode && latestVersionCode != dismissedVersion) {
                    withContext(Dispatchers.Main) {
                        if (isFinishing || isDestroyed) return@withContext
                        tvUpdateBannerText?.text = getString(R.string.in_app_update_banner_title, latestVersionName)
                        layoutUpdateBanner.visibility = View.VISIBLE

                        btnUpdateBannerAction?.setOnClickListener {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                startActivity(intent)
                            } catch (_: Exception) {
                                val settingsIntent = Intent(this@MainActivity, SettingsActivity::class.java)
                                startActivity(settingsIntent)
                            }
                        }

                        btnUpdateBannerClose?.setOnClickListener {
                            layoutUpdateBanner.visibility = View.GONE
                            prefs.edit().putInt("key_dismissed_update_version", latestVersionCode).apply()
                        }
                    }
                }
            } catch (_: Exception) {
                // Silently ignore background check errors to preserve offline experience
            }
        }
    }

    private fun showDitherModeDialog() {
        val modes = DitheringEngine.DitherMode.values()
        val items = modes.map { "${getDitherModeName(it)}\n${getDitherModeDescription(it)}" }.toTypedArray()
        val currentIndex = modes.indexOf(currentDitherMode)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.kt_dither_mode_title)
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                currentDitherMode = modes[which]
                chipDitherMode?.text = getDitherModeName(currentDitherMode)
                updateThermalPreview()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.kt_btn_close, null)
            .show()
    }

    private fun updateSpoolerBadge(count: Int) {
        if (count > 0) {
            layoutSpoolerBadge?.visibility = View.VISIBLE
            tvSpoolerQueueBadge?.text = getString(R.string.kt_spooler_pending_count, count)
        } else {
            layoutSpoolerBadge?.visibility = View.GONE
        }
    }

    private fun getDitherModeName(mode: DitheringEngine.DitherMode): String {
        return getString(
            when (mode) {
                DitheringEngine.DitherMode.FLOYD_STEINBERG -> R.string.kt_dither_floyd_steinberg
                DitheringEngine.DitherMode.ATKINSON -> R.string.kt_dither_atkinson
                DitheringEngine.DitherMode.THRESHOLD -> R.string.kt_dither_threshold
            }
        )
    }

    private fun getDitherModeDescription(mode: DitheringEngine.DitherMode): String {
        return getString(
            when (mode) {
                DitheringEngine.DitherMode.FLOYD_STEINBERG -> R.string.kt_dither_floyd_description
                DitheringEngine.DitherMode.ATKINSON -> R.string.kt_dither_atkinson_description
                DitheringEngine.DitherMode.THRESHOLD -> R.string.kt_dither_threshold_description
            }
        )
    }

    private fun flushOfflineSpoolerQueue() {
        val count = printSpoolerManager.getQueueCount()
        if (count == 0) return

        val statusDialog = StatusBottomSheetDialog(this)
        statusDialog.showLoading(
            title = getString(R.string.kt_spooler_queue_title, count),
            message = getString(R.string.kt_spooler_preparing_count, count),
            showProgress = true
        )

        lifecycleScope.launch {
            try {
                val result = printSpoolerManager.flushQueue(bluetoothService) { current, total, job ->
                    val percent = if (total > 0) (current * 100) / total else 0
                    statusDialog.updateProgress(
                        getString(R.string.kt_progress_spooler_item, current, total, job.title),
                        percent
                    )
                }

                if (result.isSuccess) {
                    val printed = result.getOrDefault(count)
                    statusDialog.showSuccess(
                        title = getString(R.string.kt_spooler_success_title),
                        message = getString(R.string.kt_spooler_success_msg, printed)
                    )
                } else {
                    statusDialog.showError(
                        title = getString(R.string.kt_dialog_spooler_fail_title),
                        message = getString(R.string.kt_spooler_connect_fail_msg)
                    )
                }
            } catch (_: Exception) {
                android.util.Log.e("MainActivity", "Unable to process queued print jobs")
                statusDialog.showError(
                    title = getString(R.string.kt_dialog_error_title),
                    message = getString(R.string.kt_spooler_process_error_msg)
                )
            }
        }
    }

    private fun showSpoolerQueueDialog() {
        val jobs = printSpoolerManager.getAllJobs()
        if (jobs.isEmpty()) {
            StatusBottomSheetDialog(this).showSuccess(
                title = getString(R.string.kt_spooler_empty_title),
                message = getString(R.string.kt_spooler_empty_msg)
            )
            return
        }

        val jobTitles = jobs.mapIndexed { idx, j -> "${idx + 1}. ${j.title} (${j.printType})" }.joinToString("\n")
        StatusBottomSheetDialog(this).showError(
            title = getString(R.string.kt_spooler_queue_title, jobs.size),
            message = getString(R.string.kt_spooler_queue_msg, jobTitles),
            actionText = getString(R.string.kt_spooler_print_now_action)
        ) {
            flushOfflineSpoolerQueue()
        }
    }

    private fun updateThermalPreview() {
        val previewCard = cardThermalPreview ?: findViewById<View>(R.id.layoutThermalPreviewCard) ?: findViewById<View>(R.id.cardThermalPreview)
        if (previewCard == null && tvThermalPreviewText == null) return
        if (cardThermalPreview == null) cardThermalPreview = previewCard
        if (tvThermalPreviewText == null) tvThermalPreviewText = findViewById(R.id.tvThermalPreviewText)
        if (chipPaperWidth == null) chipPaperWidth = findViewById(R.id.chipPaperWidth)
        if (chipDitherMode == null) chipDitherMode = findViewById(R.id.chipDitherMode)
        if (tvThermalPreviewInfo == null) tvThermalPreviewInfo = findViewById(R.id.tvThermalPreviewInfo)
        if (ivThermalPreviewImage == null) ivThermalPreviewImage = findViewById(R.id.ivThermalPreviewImage)
        val prefs = SecurePreferencesHelper.getPreferences(this)
        val printWidth = prefs.getInt(SettingsActivity.KEY_PRINT_WIDTH, 384)
        val cpl = if (printWidth == 576) 48 else 32

        chipPaperWidth?.text = if (printWidth == 576) getString(R.string.kt_chip_width_80mm) else getString(R.string.kt_chip_width_58mm)
        chipDitherMode?.text = getDitherModeName(currentDitherMode)
        tvThermalPreviewInfo?.text = getString(R.string.kt_thermal_preview_info, printWidth)

        when (currentMode) {
            CurrentMode.FORM -> {
                ivThermalPreviewImage?.visibility = View.GONE
                tvThermalPreviewText?.visibility = View.VISIBLE

                val divider = "=".repeat(cpl)
                val thinDivider = "-".repeat(cpl)
                val sb = StringBuilder()
                sb.append(divider).append("\n")
                sb.append(getString(R.string.kt_receipt_shipping_label)).append("\n")
                sb.append(divider).append("\n")

                val kurir = actvJasaKurir.text.toString().trim()
                if (kurir.isNotEmpty()) {
                    sb.append(getString(R.string.kt_preview_courier, kurir)).append("\n")
                }
                val resi = etKodeBooking.text.toString().trim()
                if (resi.isNotEmpty()) {
                    sb.append(getString(R.string.kt_preview_tracking_number, resi)).append("\n")
                }
                sb.append(thinDivider).append("\n")

                val penerima = etPenerima.text.toString().trim()
                sb.append(
                    getString(
                        R.string.kt_preview_recipient,
                        if (penerima.isEmpty()) getString(R.string.kt_preview_empty_recipient) else penerima
                    )
                ).append("\n")

                val pengirim = etPengirim.text.toString().trim()
                if (pengirim.isNotEmpty()) {
                    sb.append(thinDivider).append("\n")
                    sb.append(getString(R.string.kt_preview_sender, pengirim)).append("\n")
                }

                val isi = etIsiPaket.text.toString().trim()
                if (isi.isNotEmpty()) {
                    sb.append(getString(R.string.kt_preview_package_contents, isi)).append("\n")
                }

                val catatan = etCatatan.text.toString().trim()
                if (catatan.isNotEmpty()) {
                    sb.append(getString(R.string.kt_preview_notes, catatan)).append("\n")
                }
                sb.append(divider).append("\n")
                sb.append(getString(R.string.kt_preview_thank_you)).append("\n")
                sb.append(divider)

                tvThermalPreviewText?.text = sb.toString()
            }

            CurrentMode.FREE_TEXT -> {
                ivThermalPreviewImage?.visibility = View.GONE
                tvThermalPreviewText?.visibility = View.VISIBLE

                val text = etFreeText.text.toString().trim()
                val divider = "-".repeat(cpl)
                val sb = StringBuilder()
                sb.append(divider).append("\n")
                sb.append(
                    if (text.isEmpty()) getString(R.string.kt_preview_empty_free_text) else text
                ).append("\n")
                sb.append(divider)
                tvThermalPreviewText?.text = sb.toString()
            }

            CurrentMode.IMAGE -> {
                val bitmap = originalImageBitmap
                if (bitmap != null) {
                    tvThermalPreviewText?.visibility = View.GONE
                    ivThermalPreviewImage?.visibility = View.VISIBLE
                    lifecycleScope.launch(Dispatchers.Default) {
                        val dithered = DitheringEngine.ditherToBitmap(bitmap, printWidth, currentDitherMode)
                        withContext(Dispatchers.Main) {
                            ivThermalPreviewImage?.setImageBitmap(dithered)
                        }
                    }
                } else {
                    ivThermalPreviewImage?.visibility = View.GONE
                    tvThermalPreviewText?.visibility = View.VISIBLE
                    tvThermalPreviewText?.setText(R.string.kt_preview_choose_image)
                }
            }

            CurrentMode.PDF -> {
                val bitmap = currentPdfPageBitmap
                if (bitmap != null) {
                    tvThermalPreviewText?.visibility = View.GONE
                    ivThermalPreviewImage?.visibility = View.VISIBLE
                    lifecycleScope.launch(Dispatchers.Default) {
                        val dithered = DitheringEngine.ditherToBitmap(bitmap, printWidth, currentDitherMode)
                        withContext(Dispatchers.Main) {
                            ivThermalPreviewImage?.setImageBitmap(dithered)
                        }
                    }
                } else {
                    ivThermalPreviewImage?.visibility = View.GONE
                    tvThermalPreviewText?.visibility = View.VISIBLE
                    tvThermalPreviewText?.setText(R.string.kt_preview_choose_pdf)
                }
            }

            CurrentMode.PLACEHOLDER -> {
                tvThermalPreviewText?.visibility = View.VISIBLE
                ivThermalPreviewImage?.visibility = View.GONE
                tvThermalPreviewText?.setText(R.string.fitur_sedang_dalam_pengembangan)
            }
        }
    }
}
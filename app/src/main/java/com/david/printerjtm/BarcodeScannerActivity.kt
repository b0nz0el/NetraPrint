package com.david.printerjtm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.HapticFeedbackConstants
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BarcodeScannerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCANNED_CODE = "SCANNED_BARCODE"
    }

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var isTorchOn: Boolean = false
    private var isScanned: Boolean = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            StatusBottomSheetDialog(this).showError(
                title = getString(R.string.kt_dialog_camera_perm_title),
                message = getString(R.string.kt_dialog_camera_perm_msg)
            ) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner)

        previewView = findViewById(R.id.previewView)
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<ImageButton>(R.id.btnBackScanner).setOnClickListener {
            finish()
        }

        val btnFlashlight = findViewById<ImageButton>(R.id.btnFlashlight)
        btnFlashlight.setOnClickListener {
            val cam = camera ?: return@setOnClickListener
            if (cam.cameraInfo.hasFlashUnit()) {
                isTorchOn = !isTorchOn
                cam.cameraControl.enableTorch(isTorchOn)
                btnFlashlight.setColorFilter(
                    if (isTorchOn) ContextCompat.getColor(this, android.R.color.holo_orange_light)
                    else ContextCompat.getColor(this, android.R.color.white)
                )
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1080p target resolution to capture dense Code 128 barcodes clearly
            val targetSize = Size(1920, 1080)

            val preview = Preview.Builder()
                .setTargetResolution(targetSize)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val barcodeOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val barcodeScanner = BarcodeScanning.getClient(barcodeOptions)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(targetSize)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(barcodeScanner) { rawCode ->
                        if (!isScanned) {
                            isScanned = true
                            runOnUiThread {
                                previewView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val intent = Intent().apply {
                                    putExtra(EXTRA_SCANNED_CODE, rawCode)
                                }
                                setResult(Activity.RESULT_OK, intent)
                                finish()
                            }
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                StatusBottomSheetDialog(this).showError(
                    title = getString(R.string.kt_dialog_camera_unavailable_title),
                    message = getString(R.string.kt_dialog_camera_error_msg)
                ) {
                    finish()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private class BarcodeAnalyzer(
        private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        private val onBarcodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (!barcodes.isNullOrEmpty()) {
                            for (barcode in barcodes) {
                                val raw = barcode.rawValue ?: barcode.displayValue
                                if (!raw.isNullOrBlank()) {
                                    onBarcodeDetected(raw.trim())
                                    break
                                }
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}

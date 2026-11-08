package com.netraprint.app

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * High-performance, memory-efficient 1-bit Monochrome Dithering Engine for ESC/POS Thermal Printers.
 * Supports:
 * 1. Floyd-Steinberg: Industry standard error-diffusion algorithm (7/16, 3/16, 5/16, 1/16).
 * 2. Atkinson: Classic high-contrast algorithm with 1/8 error diffusion across 6 neighbors.
 * 3. Sharp Threshold: Direct luminance cutoff (140) for crisp barcodes, QR, and sharp text.
 */
object DitheringEngine {

    enum class DitherMode(val displayName: String, val description: String) {
        FLOYD_STEINBERG("Floyd-Steinberg", "Gradasi halus & seimbang untuk foto & dokumen"),
        ATKINSON("Atkinson (Kontras Tinggi)", "Sangat tajam & berkarakter khas struk thermal"),
        THRESHOLD("Hitam-Putih Tegas", "Ambang batas langsung untuk teks tebal & barcode")
    }

    /**
     * Converts a source Bitmap into a 1-bit monochrome Bitmap (Black #000000 and White #FFFFFF)
     * using the specified dithering algorithm. Ideal for real-time WYSIWYG Thermal Paper Roll Preview.
     */
    fun ditherToBitmap(
        sourceBitmap: Bitmap,
        targetWidth: Int = 384,
        mode: DitherMode = DitherMode.FLOYD_STEINBERG
    ): Bitmap {
        val width = ((targetWidth.coerceAtLeast(128)) / 8) * 8
        val height = ((sourceBitmap.height * (width.toFloat() / sourceBitmap.width)).toInt()).coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(sourceBitmap, width, height, true)
        val binaryMatrix = computeDitherMatrix(scaled, width, height, mode)

        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                outPixels[rowOffset + x] = if (binaryMatrix[y][x]) Color.BLACK else Color.WHITE
            }
        }

        outputBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outputBitmap
    }

    /**
     * Converts a source Bitmap directly into ESC/POS GS v 0 1-bit raster image bytes
     * using the specified dithering mode.
     */
    fun ditherToEscPosRaster(
        sourceBitmap: Bitmap,
        targetWidth: Int = 384,
        maxHeight: Int? = null,
        autoCut: Boolean = true,
        mode: DitherMode = DitherMode.FLOYD_STEINBERG,
        includeInitAndFeed: Boolean = true
    ): ByteArray {
        var width = ((targetWidth.coerceAtLeast(128)) / 8) * 8
        var height = ((sourceBitmap.height * (width.toFloat() / sourceBitmap.width)).toInt()).coerceAtLeast(1)

        // Clamping height for compact receipt logos to avoid massive buffer bloat
        if (maxHeight != null && height > maxHeight) {
            val scale = maxHeight.toFloat() / height
            height = maxHeight
            width = (((width * scale).toInt() / 8) * 8).coerceAtLeast(64)
        }

        val scaled = Bitmap.createScaledBitmap(sourceBitmap, width, height, true)
        val binaryMatrix = computeDitherMatrix(scaled, width, height, mode)

        val widthBytes = width / 8
        val xL = (widthBytes % 256).toByte()
        val xH = (widthBytes / 256).toByte()
        val yL = (height % 256).toByte()
        val yH = (height / 256).toByte()

        val buffer = ByteArrayOutputStream()
        if (includeInitAndFeed) {
            // ESC @: Initialize printer
            buffer.write(byteArrayOf(0x1B, 0x40))
        }
        // ESC a 1: Align Center
        buffer.write(byteArrayOf(0x1B, 0x61, 0x01))

        // GS v 0 0 xL xH yL yH: Print raster bit image
        buffer.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

        for (y in 0 until height) {
            val row = binaryMatrix[y]
            for (byteIdx in 0 until widthBytes) {
                var byteVal = 0
                val startX = byteIdx * 8
                for (bit in 0 until 8) {
                    val x = startX + bit
                    if (x < width && row[x]) {
                        // In ESC/POS GS v 0: 1 = black dot, 0 = white dot
                        byteVal = byteVal or (1 shl (7 - bit))
                    }
                }
                buffer.write(byteVal)
            }
        }

        if (includeInitAndFeed) {
            // Feed 3 lines to clear the tear-off bar
            buffer.write(byteArrayOf(0x1B, 0x64, 0x03))

            // Partial cut if requested
            if (autoCut) {
                buffer.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
            }
        }

        return buffer.toByteArray()
    }

    /**
     * Computes the 2D boolean matrix where true = Black dot (printed), false = White dot.
     */
    fun computeDitherMatrix(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        mode: DitherMode
    ): Array<BooleanArray> {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Extract grayscale float matrix (0.0 = black, 255.0 = white)
        val gray = Array(height) { FloatArray(width) }
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val color = pixels[rowOffset + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                gray[y][x] = (0.299f * r + 0.587f * g + 0.114f * b)
            }
        }

        val result = Array(height) { BooleanArray(width) }

        when (mode) {
            DitherMode.THRESHOLD -> {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        result[y][x] = gray[y][x] < 140f
                    }
                }
            }

            DitherMode.FLOYD_STEINBERG -> {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val oldVal = gray[y][x].coerceIn(0f, 255f)
                        val newVal = if (oldVal < 128f) 0f else 255f
                        result[y][x] = newVal == 0f
                        val err = oldVal - newVal

                        // Distribute error
                        // [x + 1, y] += err * 7/16
                        if (x + 1 < width) {
                            gray[y][x + 1] += err * (7f / 16f)
                        }
                        // [x - 1, y + 1] += err * 3/16
                        if (x - 1 >= 0 && y + 1 < height) {
                            gray[y + 1][x - 1] += err * (3f / 16f)
                        }
                        // [x, y + 1] += err * 5/16
                        if (y + 1 < height) {
                            gray[y + 1][x] += err * (5f / 16f)
                        }
                        // [x + 1, y + 1] += err * 1/16
                        if (x + 1 < width && y + 1 < height) {
                            gray[y + 1][x + 1] += err * (1f / 16f)
                        }
                    }
                }
            }

            DitherMode.ATKINSON -> {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val oldVal = gray[y][x].coerceIn(0f, 255f)
                        val newVal = if (oldVal < 128f) 0f else 255f
                        result[y][x] = newVal == 0f
                        val err = oldVal - newVal
                        val errFraction = err / 8f

                        // Atkinson spreads 1/8 of error to 6 neighbors (6/8 total retained):
                        // [x + 1, y]
                        if (x + 1 < width) gray[y][x + 1] += errFraction
                        // [x + 2, y]
                        if (x + 2 < width) gray[y][x + 2] += errFraction
                        // [x - 1, y + 1]
                        if (x - 1 >= 0 && y + 1 < height) gray[y + 1][x - 1] += errFraction
                        // [x, y + 1]
                        if (y + 1 < height) gray[y + 1][x] += errFraction
                        // [x + 1, y + 1]
                        if (x + 1 < width && y + 1 < height) gray[y + 1][x + 1] += errFraction
                        // [x, y + 2]
                        if (y + 2 < height) gray[y + 2][x] += errFraction
                    }
                }
            }
        }

        return result
    }
}

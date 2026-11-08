package com.netraprint.app

import java.util.UUID

/**
 * Metadata representasi riwayat pencetakan di NetraPrint.
 * Tidak menyimpan isi/payload dokumen sensitif untuk efisiensi penyimpanan & privasi.
 */
data class PrintHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val printType: String, // "FORM", "FREE_TEXT", "IMAGE", "PDF", "QR", "WEB", "SYSTEM_PRINT"
    val timestamp: Long = System.currentTimeMillis(),
    val targetName: String,
    val targetInfo: String? = null,
    val status: Status = Status.SUCCESS,
    val copies: Int = 1
) {
    enum class Status {
        SUCCESS,
        QUEUED_OFFLINE,
        FAILED
    }
}

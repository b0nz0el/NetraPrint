package com.netraprint.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manajer penyimpanan riwayat cetak persisten untuk NetraPrint.
 * Menyimpan hingga [maxItems] entri terbaru secara atomic dan thread-safe.
 */
class PrintHistoryManager(
    private val storageDir: File,
    private val maxItems: Int = 100
) {
    companion object {
        private const val FILENAME = "print_history.json"

        @Volatile
        private var INSTANCE: PrintHistoryManager? = null

        fun getInstance(context: Context): PrintHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrintHistoryManager(context.applicationContext.filesDir).also { INSTANCE = it }
            }
        }
    }

    private val historyItems = CopyOnWriteArrayList<PrintHistoryItem>()
    private val file = File(storageDir, FILENAME)

    init {
        loadFromDisk()
    }

    fun addEntry(
        title: String,
        printType: String,
        targetName: String,
        targetInfo: String? = null,
        status: PrintHistoryItem.Status = PrintHistoryItem.Status.SUCCESS,
        copies: Int = 1
    ): PrintHistoryItem {
        val item = PrintHistoryItem(
            title = title,
            printType = printType,
            targetName = targetName,
            targetInfo = targetInfo,
            status = status,
            copies = copies
        )
        historyItems.add(0, item)
        while (historyItems.size > maxItems) {
            historyItems.removeAt(historyItems.size - 1)
        }
        saveToDisk()
        return item
    }

    fun getAll(): List<PrintHistoryItem> {
        return historyItems.toList()
    }

    fun getCount(): Int {
        return historyItems.size
    }

    fun clearAll() {
        historyItems.clear()
        saveToDisk()
    }

    fun deleteItem(id: String): Boolean {
        val removed = historyItems.removeIf { it.id == id }
        if (removed) {
            saveToDisk()
        }
        return removed
    }

    private fun loadFromDisk() {
        if (!file.isFile) return
        try {
            val jsonText = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonText)
            historyItems.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val statusStr = obj.optString("status", PrintHistoryItem.Status.SUCCESS.name)
                val status = try {
                    PrintHistoryItem.Status.valueOf(statusStr)
                } catch (_: Exception) {
                    PrintHistoryItem.Status.SUCCESS
                }
                historyItems.add(
                    PrintHistoryItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        printType = obj.optString("printType"),
                        timestamp = obj.optLong("timestamp"),
                        targetName = obj.optString("targetName"),
                        targetInfo = obj.optString("targetInfo").takeIf { it.isNotBlank() },
                        status = status,
                        copies = obj.optInt("copies", 1)
                    )
                )
            }
        } catch (_: Exception) {
            // Abaikan parsing error jika berkas rusak
        }
    }

    private fun saveToDisk() {
        try {
            if (!storageDir.isDirectory) {
                storageDir.mkdirs()
            }
            val jsonArray = JSONArray()
            for (item in historyItems) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("printType", item.printType)
                    put("timestamp", item.timestamp)
                    put("targetName", item.targetName)
                    put("targetInfo", item.targetInfo ?: "")
                    put("status", item.status.name)
                    put("copies", item.copies)
                }
                jsonArray.put(obj)
            }
            val tempFile = File(storageDir, "$FILENAME.tmp")
            tempFile.writeText(jsonArray.toString(), Charsets.UTF_8)
            try {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            // Keep safe
        }
    }
}

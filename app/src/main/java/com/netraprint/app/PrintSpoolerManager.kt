package com.netraprint.app

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Resilient, thread-safe Offline Print Spooler Queue for NetraPrint.
 * Automatically stores print jobs when the printer is offline or out of range,
 * and allows 1-tap retry or automatic background flushing when reconnected.
 */
class PrintSpoolerManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PrintSpoolerManager"
        private const val LEGACY_QUEUE_FILENAME = "print_spooler_queue.json"
        private const val JOB_TTL_MILLIS = SpoolerFileStore.TTL_MILLIS

        @Volatile
        private var INSTANCE: PrintSpoolerManager? = null

        fun getInstance(context: Context): PrintSpoolerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrintSpoolerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    data class SpoolJob(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val printType: String, // "LABEL", "TEXT", "IMAGE", "PDF"
        val payloadFileName: String,
        val timestamp: Long = System.currentTimeMillis(),
        val copies: Int = 1,
        val targetDeviceAddress: String? = null,
        val targetWifiIp: String? = null,
        val targetWifiPort: Int = 9100
    )

    private val queueLock = Any()
    private val memoryQueue = mutableListOf<SpoolJob>()
    private val queueListeners = CopyOnWriteArrayList<(count: Int) -> Unit>()
    private val fileStore = SpoolerFileStore(context.cacheDir)

    init {
        loadQueueFromDisk()
    }

    fun addListener(listener: (count: Int) -> Unit) {
        queueListeners.add(listener)
        listener(getQueueCount())
    }

    fun removeListener(listener: (count: Int) -> Unit) {
        queueListeners.remove(listener)
    }

    private fun notifyListeners() {
        val count = getQueueCount()
        for (listener in queueListeners) {
            try {
                listener(count)
            } catch (_: RuntimeException) {
                Log.e(TAG, "A spooler queue listener failed")
            }
        }
    }

    fun getQueueCount(): Int {
        synchronized(queueLock) {
            return memoryQueue.size
        }
    }

    fun getAllJobs(): List<SpoolJob> {
        synchronized(queueLock) {
            return ArrayList(memoryQueue)
        }
    }

    fun enqueue(job: SpoolJob) {
        synchronized(queueLock) {
            memoryQueue.add(job)
            try {
                saveQueueToDisk()
            } catch (error: Exception) {
                memoryQueue.remove(job)
                try {
                    fileStore.deletePayload(job.payloadFileName)
                } catch (cleanupError: Exception) {
                    error.addSuppressed(cleanupError)
                }
                throw error
            }
        }
        notifyListeners()
        Log.d(TAG, "Print job queued. Pending count: ${getQueueCount()}")
    }

    fun enqueueData(
        title: String,
        printType: String,
        data: ByteArray,
        copies: Int = 1,
        targetDeviceAddress: String? = null,
        targetWifiIp: String? = null,
        targetWifiPort: Int = 9100
    ): SpoolJob {
        val id = UUID.randomUUID().toString()
        val payloadFileName = fileStore.writePayload(id, data)
        val job = SpoolJob(
            id = id,
            title = title,
            printType = printType,
            payloadFileName = payloadFileName,
            copies = copies.coerceAtLeast(1),
            targetDeviceAddress = targetDeviceAddress,
            targetWifiIp = targetWifiIp,
            targetWifiPort = targetWifiPort
        )
        enqueue(job)
        return job
    }

    fun peek(): SpoolJob? {
        synchronized(queueLock) {
            return memoryQueue.firstOrNull()
        }
    }

    fun dequeue(): SpoolJob? {
        val job = synchronized(queueLock) {
            if (memoryQueue.isNotEmpty()) {
                val removed = memoryQueue.removeAt(0)
                fileStore.deletePayload(removed.payloadFileName)
                saveQueueToDisk()
                removed
            } else {
                null
            }
        }
        if (job != null) {
            notifyListeners()
        }
        return job
    }

    fun removeJob(id: String): Boolean {
        val removed = synchronized(queueLock) {
            val index = memoryQueue.indexOfFirst { it.id == id }
            if (index != -1) {
                val removed = memoryQueue.removeAt(index)
                fileStore.deletePayload(removed.payloadFileName)
                saveQueueToDisk()
                true
            } else {
                false
            }
        }
        if (removed) {
            notifyListeners()
        }
        return removed
    }

    fun clearQueue() {
        synchronized(queueLock) {
            memoryQueue.forEach { fileStore.deletePayload(it.payloadFileName) }
            memoryQueue.clear()
            fileStore.deleteQueue()
        }
        notifyListeners()
        Log.d(TAG, "Print queue cleared")
    }

    /**
     * Sequentially flushes all queued jobs.
     * Stops on first connection error, preserving remaining jobs in queue.
     */
    suspend fun flushQueue(
        bluetoothService: BluetoothService,
        onProgress: (current: Int, total: Int, job: SpoolJob) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        val jobs = getAllJobs()
        if (jobs.isEmpty()) {
            return@withContext Result.success(0)
        }

        var successCount = 0
        val total = jobs.size

        for ((index, job) in jobs.withIndex()) {
            try {
                withContext(Dispatchers.Main) {
                    onProgress(index + 1, total, job)
                }
            } catch (cancelled: CancellationException) {
                removeJob(job.id)
                throw cancelled
            }

            val bytes = try {
                fileStore.readPayload(job.payloadFileName)
            } catch (_: IOException) {
                null
            }
            if (bytes == null || bytes.isEmpty()) {
                Log.w(TAG, "Discarded a queued print job with missing or empty payload")
                removeJob(job.id)
                continue
            }

            try {
                for (copy in 1..job.copies) {
                    val result: Result<Unit> = if (!job.targetWifiIp.isNullOrBlank()) {
                        bluetoothService.printDataWifi(
                            ipAddress = job.targetWifiIp,
                            port = job.targetWifiPort,
                            data = bytes
                        )
                    } else {
                        val paired = bluetoothService.getPairedDevices()
                        val targetDevice = if (!job.targetDeviceAddress.isNullOrBlank()) {
                            paired.find { it.address == job.targetDeviceAddress }
                        } else {
                            val prefs = SecurePreferencesHelper.getPreferences(context)
                            val defaultAddr = prefs.getString(SettingsActivity.KEY_DEFAULT_PRINTER_ADDRESS, "") ?: ""
                            if (defaultAddr.isNotBlank()) {
                                paired.find { it.address == defaultAddr } ?: paired.firstOrNull()
                            } else {
                                paired.firstOrNull()
                            }
                        }

                        if (targetDevice == null) {
                            Result.failure(IllegalStateException("No paired printer is available"))
                        } else {
                            bluetoothService.printData(targetDevice, bytes)
                        }
                    }

                    if (result.isFailure) {
                        Log.e(TAG, "A queued print failed; the job was retained for retry")
                        return@withContext Result.failure(
                            Exception(AppLocaleContext.wrap(context).getString(R.string.kt_spooler_failed_retry))
                        )
                    }
                }

                removeJob(job.id)
                successCount++
                try {
                    PrintHistoryManager.getInstance(context).addEntry(
                        title = job.title,
                        printType = job.printType,
                        targetName = job.targetWifiIp ?: (job.targetDeviceAddress ?: "Printer"),
                        targetInfo = if (!job.targetWifiIp.isNullOrBlank()) job.targetWifiPort.toString() else job.targetDeviceAddress,
                        status = PrintHistoryItem.Status.SUCCESS,
                        copies = job.copies
                    )
                } catch (_: Exception) {
                    // Ignored
                }
            } catch (cancelled: CancellationException) {
                removeJob(job.id)
                throw cancelled
            } finally {
                bytes.fill(0)
            }
        }

        Result.success(successCount)
    }

    private fun loadQueueFromDisk() {
        synchronized(queueLock) {
            memoryQueue.clear()
            try {
                migrateLegacyQueue()
                val jsonString = fileStore.readQueue()
                if (jsonString != null) {
                    val jsonArray = JSONArray(jsonString)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        val payloadFileName = obj.optString("payloadFileName", "")
                        val job = SpoolJob(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title").ifBlank {
                                AppLocaleContext.wrap(context).getString(R.string.kt_job_document)
                            },
                            printType = obj.optString("printType", "DOC"),
                            payloadFileName = payloadFileName,
                            timestamp = timestamp,
                            copies = obj.optInt("copies", 1),
                            targetDeviceAddress = if (obj.has("targetDeviceAddress") && !obj.isNull("targetDeviceAddress")) obj.getString("targetDeviceAddress") else null,
                            targetWifiIp = if (obj.has("targetWifiIp") && !obj.isNull("targetWifiIp")) obj.getString("targetWifiIp") else null,
                            targetWifiPort = obj.optInt("targetWifiPort", 9100)
                        )
                        if (timestamp >= System.currentTimeMillis() - JOB_TTL_MILLIS &&
                            fileStore.payloadExists(payloadFileName)
                        ) {
                            memoryQueue.add(job)
                        } else {
                            fileStore.deletePayload(payloadFileName)
                        }
                    }
                }
                fileStore.sweep(memoryQueue.mapTo(mutableSetOf()) { it.payloadFileName })
                saveQueueToDisk()
                Log.d(TAG, "Loaded ${memoryQueue.size} pending print jobs")
            } catch (_: Exception) {
                memoryQueue.clear()
                try {
                    fileStore.deleteAllPayloads()
                    fileStore.deleteQueue()
                } catch (_: Exception) {
                    Log.e(TAG, "Unable to fully clear corrupted spooler data")
                }
                Log.e(TAG, "Unable to load cached print queue; cached jobs were discarded")
            }
        }
    }

    private fun saveQueueToDisk() {
        val jsonArray = JSONArray()
        for (job in memoryQueue) {
            val obj = JSONObject().apply {
                put("id", job.id)
                put("title", job.title)
                put("printType", job.printType)
                put("payloadFileName", job.payloadFileName)
                put("timestamp", job.timestamp)
                put("copies", job.copies)
                put("targetDeviceAddress", job.targetDeviceAddress)
                put("targetWifiIp", job.targetWifiIp)
                put("targetWifiPort", job.targetWifiPort)
            }
            jsonArray.put(obj)
        }
        fileStore.writeQueue(jsonArray.toString())
    }

    private fun migrateLegacyQueue() {
        val legacyFile = File(context.filesDir, LEGACY_QUEUE_FILENAME)
        if (!legacyFile.isFile) return
        try {
            val legacyJobs = JSONArray(legacyFile.readText(Charsets.UTF_8))
            for (index in 0 until legacyJobs.length()) {
                val obj = legacyJobs.getJSONObject(index)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val encoded = obj.optString("dataBase64", "")
                val decoded = try {
                    android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                } catch (_: IllegalArgumentException) {
                    ByteArray(0)
                }
                try {
                    if (decoded.isNotEmpty()) {
                        val payloadFileName = fileStore.writePayload(id, decoded)
                        memoryQueue.add(
                            SpoolJob(
                                id = id,
                                title = AppLocaleContext.wrap(context).getString(R.string.kt_job_document),
                                printType = obj.optString("printType", "DOC"),
                                payloadFileName = payloadFileName,
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                copies = obj.optInt("copies", 1),
                                targetDeviceAddress = obj.optString("targetDeviceAddress").takeIf(String::isNotBlank),
                                targetWifiIp = obj.optString("targetWifiIp").takeIf(String::isNotBlank),
                                targetWifiPort = obj.optInt("targetWifiPort", 9100)
                            )
                        )
                    }
                } finally {
                    decoded.fill(0)
                }
            }
            saveQueueToDisk()
        } finally {
            if (legacyFile.exists() && !legacyFile.delete()) {
                throw IOException("Unable to remove legacy print queue")
            }
        }
    }
}

package com.netraprint.app

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class SpoolerFileStore(
    cacheDir: File,
    private val now: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val DIRECTORY_NAME = "print_spooler"
        private const val QUEUE_FILENAME = "queue.json"
        private val SAFE_ID = Regex("[A-Za-z0-9-]{1,80}")
        private val PAYLOAD_FILENAME = Regex("[A-Za-z0-9-]{1,80}\\.payload")
    }

    private val directory = File(cacheDir, DIRECTORY_NAME)

    fun writePayload(id: String, bytes: ByteArray): String {
        require(SAFE_ID.matches(id)) { "Invalid spool job identifier" }
        ensureDirectory()
        val finalFile = File(directory, "$id.payload")
        val tempFile = File(directory, "$id.payload.tmp")
        try {
            tempFile.writeBytes(bytes)
            atomicReplace(tempFile, finalFile)
        } catch (error: Exception) {
            if (tempFile.exists() && !tempFile.delete()) {
                error.addSuppressed(IOException("Unable to remove temporary spool payload"))
            }
            throw error
        }
        return finalFile.name
    }

    fun readPayload(fileName: String): ByteArray? {
        if (!PAYLOAD_FILENAME.matches(fileName)) return null
        val file = File(directory, fileName)
        return if (file.isFile) file.readBytes() else null
    }

    fun payloadExists(fileName: String): Boolean {
        if (!PAYLOAD_FILENAME.matches(fileName)) return false
        return File(directory, fileName).isFile
    }

    fun deletePayload(fileName: String) {
        if (!PAYLOAD_FILENAME.matches(fileName)) return
        val file = File(directory, fileName)
        if (file.exists() && !file.delete()) {
            throw IOException("Unable to remove cached print payload")
        }
    }

    fun readQueue(): String? {
        val file = File(directory, QUEUE_FILENAME)
        return if (file.isFile) file.readText(Charsets.UTF_8) else null
    }

    fun writeQueue(json: String) {
        ensureDirectory()
        val file = File(directory, QUEUE_FILENAME)
        val tempFile = File(directory, "$QUEUE_FILENAME.tmp")
        try {
            tempFile.writeText(json, Charsets.UTF_8)
            atomicReplace(tempFile, file)
        } catch (error: Exception) {
            if (tempFile.exists() && !tempFile.delete()) {
                error.addSuppressed(IOException("Unable to remove temporary spool queue file"))
            }
            throw error
        }
    }

    fun deleteQueue() {
        val file = File(directory, QUEUE_FILENAME)
        if (file.exists() && !file.delete()) {
            throw IOException("Unable to remove cached print queue")
        }
    }

    fun deleteAllPayloads() {
        if (!directory.isDirectory) return
        directory.listFiles()?.filter { PAYLOAD_FILENAME.matches(it.name) }?.forEach { file ->
            if (!file.delete()) {
                throw IOException("Unable to remove cached print payload")
            }
        }
    }

    fun sweep(referencedPayloads: Set<String>) {
        if (!directory.isDirectory) return
        val staleBefore = now() - TTL_MILLIS
        directory.listFiles()?.forEach { file ->
            val isPayload = PAYLOAD_FILENAME.matches(file.name)
            val isTemp = file.name.endsWith(".tmp")
            if ((isTemp || isPayload && file.name !in referencedPayloads && file.lastModified() < staleBefore) &&
                !file.delete()
            ) {
                throw IOException("Unable to remove an expired spool cache file")
            }
        }
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create private spool cache directory")
        }
    }

    private fun atomicReplace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

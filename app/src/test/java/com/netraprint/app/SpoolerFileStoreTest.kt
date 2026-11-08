package com.netraprint.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SpoolerFileStoreTest {
    @Test
    fun payloadIsPrivateToCacheAndDeletedOnCompletion() {
        val root = Files.createTempDirectory("netraprint-spool-test").toFile()
        try {
            val store = SpoolerFileStore(root)
            val id = "job-123"
            val payloadName = store.writePayload(id, byteArrayOf(1, 2, 3))
            val payload = File(root, "print_spooler/$payloadName")

            assertTrue(payload.canonicalPath.startsWith(root.canonicalPath + File.separator))
            assertArrayEquals(byteArrayOf(1, 2, 3), store.readPayload(payloadName))
            store.deletePayload(payloadName)
            assertFalse(payload.exists())
            assertNull(store.readPayload(payloadName))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun startupSweepRemovesExpiredOrphanPayloadsButKeepsLiveJobs() {
        val root = Files.createTempDirectory("netraprint-spool-sweep-test").toFile()
        try {
            var now = 10L * SpoolerFileStore.TTL_MILLIS
            val store = SpoolerFileStore(root) { now }
            val liveName = store.writePayload("live-job", byteArrayOf(1))
            val orphanName = store.writePayload("orphan-job", byteArrayOf(2))
            val directory = File(root, "print_spooler")
            val abandonedTemp = File(directory, "interrupted.payload.tmp")
            abandonedTemp.writeBytes(byteArrayOf(3))
            File(directory, liveName).setLastModified(now)
            val orphan = File(directory, orphanName)
            orphan.setLastModified(now - SpoolerFileStore.TTL_MILLIS - 1)

            store.sweep(setOf(liveName))

            assertTrue(File(directory, liveName).exists())
            assertFalse(orphan.exists())
            assertFalse(abandonedTemp.exists())
            assertTrue(store.payloadExists(liveName))
            assertFalse(store.payloadExists("../outside.payload"))
            now++
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun payloadNamesCannotEscapeCacheDirectory() {
        val root = Files.createTempDirectory("netraprint-spool-path-test").toFile()
        try {
            val store = SpoolerFileStore(root)
            assertNull(store.readPayload("../outside.payload"))
            store.deletePayload("../outside.payload")
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}

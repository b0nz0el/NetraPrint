package com.david.printerjtm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PrintSpoolerTest {

    @Test
    fun testSpoolJobCreation() {
        val testId = UUID.randomUUID().toString()
        val job = PrintSpoolerManager.SpoolJob(
            id = testId,
            title = "Struk POS #1234",
            printType = "LABEL",
            payloadFileName = "$testId.payload",
            copies = 2,
            targetDeviceAddress = "00:11:22:33:44:55"
        )

        assertEquals(testId, job.id)
        assertEquals("Struk POS #1234", job.title)
        assertEquals("LABEL", job.printType)
        assertEquals(2, job.copies)
        assertEquals("00:11:22:33:44:55", job.targetDeviceAddress)
        assertTrue(job.timestamp > 0)
    }

    @Test
    fun testSpoolJobDefaultCopies() {
        val id = UUID.randomUUID().toString()
        val job = PrintSpoolerManager.SpoolJob(
            id = id,
            title = "Resi J&T",
            printType = "IMAGE",
            payloadFileName = "$id.payload"
        )
        assertEquals(1, job.copies)
        assertEquals(9100, job.targetWifiPort)
    }
}

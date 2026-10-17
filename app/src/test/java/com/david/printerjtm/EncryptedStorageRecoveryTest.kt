package com.david.printerjtm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

class EncryptedStorageRecoveryTest {
    @Test
    fun recoverableFailureResetsAndOpensFreshEncryptedStore() {
        var attempts = 0
        var resets = 0
        var notified = false
        val recovery = EncryptedStorageRecovery(
            openStorage = {
                attempts++
                if (attempts == 1) throw GeneralSecurityException("corrupt key")
                "encrypted-store"
            },
            resetStorage = { resets++ },
            isRecoverableFailure = { it is GeneralSecurityException },
            onRecovered = { notified = true }
        )

        assertEquals("encrypted-store", recovery.open())
        assertEquals(2, attempts)
        assertEquals(1, resets)
        assertTrue(notified)
    }

    @Test
    fun nonRecoverableFailureDoesNotResetOrFallBack() {
        var resets = 0
        val recovery = EncryptedStorageRecovery(
            openStorage = { throw IllegalArgumentException("invalid configuration") },
            resetStorage = { resets++ },
            isRecoverableFailure = { it is GeneralSecurityException },
            onRecovered = {}
        )

        try {
            recovery.open()
            throw AssertionError("Expected initialization failure")
        } catch (expected: IllegalArgumentException) {
            assertEquals("invalid configuration", expected.message)
        }
        assertEquals(0, resets)
    }

    @Test
    fun failedRecoveryDoesNotReportSuccess() {
        var notified = false
        var attempts = 0
        val recovery = EncryptedStorageRecovery(
            openStorage = {
                attempts++
                throw GeneralSecurityException("still failing")
            },
            resetStorage = {},
            isRecoverableFailure = { it is GeneralSecurityException },
            onRecovered = { notified = true }
        )

        try {
            recovery.open()
            throw AssertionError("Expected second initialization failure")
        } catch (expected: GeneralSecurityException) {
            assertEquals(1, expected.suppressed.size)
        }
        assertEquals(2, attempts)
        assertFalse(notified)
    }
}

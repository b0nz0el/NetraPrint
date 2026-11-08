package com.netraprint.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DitheringEngineTest {

    @Test
    fun testDitherModesAvailability() {
        val modes = DitheringEngine.DitherMode.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(DitheringEngine.DitherMode.FLOYD_STEINBERG))
        assertTrue(modes.contains(DitheringEngine.DitherMode.ATKINSON))
        assertTrue(modes.contains(DitheringEngine.DitherMode.THRESHOLD))
    }

    @Test
    fun testDitherModeProperties() {
        val fs = DitheringEngine.DitherMode.FLOYD_STEINBERG
        assertNotNull(fs.displayName)
        assertNotNull(fs.description)
        assertTrue(fs.displayName.contains("Floyd-Steinberg"))

        val atkinson = DitheringEngine.DitherMode.ATKINSON
        assertTrue(atkinson.displayName.contains("Atkinson"))

        val threshold = DitheringEngine.DitherMode.THRESHOLD
        assertTrue(threshold.displayName.contains("Tegas"))
    }
}

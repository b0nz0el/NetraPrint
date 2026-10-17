package com.david.printerjtm

import android.os.Build
import android.util.Log
import java.io.File

/**
 * Lightweight helper to detect root status and compromised environment.
 * Checks build tags and known su binary paths to ensure security of sensitive print transactions.
 */
object RootDetectionHelper {

    private const val TAG = "RootDetection"

    private val SU_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su"
    )

    /**
     * Determines whether the device appears to be rooted or running a custom test-keys build.
     */
    fun isDeviceRooted(): Boolean {
        return checkBuildTags() || checkSuBinaries() || checkSuCommand()
    }

    private fun checkBuildTags(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            Log.w(TAG, "Root risk detected")
            return true
        }
        return false
    }

    private fun checkSuBinaries(): Boolean {
        for (path in SU_PATHS) {
            try {
                if (File(path).exists()) {
                    Log.w(TAG, "Root risk detected")
                    return true
                }
            } catch (_: Exception) {
                // Ignore permission or file system exceptions
            }
        }
        return false
    }

    private fun checkSuCommand(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }
}

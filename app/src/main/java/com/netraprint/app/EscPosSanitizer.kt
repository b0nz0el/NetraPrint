package com.netraprint.app

object EscPosSanitizer {

    /**
     * Sanitizes user input to prevent ESC/POS command injection attacks.
     * Strips dangerous control bytes:
     * - 0x1B (ESC - Escape command initiator)
     * - 0x1D (GS - Group Separator command initiator)
     * - 0x10 (DLE - Data Link Escape realtime command)
     * - 0x1C (FS - File Separator)
     * - 0x00 (NUL - Null character)
     */
    fun sanitize(input: String?): String {
        if (input.isNullOrEmpty()) return ""

        val sb = StringBuilder(input.length)
        for (char in input) {
            val code = char.code
            if (code == 0x1B || code == 0x1D || code == 0x10 || code == 0x1C || code == 0x00) {
                continue
            }
            sb.append(char)
        }
        return sb.toString()
    }
}

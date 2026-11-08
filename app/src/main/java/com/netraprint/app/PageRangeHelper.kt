package com.netraprint.app

object PageRangeHelper {
    /**
     * Parses a page range string (e.g. "1-3, 5, 8-10") into a sorted, 0-indexed list of page numbers.
     * Throws IllegalArgumentException if any page is out of bounds (1..totalPages) or format is invalid.
     */
    fun parsePageRange(input: String, totalPages: Int): List<Int> {
        if (input.isBlank()) {
            throw IllegalArgumentException("Rentang halaman tidak boleh kosong.")
        }
        val result = mutableSetOf<Int>()
        val tokens = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        if (tokens.isEmpty()) {
            throw IllegalArgumentException("Rentang halaman tidak valid.")
        }

        for (token in tokens) {
            if (token.contains("-")) {
                val parts = token.split("-").map { it.trim() }
                if (parts.size != 2) {
                    throw IllegalArgumentException("Format rentang tidak valid: '$token'")
                }
                val start = parts[0].toIntOrNull() ?: throw IllegalArgumentException("Nomor halaman tidak valid: '${parts[0]}'")
                val end = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Nomor halaman tidak valid: '${parts[1]}'")
                if (start > end) {
                    throw IllegalArgumentException("Rentang terbalik: $start-$end")
                }
                if (start < 1 || end > totalPages) {
                    throw IllegalArgumentException("Halaman $start-$end di luar batas dokumen (1 - $totalPages).")
                }
                for (p in start..end) {
                    result.add(p - 1)
                }
            } else {
                val page = token.toIntOrNull() ?: throw IllegalArgumentException("Nomor halaman tidak valid: '$token'")
                if (page < 1 || page > totalPages) {
                    throw IllegalArgumentException("Halaman $page di luar batas dokumen (1 - $totalPages).")
                }
                result.add(page - 1)
            }
        }

        return result.sorted()
    }
}

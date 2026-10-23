package com.omer.belestiviclone.shared.transfer

/**
 * Pure Kotlin QR Code Matrix Generator (Model 2, Byte Mode, Level L/M).
 * Self-contained without external dependencies.
 */
object QrMatrix {

    // Reed-Solomon GF(256) with primitive polynomial 0x11D (285)
    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            exp[i + 255] = x
            log[x] = i
            x = x shl 1
            if (x >= 256) x = x xor 0x11D
        }
        log[0] = 0
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return exp[log[a] + log[b]]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val root = exp[i]
            for (j in poly.indices) {
                next[j] = next[j] xor gfMul(poly[j], root)
                next[j + 1] = next[j + 1] xor poly[j]
            }
            poly = next
        }
        return poly
    }

    private fun rsEncode(data: IntArray, ecCount: Int): IntArray {
        val gen = rsGeneratorPoly(ecCount)
        val res = IntArray(data.size + ecCount)
        System.arraycopy(data, 0, res, 0, data.size)

        for (i in data.indices) {
            val coef = res[i]
            if (coef != 0) {
                for (j in gen.indices) {
                    res[i + j] = res[i + j] xor gfMul(gen[j], coef)
                }
            }
        }
        val ec = IntArray(ecCount)
        System.arraycopy(res, data.size, ec, 0, ecCount)
        return ec
    }

    data class QrVersionInfo(
        val version: Int,
        val size: Int,
        val totalCodewords: Int,
        val ecCodewords: Int,
        val alignments: IntArray
    ) {
        val dataCodewords: Int get() = totalCodewords - ecCodewords
    }

    // Level L parameters for Version 2..5 (supports 32-106 bytes)
    private val versions = listOf(
        QrVersionInfo(version = 2, size = 25, totalCodewords = 44, ecCodewords = 10, alignments = intArrayOf(6, 18)),
        QrVersionInfo(version = 3, size = 29, totalCodewords = 70, ecCodewords = 15, alignments = intArrayOf(6, 22)),
        QrVersionInfo(version = 4, size = 33, totalCodewords = 100, ecCodewords = 20, alignments = intArrayOf(6, 26)),
        QrVersionInfo(version = 5, size = 37, totalCodewords = 134, ecCodewords = 26, alignments = intArrayOf(6, 30))
    )

    fun generate(text: String): Array<BooleanArray> {
        val bytes = text.encodeToByteArray()
        val vi = versions.firstOrNull { it.dataCodewords - 3 >= bytes.size }
            ?: versions.last()

        val data = buildDataCodewords(bytes, vi.dataCodewords)
        val ec = rsEncode(data, vi.ecCodewords)

        val fullCodewords = IntArray(vi.totalCodewords)
        System.arraycopy(data, 0, fullCodewords, 0, data.size)
        System.arraycopy(ec, 0, fullCodewords, data.size, ec.size)

        val matrix = Array(vi.size) { BooleanArray(vi.size) }
        val reserved = Array(vi.size) { BooleanArray(vi.size) }

        placeFunctionPatterns(matrix, reserved, vi)
        placeDataBits(matrix, reserved, fullCodewords, vi)
        applyMaskPattern(matrix, reserved, vi)
        placeFormatInformation(matrix, reserved, vi)

        return matrix
    }

    private fun buildDataCodewords(bytes: ByteArray, capacity: Int): IntArray {
        val bits = mutableListOf<Int>()
        fun putBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value shr i) and 1)
            }
        }

        // Byte mode indicator: 0100
        putBits(0b0100, 4)
        // Character count indicator (8 bits for version 1-9 byte mode)
        putBits(bytes.size, 8)
        // Data bytes
        for (b in bytes) {
            putBits(b.toInt() and 0xFF, 8)
        }
        // Terminator (up to 4 zeroes)
        val termLen = minOf(4, capacity * 8 - bits.size)
        putBits(0, termLen)
        // Pad to byte boundary
        while (bits.size % 8 != 0) {
            bits.add(0)
        }
        // Pad codewords: 0xEC and 0x11
        val pad = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < capacity * 8) {
            putBits(pad[padIdx % 2], 8)
            padIdx++
        }

        val codewords = IntArray(capacity)
        for (i in 0 until capacity) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bits[i * 8 + b]
            }
            codewords[i] = v
        }
        return codewords
    }

    private fun placeFunctionPatterns(m: Array<BooleanArray>, r: Array<BooleanArray>, vi: QrVersionInfo) {
        val s = vi.size
        // Finder patterns: top-left (0,0), top-right (0, s-7), bottom-left (s-7, 0)
        drawFinder(m, r, 0, 0)
        drawFinder(m, r, 0, s - 7)
        drawFinder(m, r, s - 7, 0)

        // Separators around finders
        drawSeparators(m, r, s)

        // Timing patterns
        for (i in 8 until s - 8) {
            val v = (i % 2 == 0)
            if (!r[6][i]) { m[6][i] = v; r[6][i] = true }
            if (!r[i][6]) { m[i][6] = v; r[i][6] = true }
        }

        // Alignment patterns
        for (row in vi.alignments) {
            for (col in vi.alignments) {
                if (r[row][col]) continue
                drawAlignment(m, r, row, col)
            }
        }

        // Dark module
        m[4 * vi.version + 9][8] = true
        r[4 * vi.version + 9][8] = true

        // Reserve Format Information areas
        for (i in 0..8) {
            r[8][i] = true
            r[i][8] = true
        }
        for (i in s - 8 until s) {
            r[8][i] = true
            r[i][8] = true
        }
    }

    private fun drawFinder(m: Array<BooleanArray>, r: Array<BooleanArray>, r0: Int, c0: Int) {
        for (dr in 0 until 7) {
            for (dc in 0 until 7) {
                val isBlack = dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
                m[r0 + dr][c0 + dc] = isBlack
                r[r0 + dr][c0 + dc] = true
            }
        }
    }

    private fun drawSeparators(m: Array<BooleanArray>, r: Array<BooleanArray>, s: Int) {
        // Top-left
        for (i in 0..7) {
            setSep(m, r, 7, i)
            setSep(m, r, i, 7)
        }
        // Top-right
        for (i in 0..7) {
            setSep(m, r, 7, s - 1 - i)
            setSep(m, r, i, s - 8)
        }
        // Bottom-left
        for (i in 0..7) {
            setSep(m, r, s - 8, i)
            setSep(m, r, s - 1 - i, 7)
        }
    }

    private fun setSep(m: Array<BooleanArray>, r: Array<BooleanArray>, row: Int, col: Int) {
        if (row in m.indices && col in m[0].indices) {
            m[row][col] = false
            r[row][col] = true
        }
    }

    private fun drawAlignment(m: Array<BooleanArray>, r: Array<BooleanArray>, cr: Int, cc: Int) {
        for (dr in -2..2) {
            for (dc in -2..2) {
                val isBlack = dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0)
                m[cr + dr][cc + dc] = isBlack
                r[cr + dr][cc + dc] = true
            }
        }
    }

    private fun placeDataBits(m: Array<BooleanArray>, r: Array<BooleanArray>, codewords: IntArray, vi: QrVersionInfo) {
        val s = vi.size
        var bitIndex = 0
        val totalBits = codewords.size * 8

        var col = s - 1
        var upwards = true

        while (col > 0) {
            if (col == 6) col-- // Skip vertical timing column
            val rows = if (upwards) (s - 1 downTo 0) else (0 until s)
            for (row in rows) {
                for (c in intArrayOf(col, col - 1)) {
                    if (!r[row][c]) {
                        val bit = if (bitIndex < totalBits) {
                            (codewords[bitIndex / 8] shr (7 - (bitIndex % 8))) and 1
                        } else 0
                        m[row][c] = (bit == 1)
                        bitIndex++
                    }
                }
            }
            upwards = !upwards
            col -= 2
        }
    }

    private fun applyMaskPattern(m: Array<BooleanArray>, r: Array<BooleanArray>, vi: QrVersionInfo) {
        // Using Mask 0: (row + col) % 2 == 0
        for (row in 0 until vi.size) {
            for (col in 0 until vi.size) {
                if (!r[row][col]) {
                    if ((row + col) % 2 == 0) {
                        m[row][col] = !m[row][col]
                    }
                }
            }
        }
    }

    private fun placeFormatInformation(m: Array<BooleanArray>, r: Array<BooleanArray>, vi: QrVersionInfo) {
        // Level L (01) + Mask 0 (000) -> 01000
        // Precomputed 15-bit format with BCH code and mask 0x5412:
        // Format string for (L, Mask 0) = 0x77C4 (111011111000100 in 15 bits)
        val formatBits = 0x77C4
        val s = vi.size

        // Place around top-left
        for (i in 0..5) {
            m[8][i] = ((formatBits shr (14 - i)) and 1) == 1
        }
        m[8][7] = ((formatBits shr 8) and 1) == 1
        m[8][8] = ((formatBits shr 7) and 1) == 1
        m[7][8] = ((formatBits shr 6) and 1) == 1
        for (i in 0..5) {
            m[5 - i][8] = ((formatBits shr (5 - i)) and 1) == 1
        }

        // Place around other finders
        for (i in 0..7) {
            m[8][s - 1 - i] = ((formatBits shr i) and 1) == 1
        }
        for (i in 0..6) {
            m[s - 7 + i][8] = ((formatBits shr (14 - i)) and 1) == 1
        }
    }
}

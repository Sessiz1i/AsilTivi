package com.omer.belestiviclone.shared.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QrMatrixTest {

    @Test
    fun generatesValidMatrixForHttpUrl() {
        val url = "http://192.168.1.100:8080"
        val matrix = QrMatrix.generate(url)

        // Version 2..5 size should be in 25..37 range
        assertTrue(matrix.size in 25..37)
        assertEquals(matrix.size, matrix[0].size)

        // Top-left finder pattern center (row 3, col 3) must be black (true)
        assertTrue(matrix[3][3])
        // Top-left finder pattern border (row 0, col 0) must be black (true)
        assertTrue(matrix[0][0])
        // Top-left finder pattern inner white space (row 1, col 1) must be false
        assertEquals(false, matrix[1][1])

        // Top-right finder pattern (row 0, col size-1) must be true
        assertTrue(matrix[0][matrix.size - 1])
        // Bottom-left finder pattern (row size-1, col 0) must be true
        assertTrue(matrix[matrix.size - 1][0])
    }

    @Test
    fun generatesMatrixForDifferentLengths() {
        val shortUrl = "http://10.0.0.2:80"
        val m1 = QrMatrix.generate(shortUrl)
        assertTrue(m1.isNotEmpty())

        val longerUrl = "http://192.168.100.254:8080/transfer-endpoint"
        val m2 = QrMatrix.generate(longerUrl)
        assertTrue(m2.isNotEmpty())
    }
}

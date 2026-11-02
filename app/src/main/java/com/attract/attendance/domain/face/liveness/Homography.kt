package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.Point3
import kotlin.math.sqrt

/**
 * 3x3 Projective transformation (Homography) solver and evaluator (doc-20 WP7.1).
 * Pure Kotlin, zero external dependencies.
 */
class Homography(private val h: DoubleArray) {
    init {
        require(h.size == 9) { "Homography must have 9 elements." }
    }

    fun transform(x: Double, y: Double): Pair<Double, Double> {
        val w = h[6] * x + h[7] * y + h[8]
        val safeW = if (kotlin.math.abs(w) > 1e-7) w else 1.0
        val tx = (h[0] * x + h[1] * y + h[2]) / safeW
        val ty = (h[3] * x + h[4] * y + h[5]) / safeW
        return Pair(tx, ty)
    }

    companion object {
        /**
         * Solves 3x3 homography H mapping 4 source points to 4 destination points.
         * Uses 8x8 Gaussian elimination with partial pivoting.
         */
        fun solve4Points(
            src: List<Point3>,
            dst: List<Point3>,
        ): Homography? {
            if (src.size < 4 || dst.size < 4) return null

            // 8x8 matrix A and 8x1 vector B for A * h = B (where h9 = 1)
            val a = Array(8) { DoubleArray(8) }
            val b = DoubleArray(8)

            for (i in 0 until 4) {
                val xi = src[i].x.toDouble()
                val yi = src[i].y.toDouble()
                val ui = dst[i].x.toDouble()
                val vi = dst[i].y.toDouble()

                val r0 = 2 * i
                val r1 = 2 * i + 1

                a[r0][0] = xi
                a[r0][1] = yi
                a[r0][2] = 1.0
                a[r0][3] = 0.0
                a[r0][4] = 0.0
                a[r0][5] = 0.0
                a[r0][6] = -ui * xi
                a[r0][7] = -ui * yi
                b[r0] = ui

                a[r1][0] = 0.0
                a[r1][1] = 0.0
                a[r1][2] = 0.0
                a[r1][3] = xi
                a[r1][4] = yi
                a[r1][5] = 1.0
                a[r1][6] = -vi * xi
                a[r1][7] = -vi * yi
                b[r1] = vi
            }

            // Gaussian elimination with partial pivoting
            for (col in 0 until 8) {
                var maxRow = col
                var maxVal = kotlin.math.abs(a[col][col])
                for (row in col + 1 until 8) {
                    val absVal = kotlin.math.abs(a[row][col])
                    if (absVal > maxVal) {
                        maxVal = absVal
                        maxRow = row
                    }
                }

                if (maxVal < 1e-10) return null // Singular

                if (maxRow != col) {
                    val tempRow = a[col]
                    a[col] = a[maxRow]
                    a[maxRow] = tempRow

                    val tempB = b[col]
                    b[col] = b[maxRow]
                    b[maxRow] = tempB
                }

                for (row in col + 1 until 8) {
                    val factor = a[row][col] / a[col][col]
                    for (c in col until 8) {
                        a[row][c] -= factor * a[col][c]
                    }
                    b[row] -= factor * b[col]
                }
            }

            // Back substitution
            val hVec = DoubleArray(9)
            hVec[8] = 1.0
            for (row in 7 downTo 0) {
                var sum = b[row]
                for (col in row + 1 until 8) {
                    sum -= a[row][col] * hVec[col]
                }
                hVec[row] = sum / a[row][row]
            }

            return Homography(hVec)
        }
    }
}

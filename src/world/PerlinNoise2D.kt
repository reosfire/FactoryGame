package world

import kotlin.math.*
import kotlin.random.*

class PerlinNoise2D(seed: Long) {

    // Permutation table, doubled to avoid index wrapping issues
    private val p: IntArray = IntArray(512)

    init {
        // Use Kotlin's Random initialized with the seed
        val random = Random(seed)
        val originalP = IntArray(256) { it } // Array [0, 1, 2, ..., 255]

        // Shuffle the array using the provided seed
        originalP.shuffle(random)

        // Double the permutation table to avoid modulo operations later
        for (i in 0..255) {
            p[i] = originalP[i]
            p[i + 256] = originalP[i]
        }
    }

    fun noise(x: Double, y: Double): Double {
        // Find the unit grid cell coordinates containing the point (x, y)
        val xi = floor(x).toInt() and 255 // Use bitwise AND for faster modulo 256
        val yi = floor(y).toInt() and 255

        // Get the fractional parts of x and y within the cell
        val xf = x - floor(x)
        val yf = y - floor(y)

        // Apply the fade function (smoothstep) to the fractional parts
        // 6t^5 - 15t^4 + 10t^3
        val u = fade(xf)
        val v = fade(yf)

        // Hash coordinates of the 4 square corners
        val aa = p[p[xi] + yi]
        val ab = p[p[xi] + yi + 1]
        val ba = p[p[xi + 1] + yi]
        val bb = p[p[xi + 1] + yi + 1]

        // Calculate dot products between gradient vectors and distance vectors
        // grad(hash, x, y) calculates dot(gradient[hash % 8], (x, y))
        val gradAA = grad(aa, xf, yf)         // Top-left corner
        val gradBA = grad(ba, xf - 1.0, yf)     // Top-right corner
        val gradAB = grad(ab, xf, yf - 1.0)     // Bottom-left corner
        val gradBB = grad(bb, xf - 1.0, yf - 1.0) // Bottom-right corner

        // Interpolate along x-axis
        val lerpX1 = lerp(gradAA, gradBA, u)
        val lerpX2 = lerp(gradAB, gradBB, u)

        // Interpolate along y-axis
        val result = lerp(lerpX1, lerpX2, v)

        // Theoretical range is approx [-sqrt(2)/2, sqrt(2)/2] = [-0.707, 0.707]
        // Some implementations scale this to [-1, 1], e.g., by multiplying by sqrt(2)
        // We return the standard range here.
        return result
    }

    private fun fade(t: Double): Double {
        return t * t * t * (t * (t * 6 - 15) + 10)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double {
        return a + t * (b - a)
    }

    private fun grad(hash: Int, x: Double, y: Double): Double {
        // Use the lower 3 bits of the hash to select one of the 8 gradients
        return when (hash and 7) {
            0 -> x + y    // Gradient (1, 1)
            1 -> -x + y   // Gradient (-1, 1)
            2 -> x - y    // Gradient (1, -1)
            3 -> -x - y   // Gradient (-1, -1)
            4 -> x        // Gradient (1, 0)
            5 -> -x       // Gradient (-1, 0)
            6 -> y        // Gradient (0, 1)
            7 -> -y       // Gradient (0, -1)
            else -> 0.0   // Should not happen
        }
    }
}

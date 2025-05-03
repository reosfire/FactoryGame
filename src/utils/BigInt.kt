package utils

import kotlin.math.*

fun Long.toBigInt(): BigInt = BigInt.valueOf(this)
fun Int.toBigInt(): BigInt = BigInt.valueOf(this.toLong())
fun String.toBigInt(): BigInt = BigInt(this)

class BigInt private constructor(
    private val signNumber: Int,
    private val magnitude: IntArray
) : Comparable<BigInt> {

    fun add(other: BigInt): BigInt {
        return when {
            this.signNumber == 0 -> other
            other.signNumber == 0 -> this
            this.signNumber == other.signNumber -> create(this.signNumber, addMagnitudes(this.magnitude, other.magnitude))
            else -> { // Signs differ
                when (compareMagnitudes(this.magnitude, other.magnitude)) {
                    1 -> create(this.signNumber, subtractMagnitudes(this.magnitude, other.magnitude)) // |this| > |other|
                    -1 -> create(other.signNumber, subtractMagnitudes(other.magnitude, this.magnitude)) // |other| > |this|
                    else -> ZERO // Magnitudes are equal, result is 0
                }
            }
        }
    }

    fun subtract(other: BigInt): BigInt {
        return when {
            other.signNumber == 0 -> this
            this.signNumber == 0 -> other.negate()
            this.signNumber != other.signNumber -> create(this.signNumber, addMagnitudes(this.magnitude, other.magnitude)) // a - (-b) = a + b or (-a) - b = -(a+b)
            else -> { // Signs are the same
                when (compareMagnitudes(this.magnitude, other.magnitude)) {
                    1 -> create(this.signNumber, subtractMagnitudes(this.magnitude, other.magnitude)) // a - b where |a| > |b|
                    -1 -> create(-this.signNumber, subtractMagnitudes(other.magnitude, this.magnitude)) // a - b where |b| > |a| -> -(b-a)
                    else -> ZERO // Magnitudes are equal, result is 0
                }
            }
        }
    }

    fun multiply(other: BigInt): BigInt {
        return when {
            this.signNumber == 0 || other.signNumber == 0 -> ZERO
            else -> {
                val resultMag = multiplyMagnitudes(this.magnitude, other.magnitude)
                val resultSign = this.signNumber * other.signNumber
                create(resultSign, resultMag)
            }
        }
    }

    /** Performs division, returning the quotient. Throws ArithmeticException if divisor is zero. */
    fun divide(divisor: BigInt): BigInt {
        if (divisor.signNumber == 0) throw ArithmeticException("BigInt division by zero")
        if (this.signNumber == 0) return ZERO

        val (quotientMag, _) = divideAndRemainderMagnitudes(this.magnitude, divisor.magnitude)
        val resultSign = if (quotientMag.isEmpty()) 0 else this.signNumber * divisor.signNumber
        return create(resultSign, quotientMag)
    }

    /** Performs division, returning the remainder. Throws ArithmeticException if divisor is zero. */
    fun remainder(divisor: BigInt): BigInt {
        if (divisor.signNumber == 0) throw ArithmeticException("BigInt division by zero")
        if (this.signNumber == 0) return ZERO

        val (_, remainderMag) = divideAndRemainderMagnitudes(this.magnitude, divisor.magnitude)
        // Remainder sign matches the dividend's sign
        val resultSign = if (remainderMag.isEmpty()) 0 else this.signNumber
        return create(resultSign, remainderMag)
    }

    fun divideAndRemainder(divisor: BigInt): Pair<BigInt, BigInt> {
        if (divisor.signNumber == 0) throw ArithmeticException("BigInt division by zero")
        if (this.signNumber == 0) return Pair(ZERO, ZERO)

        val (quotientMag, remainderMag) = divideAndRemainderMagnitudes(this.magnitude, divisor.magnitude)

        val quotientSign = if (quotientMag.isEmpty()) 0 else this.signNumber * divisor.signNumber
        val remainderSign = if (remainderMag.isEmpty()) 0 else this.signNumber // Remainder sign matches dividend

        val quotient = create(quotientSign, quotientMag)
        val remainder = create(remainderSign, remainderMag)
        return Pair(quotient, remainder)
    }

    fun pow(exponent: Int): BigInt {
        if (exponent < 0) throw IllegalArgumentException("Negative exponent not supported")
        if (this.signNumber == 0) return ZERO
        if (exponent == 0) return ONE

        var result = ONE
        var base = this

        var exp = exponent
        while (exp > 0) {
            if (exp and 1 == 1) {
                result *= base
            }
            base *= base
            exp = exp shr 1
        }
        return result
    }

    fun negate(): BigInt = if (signNumber == 0) ZERO else create(-signNumber, magnitude)

    fun abs(): BigInt = if (signNumber >= 0) this else create(1, magnitude)

    override fun compareTo(other: BigInt): Int {
        return if (this.signNumber != other.signNumber) {
            this.signNumber.compareTo(other.signNumber)
        } else {
            when (this.signNumber) {
                0 -> 0 // Both are zero
                1 -> compareMagnitudes(this.magnitude, other.magnitude) // Both positive
                -1 -> compareMagnitudes(other.magnitude, this.magnitude) // Both negative (reverse comparison)
                else -> error("Invalid signum")
            }
        }
    }

    override fun toString(): String {
        if (signNumber == 0) return "0"

        var current = this.abs()
        val resultBuilder = StringBuilder()

        while (current.signNumber != 0) {
            val (quotient, remainder) = current.divideAndRemainder(TEN)

            resultBuilder.append(remainder.toIntAbs())
            current = quotient
        }

        val magnitudeString = resultBuilder.reverse().toString()
        return if (signNumber < 0) "-$magnitudeString" else magnitudeString
    }

    fun toLong(): Long {
        if (signNumber == 0) return 0L

        val low = magnitude.getOrElse(0) { 0 }.toLong()
        val high = magnitude.getOrElse(1) { 0 }.toLong()

        return signNumber * ((high shl 32) or (low and LONG_MASK))
    }

    fun toIntAbs() = magnitude.getOrElse(0) { 0 }
    fun toInt() = signNumber * toIntAbs()

    fun mostSignificant1Bit(): Int {
        if (signNumber == 0) return 0
        for (i in magnitude.indices.reversed()) {
            if (magnitude[i] != 0) {
                return i * 32 + magnitude[i].mostSignificant1Bit()
            }
        }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as BigInt
        if (signNumber != other.signNumber) return false
        if (signNumber == 0 && other.signNumber == 0) return true
        return magnitude.contentEquals(other.magnitude)
    }

    override fun hashCode(): Int {
        var result = signNumber
        result = 31 * result + magnitude.contentHashCode()
        return result
    }

    operator fun plus(other: BigInt): BigInt = add(other)
    operator fun minus(other: BigInt): BigInt = subtract(other)
    operator fun times(other: BigInt): BigInt = multiply(other)
    operator fun div(other: BigInt): BigInt = divide(other)
    operator fun rem(other: BigInt): BigInt = remainder(other)
    operator fun unaryMinus(): BigInt = negate()

    companion object {
        val ZERO = BigInt(0, IntArray(0))
        val ONE = BigInt(1, intArrayOf(1))
        val TEN = BigInt(1, intArrayOf(10))

        private fun create(signum: Int, mag: IntArray): BigInt {
            val normMag = stripLeadingZeros(mag)
            return when {
                normMag.isEmpty() -> ZERO
                signum == 0 -> ZERO
                else -> BigInt(signum, normMag)
            }
        }

        fun valueOf(value: Long): BigInt {
            return when (value) {
                0L -> ZERO
                1L -> ONE
                10L -> TEN
                Long.MIN_VALUE -> BigInt(-1, intArrayOf(0, Int.MIN_VALUE)) // Special case for Long.MIN_VALUE
                else -> {
                    val absValue = abs(value)
                    val sign = if (value > 0) 1 else -1
                    val high = (absValue ushr 32).toInt()
                    val low = (absValue and LONG_MASK).toInt()
                    if (high == 0) {
                        create(sign, intArrayOf(low))
                    } else {
                        create(sign, intArrayOf(low, high))
                    }
                }
            }
        }

        operator fun invoke(value: String): BigInt {
            val len = value.length
            if (len == 0) throw NumberFormatException("Zero length BigInt")

            var cursor = 0
            var sign = 1
            when (value[0]) {
                '-' -> {
                    sign = -1
                    cursor++
                }
                '+' -> cursor++
            }

            if (cursor == len) throw NumberFormatException("Sign only BigInt")

            // Simple parsing: multiply by 10 and add digit
            var result = ZERO
            while (cursor < len) {
                val digit = value[cursor].digitToIntOrNull()
                    ?: throw NumberFormatException("Invalid character in BigInt: ${value[cursor]}")
                if (digit == 0 && result.signNumber == 0) {
                    cursor++ // Skip leading zeros after sign
                    continue
                }
                result = result * TEN + create(1, intArrayOf(digit))
                cursor++
            }

            return if (result.signNumber == 0) ZERO else create(sign, result.magnitude)
        }
    }
}

private const val LONG_MASK = 0xFFFFFFFFL

private fun stripLeadingZeros(value: IntArray): IntArray {
    var keep = value.size
    while (keep > 0 && value[keep - 1] == 0) {
        keep--
    }
    return if (keep == value.size) value else value.copyOf(keep)
}

private fun addMagnitudes(a: IntArray, b: IntArray): IntArray {
    val (longer, shorter) = if (a.size >= b.size) a to b else b to a
    val result = IntArray(longer.size)
    var carry = 0L
    var i = 0

    // Add common part
    while (i < shorter.size) {
        val sum = (longer[i].toLong() and LONG_MASK) + (shorter[i].toLong() and LONG_MASK) + carry
        result[i] = sum.toInt()
        carry = sum ushr 32
        i++
    }

    // Add remaining part of longer array
    while (i < longer.size) {
        val sum = (longer[i].toLong() and LONG_MASK) + carry
        result[i] = sum.toInt()
        carry = sum ushr 32
        i++
    }

    return if (carry > 0) {
        result + carry.toInt() // Append carry if needed
    } else {
        result
    }
}

// Subtracts magnitudes (a must be >= b)
private fun subtractMagnitudes(a: IntArray, b: IntArray): IntArray {
    val result = IntArray(a.size)
    var borrow = 0L
    var i = 0

    // Subtract common part
    while (i < b.size) {
        val diff = (a[i].toLong() and LONG_MASK) - (b[i].toLong() and LONG_MASK) - borrow
        if (diff < 0) {
            result[i] = (diff + (1L shl 32)).toInt()
            borrow = 1L
        } else {
            result[i] = diff.toInt()
            borrow = 0L
        }
        i++
    }

    // Subtract borrow from remaining part of a
    while (i < a.size) {
        val diff = (a[i].toLong() and LONG_MASK) - borrow
        if (diff < 0) {
            result[i] = (diff + (1L shl 32)).toInt()
            borrow = 1L
        } else {
            result[i] = diff.toInt()
            borrow = 0L
        }
        i++
    }

    return stripLeadingZeros(result)
}

// Compares magnitudes (a and b must be positive)
// Returns -1 if a < b, 0 if a == b, 1 if a > b
private fun compareMagnitudes(a: IntArray, b: IntArray): Int {
    if (a.size != b.size) {
        return a.size.compareTo(b.size)
    }
    // Compare from most significant digit downwards
    for (i in a.indices.reversed()) {
        val aDigit = a[i].toLong() and LONG_MASK
        val bDigit = b[i].toLong() and LONG_MASK
        if (aDigit != bDigit) {
            return aDigit.compareTo(bDigit)
        }
    }
    return 0 // Equal
}

// Multiplies magnitude by an Int (both positive)
private fun multiplyMagnitudeByInt(mag: IntArray, factor: Int): IntArray {
    if (factor == 0) return IntArray(0)
    if (factor == 1) return mag.copyOf()

    val factorL = factor.toLong() and LONG_MASK
    val result = IntArray(mag.size + 1) // Max one extra digit
    var carry: Long = 0L
    for (i in mag.indices) {
        val product = (mag[i].toLong() and LONG_MASK) * factorL + carry
        result[i] = product.toInt()
        carry = product ushr 32
    }
    result[mag.size] = carry.toInt()

    return stripLeadingZeros(result)
}

// Multiplies magnitudes (a and b must be positive)
private fun multiplyMagnitudes(a: IntArray, b: IntArray): IntArray {
    if (a.isEmpty() || b.isEmpty()) return IntArray(0)

    // Optimization: multiply shorter by longer
    val (longer, shorter) = if (a.size >= b.size) a to b else b to a

    var totalResult = IntArray(0) // Represents ZERO initially

    for (i in shorter.indices) {
        if (shorter[i] == 0) continue // Skip zero digits

        val partialProduct = multiplyMagnitudeByInt(longer, shorter[i])
        if (partialProduct.isNotEmpty()) {
            // Shift partial product left by i digits (add i zeros at the start)
            val shiftedProduct = IntArray(partialProduct.size + i) { k ->
                if (k < i) 0 else partialProduct[k - i]
            }
            // Add to total result
            totalResult = addMagnitudes(totalResult, shiftedProduct)
        }
    }
    return totalResult // Already stripped by addMagnitudes/multiplyMagnitudeByInt
}


// Divides magnitudes (a and b must be positive, b != 0)
// Returns Pair(quotientMag, remainderMag)
// Uses simple long division. Can be slow for very large numbers.
private fun divideAndRemainderMagnitudes(a: IntArray, b: IntArray): Pair<IntArray, IntArray> {
    if (compareMagnitudes(a, b) < 0) {
        // a < b: quotient=0, remainder=a
        return Pair(IntArray(0), a.copyOf())
    }
    if (b.size == 1 && b[0] == 1) {
        // a / 1: quotient=a, remainder=0
        return Pair(a.copyOf(), IntArray(0))
    }

    val n = b.size // Divisor length
    val m = a.size // Dividend length
    val quotientMag = IntArray(m - n + 1)
    var remainderMag = a.copyOfRange(m - n, m) // Initial remainder chunk (most significant)

    // Iterate from most significant potential quotient digit down to least significant
    for (j in (m - n) downTo 0) {
        // Current dividend chunk to consider: (remainderMag concatenated with a[j])
        // If j > 0, we prepend a[j-1] later. Effectively, we process chunks.
        var currentDividendMag = if (j > 0) {
            intArrayOf(a[j - 1]) + remainderMag // Prepend next digit from original dividend
        } else {
            remainderMag // Last iteration
        }
        currentDividendMag = stripLeadingZeros(currentDividendMag) // Normalize

        var qDigit: Long = 0L
        if (compareMagnitudes(currentDividendMag, b) >= 0) {
            // Estimate quotient digit q_j.
            // Simple estimation: Binary search or repeated subtraction.
            // A better estimation (like Knuth's D) is complex but faster.
            // Using binary search for the digit (0 to 2^32 - 1):
            var low: Long = 0L
            var high: Long = (1L shl 32) // Exclusive upper bound

            while (low < high - 1) {
                val mid = low + (high - low) / 2 // Avoid overflow compared to (low+high)/2
                if (mid == 0L) { // Ensure we try at least 1 if possible
                    if (low == 0L) low = 1L
                    else break // Should not happen if currentDividend >= b
                    continue
                }

                val product = multiplyMagnitudeByInt(b, mid.toInt()) // mid won't exceed Int max here
                if (compareMagnitudes(product, currentDividendMag) <= 0) {
                    low = mid // Mid is a possible quotient digit or too small
                } else {
                    high = mid // Mid is too large
                }
            }
            qDigit = low // The largest digit whose product is <= currentDividendMag
        }

        quotientMag[j] = qDigit.toInt()

        // Subtract qDigit * b from currentDividendMag
        if (qDigit > 0) {
            val product = multiplyMagnitudeByInt(b, qDigit.toInt())
            remainderMag = subtractMagnitudes(currentDividendMag, product)
        } else {
            remainderMag = currentDividendMag // No change needed if qDigit was 0
        }
        // The 'remainderMag' now becomes the leading part for the next iteration (when a[j-1] is prepended)
    }

    return Pair(stripLeadingZeros(quotientMag), stripLeadingZeros(remainderMag))
}

private fun Int.mostSignificant1Bit(): Int {
    for (i in 31 downTo 0) {
        if (this and (1 shl i) != 0) return i
    }
    return 0
}

package debug

import korlibs.time.*
import kotlin.time.*

class PerformanceCounter(
    private val updateWindow: Duration = 1.seconds,
    private val onUpdate: ((Int) -> Unit)? = null
) {
    private var count = 0
    private var lastUpdateTime = Duration.ZERO
    private var currentRate = 0

    fun getRate(): Int = currentRate

    fun record(currentTime: Duration): Boolean {
        count++
        
        val timeDiff = currentTime - lastUpdateTime
        if (timeDiff >= updateWindow) {
            currentRate = (count / timeDiff.seconds).toInt()
            count = 0
            lastUpdateTime = currentTime
            onUpdate?.invoke(currentRate)
            return true
        }
        
        return false
    }
}

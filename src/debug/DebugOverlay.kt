package debug

import korlibs.korge.view.*
import korlibs.time.*
import world.*
import kotlin.time.*

class DebugOverlay : Container() {
    var tileType: TileType? = null
    var worldX: Int? = null
    var worldY: Int? = null
    var entityType: String? = null
    var chunkX: Int? = null
    var chunkY: Int? = null
    var localX: Int? = null
    var localY: Int? = null
    var zoomLevel: Double? = null

    private val fpsCounter = PerformanceCounter(1.seconds) { update() }
    private val upsCounter = PerformanceCounter(1.seconds) { update() }

    private val infoText = text("", 16.0).apply {
        position(10, 10)
    }

    init {
        addChild(infoText)
        visible = false
    }

    fun update() {
        if (!visible) return

        val tileInfo = if (tileType != null) "Tile: $tileType" else "Tile: N/A"
        val posInfo = if (worldX != null && worldY != null) "($worldX, $worldY)" else "(?, ?)"
        val entityInfo = "Entity: ${entityType ?: "None"}"
        val chunkInfo = if (chunkX != null && chunkY != null) "Chunk: ($chunkX, $chunkY)" else "Chunk: (?, ?)"
        val localInfo = if (localX != null && localY != null) "Local: ($localX, $localY)" else "Local: (?, ?)"
        val zoomInfo = if (zoomLevel != null) "Zoom: ${formatDouble(zoomLevel!!, 2)}" else "Zoom: ?"
        val fpsInfo = "FPS: ${fpsCounter.getRate()}"
        val upsInfo = "UPS: ${upsCounter.getRate()}"

        infoText.text = "$tileInfo $posInfo | $entityInfo\n$chunkInfo | $localInfo\n$zoomInfo\n$fpsInfo | $upsInfo"
    }

    fun toggleVisibility() {
        visible = !visible
        update()
    }

    fun countFrame() {
        fpsCounter.record(Duration.now())
    }

    fun countTick() {
        upsCounter.record(Duration.now())
    }

    private fun formatDouble(value: Double, decimalPlaces: Int): String {
        val toString = value.toString()
        val dotIndex = toString.indexOf('.')
        return if (dotIndex != -1) {
            val endIndex = minOf(dotIndex + decimalPlaces + 1, toString.length)
            toString.substring(0, endIndex)
        } else {
            toString
        }
    }
}

import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.image.color.*
import korlibs.math.geom.*
import scenes.*

suspend fun main(): Unit = Korge(
    windowSize = Size(1024, 768),
    backgroundColor = Colors["#2b2b2b"],
    title = "Factory Game",
    clipBorders = false,
    scaleMode = ScaleMode.SHOW_ALL,
) {
    val sceneContainer = sceneContainer()
    sceneContainer.changeTo { GameScene() }
}

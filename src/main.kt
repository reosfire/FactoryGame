import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.image.color.*
import korlibs.math.geom.*
import scenes.*

suspend fun main(): Unit = Korge(
    backgroundColor = Colors["#2b2b2b"],
    title = "Factory Game",
    clipBorders = false,
    displayMode = KorgeDisplayMode.NO_SCALE,
    scaleMode = ScaleMode.NO_SCALE,
) {
    val sceneContainer = sceneContainer()
    sceneContainer.changeTo { GameScene() }
}

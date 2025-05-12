import korlibs.image.color.*
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import scenes.*

suspend fun main(): Unit = Korge(
    backgroundColor = Colors["#2b2b2b"],
    title = "Factory Game",
    displayMode = KorgeDisplayMode.NO_SCALE,
) {
    val sceneContainer = sceneContainer()
    sceneContainer.changeTo { GameScene() }

    onStageResized { width, height ->
        sceneContainer.size(width, height)
    }
}

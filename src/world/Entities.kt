package world

import korlibs.math.geom.*
import scenes.*

enum class EntityType {
    Miner,
    ConveyorBelt,
    Storage
}

sealed class Entity {

    abstract fun tick(globalState: GlobalState)

    data class Miner(val position: Point, var animationFrame: Int) : Entity() {
        override fun tick(globalState: GlobalState) {
            animationFrame = (animationFrame + 1) % 7
            if (animationFrame == 0) {
                globalState.money += 1
            }
        }
    }

    data class ConveyorBelt(val position: Point) : Entity() {
        override fun tick(globalState: GlobalState) = Unit
    }

    data class Storage(val position: Point) : Entity() {
        override fun tick(globalState: GlobalState) = Unit
    }
}

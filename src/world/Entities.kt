package world

import korlibs.math.geom.*

enum class EntityType {
    Miner,
    ConveyorBelt,
    Storage
}

sealed class Entity {

    abstract fun tick()

    data class Miner(val position: Point, var animationFrame: Int) : Entity() {
        override fun tick() {
            animationFrame = (animationFrame + 1) % 7
        }
    }
    data class ConveyorBelt(val position: Point) : Entity() {
        override fun tick() = Unit
    }
    data class Storage(val position: Point) : Entity() {
        override fun tick() = Unit
    }
}

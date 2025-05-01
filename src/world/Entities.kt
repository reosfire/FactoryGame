package world

import korlibs.math.geom.*

enum class EntityType {
    Miner,
    ConveyorBelt,
    Storage
}

sealed class Entity {
    data class Miner(val position: Point, var animationFrame: Int) : Entity()
    data class ConveyorBelt(val position: Point) : Entity()
    data class Storage(val position: Point) : Entity()
}

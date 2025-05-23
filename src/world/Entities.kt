package world

import korlibs.korge.render.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import korlibs.math.geom.slice.*
import scenes.*
import utils.*

enum class EntityType {
    Miner,
    ConveyorBelt,
    Storage
}

sealed class Entity {

    abstract fun tick(globalState: GlobalState)

    abstract fun render(
        ctx: RenderContext,
        position: Point,
        tileSize: Double,
        textures: TexturesStore
    )

    data class Miner(val position: Point, var animationFrame: Int) : Entity() {
        override fun tick(globalState: GlobalState) {
            animationFrame = (animationFrame + 1) % 7
            if (animationFrame == 0) {
                globalState.money.amount += BigInt.ONE
            }
        }

        override fun render(
            ctx: RenderContext,
            position: Point,
            tileSize: Double,
            textures: TexturesStore
        ) {
            val minerTextureCoords = TextureCoords(
                ctx.getTex(textures.miner),
                RectCoords(
                    animationFrame / 7f, 0f,
                    (animationFrame + 1) / 7f, 0f,
                    (animationFrame + 1) / 7f, 1f,
                    animationFrame / 7f, 1f
                )
            )

            ctx.useBatcher { batcher ->
                batcher.drawQuad(
                    minerTextureCoords,
                    position.x.toFloat(),
                    position.y.toFloat(),
                    tileSize.toFloat(),
                    tileSize.toFloat(),
                    blendMode = BlendMode.NORMAL,
                    filtering = false
                )
            }
        }
    }

    data class ConveyorBelt(val position: Point) : Entity() {
        override fun tick(globalState: GlobalState) = Unit

        override fun render(
            ctx: RenderContext,
            position: Point,
            tileSize: Double,
            textures: TexturesStore
        ) {
            // ConveyorBelt rendering logic (if needed)
        }
    }

    data class Storage(val position: Point) : Entity() {
        override fun tick(globalState: GlobalState) = Unit

        override fun render(
            ctx: RenderContext,
            position: Point,
            tileSize: Double,
            textures: TexturesStore
        ) {
            // Storage rendering logic (if needed)
        }
    }
}

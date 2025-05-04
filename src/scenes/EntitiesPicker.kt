package scenes

import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.korge.render.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import korlibs.math.geom.slice.*
import world.*

class EntitiesPicker(private val textures: TexturesStore) : View() {
    var selectedEntity: EntityType? = null
    private val buttonSize = 48.0
    private val padding = 8.0f

    override fun renderInternal(ctx: RenderContext) {
        val minerIconTexture = ctx.getTex(textures.miner)

        val minerIconTextureCoords = SliceCoordsWithBase(
            minerIconTexture,
            RectCoords(
                0f, 0f,
                1 / 7f, 0f,
                1 / 7f, 1f,
                0f, 1f
            )
        )

        ctx.useBatcher { batch ->
            batch.drawQuad(
                tex = ctx.getTex(Bitmaps.white),
                x = pos.x.toFloat(),
                y = pos.y.toFloat(),
                width = (buttonSize + padding * 2).toFloat(),
                height = (buttonSize + padding * 2).toFloat(),
                colorMul = if (selectedEntity == EntityType.Miner) Colors["#CCCCCC"] else Colors["#888888"],
            )
            batch.drawQuad(
                tex = minerIconTextureCoords,
                x = pos.x.toFloat() + padding,
                y = pos.y.toFloat() + padding,
                width = buttonSize.toFloat(),
                height = buttonSize.toFloat(),
                filtering = false,
            )
        }
    }

    override fun getLocalBoundsInternal(): Rectangle {
        return Rectangle(0f, 0f, buttonSize + padding * 2, buttonSize + padding * 2)
    }
}

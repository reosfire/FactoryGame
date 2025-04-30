package scenes

import KR
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.korge.input.*
import korlibs.korge.render.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import korlibs.math.geom.slice.*
import world.*
import kotlin.math.*
import kotlin.random.*

enum class TileType {
    GRASS,
    WATER,
    COAL_ORE
}

sealed class Tile(val type: TileType) {
    data object Grass : Tile(TileType.GRASS)
    data object Water : Tile(TileType.WATER)
    data object CoalOre : Tile(TileType.COAL_ORE)

    companion object {
        fun random(): Tile {
            return when ((0..2).random()) {
                0 -> Grass
                1 -> Water
                else -> CoalOre
            }
        }
    }
}

enum class EntityType {
    Miner,
    ConveyorBelt,
    Storage
}

sealed class Entity {
    data class Miner(val position: Point) : Entity()
    data class ConveyorBelt(val position: Point) : Entity()
    data class Storage(val position: Point) : Entity()
}

class TexturesStore(
    val grass: Bitmap,
    val water: Bitmap,
    val coal: Bitmap
)

class Chunk(
    val tiles: Array<Array<Tile>>,
    val entities: Array<Array<Entity?>>
) {
    fun render(ctx: RenderContext, tileSize: Double, xOffset: Double, yOffset: Double, textures: TexturesStore) {
        ctx.useBatcher { batcher ->
            for (x in 0..<16) {
                for (y in 0..<16) {
                    val tile = tiles[x][y]
                    val bitmap = when (tile.type) {
                        TileType.GRASS -> textures.grass
                        TileType.WATER -> textures.water
                        TileType.COAL_ORE -> textures.coal
                    }

                    val tex = ctx.getTex(bitmap)

                    val textureCoords = TextureCoords(
                        tex,
                        RectCoords(
                            0f, 0f,
                            1f, 0f,
                            1f, 1f,
                            0f, 1f
                        )
                    )

                    batcher.drawQuad(
                        textureCoords,
                        ((x + xOffset) * tileSize).toFloat(),
                        ((y + yOffset) * tileSize).toFloat(),
                        tileSize.toFloat(),
                        tileSize.toFloat(),
                        blendMode = BlendMode.NONE,
                        filtering = false
                    )
                }
            }
        }
    }
}

interface WorldGenerator {
    fun generateChunk(x: Int, y: Int): Chunk
}

class RandomWorldGenerator : WorldGenerator {
    override fun generateChunk(x: Int, y: Int): Chunk {
        val tiles = Array(16) { Array(16) { Tile.random() } }
        val entities = Array(16) { Array<Entity?>(16) { null } }

        return Chunk(tiles, entities)
    }
}

class World(
    val generator: WorldGenerator,
) {
    val loadedChunks = mutableMapOf<Pair<Int, Int>, Chunk>()

    fun getChunk(x: Int, y: Int): Chunk {
        return loadedChunks.getOrPut(Pair(x, y)) { generator.generateChunk(x, y) }
    }
}

class WorldRenderer(
    val world: World,
    val textures: TexturesStore,
): View() {
    var tileDisplaySize: Double = 32.0
    var currentOffset: Point = Point(0, 0)

    var screenSize = Size(100, 100)

    override fun renderInternal(ctx: RenderContext) {
        val chunkStartX = (currentOffset.x.toInt() shr 4) - 2
        val chunkStartY = (currentOffset.y.toInt() shr 4) - 2
        val chunksCountX = (screenSize.width / tileDisplaySize / 16).toInt() + 4
        val chunksCountY = (screenSize.height / tileDisplaySize / 16).toInt() + 4

        for (x in chunkStartX..<chunkStartX + chunksCountX) {
            for (y in chunkStartY..<chunkStartY + chunksCountY) {
                world.getChunk(x, y).render(
                    ctx,
                    tileDisplaySize,
                    x * 16 - currentOffset.x,
                    y * 16 - currentOffset.y,
                    textures
                )
            }
        }
    }

    fun getTileAt(point: Point): Tile {
        val x = point.x.toInt()
        val y = point.y.toInt()

        val chunkX = x shr 4
        val chunkY = y shr 4

        val chunk = world.getChunk(chunkX, chunkY)
        val tileX = x and 0xF
        val tileY = y and 0xF

        return chunk.tiles[tileX][tileY]
    }

    fun getEntityAt(point: Point): Entity? {
        val x = point.x.toInt()
        val y = point.y.toInt()

        val chunkX = x shr 4
        val chunkY = y shr 4

        val chunk = world.getChunk(chunkX, chunkY)
        val tileX = x and 0xF
        val tileY = y and 0xF

        return chunk.entities[tileX][tileY]
    }

    fun screenPositionToWorldPosition(screenPos: Point): Point {
        val worldX = (screenPos.x) / tileDisplaySize + currentOffset.x
        val worldY = (screenPos.y) / tileDisplaySize + currentOffset.y
        return Point(worldX, worldY)
    }
}

class GameScene : Scene() {
    // Game world components
    private lateinit var world: World
    private lateinit var worldRenderer: WorldRenderer
    private lateinit var entitiesPicker: EntitiesPicker

    override suspend fun SContainer.sceneMain() {
        world = World(NormalWorldGenerator())

        val textures = TexturesStore(
            grass = KR.tiles.atlas.grass.read(),
            water = KR.tiles.atlas.water.read(),
            coal = KR.tiles.atlas.coal.read()
        )
        worldRenderer = WorldRenderer(world, textures)
        addChild(worldRenderer)
        onStageResized { width, height ->
            worldRenderer.screenSize = Size(width, height)
        }

        entitiesPicker = EntitiesPicker()
        addChild(entitiesPicker)

        // Camera controls
        setupCameraControls()

        // Tile information on hover
        setupTileHoverInfo()
    }


    private fun SContainer.setupCameraControls() {
        // Mouse events for camera controls
        mouse {
            onMouseDrag {
                worldRenderer.currentOffset = Point(
                    worldRenderer.currentOffset.x + it.deltaDx / worldRenderer.tileDisplaySize,
                    worldRenderer.currentOffset.y + it.deltaDy / worldRenderer.tileDisplaySize,
                )
            }

            onScroll {
                val zoomFactor = if (it.scrollDeltaYPixels < 0) 1.1 else 0.9
                worldRenderer.tileDisplaySize *= zoomFactor
            }
        }
    }

    private fun SContainer.setupTileHoverInfo() {
        // Create a text to display tile info
        val tileInfoText = text("", 16.0).apply {
            position(10, 10)
        }

        // Update tile info on mouse move
        mouse {
            onMove {
                val worldScreenPos = worldRenderer.screenPositionToWorldPosition(it.currentPosLocal)
                val tile = worldRenderer.getTileAt(worldScreenPos)

                tileInfoText.text = "Tile: ${tile.type} (${worldScreenPos.x}, ${worldScreenPos.y})"
            }
        }
    }
}

class EntitiesPicker: View() {
    override fun renderInternal(ctx: RenderContext) {

    }
}

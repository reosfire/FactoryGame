package scenes

import KR
import debug.*
import korlibs.event.*
import korlibs.graphics.*
import korlibs.graphics.shader.*
import korlibs.graphics.shader.gl.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.korge.input.*
import korlibs.korge.internal.*
import korlibs.korge.render.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.math.*
import korlibs.math.geom.*
import korlibs.math.geom.slice.*
import korlibs.time.*
import world.*

class TexturesStore(
    val grass: Bitmap,
    val water: Bitmap,
    val coal: Bitmap,
    val miner: Bitmap,
)

class Chunk(
    val tiles: Array<Array<Tile>>,
    val entities: Array<Array<Entity?>>
) {
    private val rectCoords = RectCoords(
        0f, 0f,
        1f, 0f,
        1f, 1f,
        0f, 1f
    )

    fun render(ctx: RenderContext, tileSize: Double, xOffset: Double, yOffset: Double, textures: TexturesStore) {
        val minerTexture = ctx.getTex(textures.miner)

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
                        rectCoords
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

                    // Render entities
                    val entity = entities[x][y] ?: continue
                    if (entity is Entity.Miner) {
                        val minerTextureCoords = TextureCoords(
                            minerTexture,
                            RectCoords(
                                entity.animationFrame / 7f, 0f,
                                (entity.animationFrame + 1) / 7f, 0f,
                                (entity.animationFrame + 1) / 7f, 1f,
                                entity.animationFrame / 7f, 1f
                            )
                        )

                        batcher.drawQuad(
                            minerTextureCoords,
                            ((x + xOffset) * tileSize).toFloat(),
                            ((y + yOffset) * tileSize).toFloat(),
                            tileSize.toFloat(),
                            tileSize.toFloat(),
                            blendMode = BlendMode.NORMAL,
                            filtering = false
                        )
                    }
                }
            }
        }
    }
}

class World(
    val generator: WorldGenerator,
) {
    val loadedChunks = mutableMapOf<Pair<Int, Int>, Chunk>()

    fun getChunk(x: Int, y: Int): Chunk {
        return loadedChunks.getOrPut(Pair(x, y)) { generator.generateChunk(x, y) }
    }

    fun tick() {
        for (chunk in loadedChunks.values) {
            for (x in 0..<16) {
                for (y in 0..<16) {
                    val entity = chunk.entities[x][y]
                    if (entity is Entity.Miner) {
                        // Update miner animation frame
                        entity.animationFrame = (entity.animationFrame + 1) % 7
                    }
                }
            }
        }
    }
}

class WorldRenderer(
    val world: World,
    val textures: TexturesStore,
) : View() {
    var tileDisplaySize: Double = 32.0
    var currentOffset: Point = Point(0, 0)

    var minZoom: Double = 8.0
    var maxZoom: Double = 64.0

    var screenSize = Size(100, 100)

    var hoveredTilePosition: Point? = null

    private object SelectorUniformBlock : UniformBlock(fixedLocation = 6) {
        val frame by float()
        val color by vec4()
        val width by float()
    }

    private var currentFrame = 0f

    @OptIn(KorgeInternal::class)
    private val program = BatchBuilder2D.PROGRAM
        .replacingFragment("color") {
            DefaultShaders {
                IF (v_Tex.x.ge(1f - SelectorUniformBlock.width)
                    .or(v_Tex.x.le(SelectorUniformBlock.width))
                    .or(v_Tex.y.ge(1f - SelectorUniformBlock.width))
                    .or(v_Tex.y.le(SelectorUniformBlock.width))
                ) {
                    SET(out, SelectorUniformBlock.color)
                }.ELSE {
                    SET(out, vec4(0f.lit))
                }
            }
        }

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

        hoveredTilePosition?.let { pos ->
            val screenX = ((pos.x - currentOffset.x) * tileDisplaySize).toFloat()
            val screenY = ((pos.y - currentOffset.y) * tileDisplaySize).toFloat()

            val hasEntity = getEntityAt(pos) != null

            val highlightColor = if (hasEntity) Colors.YELLOW else Colors["#00FF00"]
            val lineWidth = if (hasEntity) 0.04f else 0.03f

            ctx[SelectorUniformBlock].push {
                it[frame] = currentFrame++
                it[color] = highlightColor
                it[width] = lineWidth
            }

            ctx.useBatcher { batch ->
                batch.drawQuad(
                    tex = ctx.getTex(Bitmaps.white),
                    x = screenX,
                    y = screenY,
                    width = tileDisplaySize.toFloat(),
                    height = tileDisplaySize.toFloat(),
                    program = program,
                )
            }
        }
    }

    override fun getLocalBoundsInternal(): Rectangle {
        return Rectangle(
            0.0,
            0.0,
            screenSize.width,
            screenSize.height,
        )
    }

    fun getTileAt(point: Point): Tile {
        val x = point.x.toIntFloor()
        val y = point.y.toIntFloor()

        val chunkX = x shr 4
        val chunkY = y shr 4

        val chunk = world.getChunk(chunkX, chunkY)
        val tileX = x and 0xF
        val tileY = y and 0xF

        return chunk.tiles[tileX][tileY]
    }

    fun getEntityAt(point: Point): Entity? {
        val x = point.x.toIntFloor()
        val y = point.y.toIntFloor()

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
    private lateinit var world: World
    private lateinit var worldRenderer: WorldRenderer
    private lateinit var entitiesPicker: EntitiesPicker
    
    // Add debug overlay component
    private lateinit var debugOverlay: DebugOverlay
    private var debugMode = false

    override suspend fun SContainer.sceneMain() {
        world = World(NormalWorldGenerator())

        val textures = TexturesStore(
            grass = KR.tiles.atlas.grass.read(),
            water = KR.tiles.atlas.water.read(),
            coal = KR.tiles.atlas.coal.read(),
            miner = KR.extractor.read(),
        )
        worldRenderer = WorldRenderer(world, textures)
        addChild(worldRenderer)
        onStageResized { width, height ->
            worldRenderer.screenSize = Size(width, height)
        }

        entitiesPicker = EntitiesPicker(textures)
        addChild(entitiesPicker)

        debugOverlay = DebugOverlay()
        addChild(debugOverlay)

        addFixedUpdater((1 / 20f).seconds) {
            world.tick()
        }

        setupCameraControls()
        setupTileHoverInfo()
        setupEntityPlacement()
        setupDebugMode()
    }
    
    private fun SContainer.setupDebugMode() {
        keys {
            down(Key.F3) {
                debugMode = !debugMode
                debugOverlay.toggleVisibility()
            }
        }
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

                val mouseWorldPosBefore = worldRenderer.screenPositionToWorldPosition(it.currentPosLocal)

                val newZoom = worldRenderer.tileDisplaySize * zoomFactor
                worldRenderer.tileDisplaySize = newZoom.coerceIn(worldRenderer.minZoom, worldRenderer.maxZoom)

                val mouseWorldPosAfter = worldRenderer.screenPositionToWorldPosition(it.currentPosLocal)

                worldRenderer.currentOffset -= Point(
                    mouseWorldPosAfter.x - mouseWorldPosBefore.x,
                    mouseWorldPosAfter.y - mouseWorldPosBefore.y,
                )

                debugOverlay.zoomLevel = worldRenderer.tileDisplaySize
                debugOverlay.update()
            }
        }
    }

    private fun SContainer.setupTileHoverInfo() {
        mouse {
            onMove {
                val worldScreenPos = worldRenderer.screenPositionToWorldPosition(it.currentPosLocal)
                val tile = worldRenderer.getTileAt(worldScreenPos)
                val entity = worldRenderer.getEntityAt(worldScreenPos)
                
                worldRenderer.hoveredTilePosition = Point(
                    worldScreenPos.x.toIntFloor(),
                    worldScreenPos.y.toIntFloor()
                )

                val worldX = worldScreenPos.x.toIntFloor()
                val worldY = worldScreenPos.y.toIntFloor()
                val chunkX = worldX shr 4
                val chunkY = worldY shr 4
                val localX = worldX and 0xF
                val localY = worldY and 0xF

                val entityTypeStr = entity?.let {
                    when (entity) {
                        is Entity.Miner -> "Miner"
                        is Entity.ConveyorBelt -> "Conveyor Belt"
                        is Entity.Storage -> "Storage"
                    }
                }
                
                // Update debug overlay fields
                debugOverlay.tileType = tile.type
                debugOverlay.worldX = worldX
                debugOverlay.worldY = worldY
                debugOverlay.entityType = entityTypeStr
                debugOverlay.chunkX = chunkX
                debugOverlay.chunkY = chunkY
                debugOverlay.localX = localX
                debugOverlay.localY = localY
                debugOverlay.zoomLevel = worldRenderer.tileDisplaySize

                debugOverlay.update()
            }

            onExit {
                worldRenderer.hoveredTilePosition = null
            }
        }
    }

    private fun SContainer.setupEntityPlacement() {
        // Click on the palette to select entity type
        entitiesPicker.mouse {
            onClick { 
                entitiesPicker.selectedEntity = EntityType.Miner
            }
        }
        
        // Place entity on the world when clicked
        worldRenderer.mouse {
            onClick {
                if (entitiesPicker.selectedEntity != null) {
                    val worldPos = worldRenderer.screenPositionToWorldPosition(it.currentPosLocal)
                    val x = worldPos.x.toIntFloor()
                    val y = worldPos.y.toIntFloor()
                    
                    val chunkX = x shr 4
                    val chunkY = y shr 4
                    val localX = x and 0xF
                    val localY = y and 0xF
                    
                    val chunk = world.getChunk(chunkX, chunkY)
                    
                    // Check if tile is valid for placement (not water)
                    val tile = chunk.tiles[localX][localY]
                    if (tile.type == TileType.COAL_ORE && chunk.entities[localX][localY] == null) {
                        when (entitiesPicker.selectedEntity) {
                            EntityType.Miner -> {
                                chunk.entities[localX][localY] = Entity.Miner(Point(x, y), 0)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

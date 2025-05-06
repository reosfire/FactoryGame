package scenes

import KR
import debug.*
import korlibs.event.*
import korlibs.graphics.*
import korlibs.graphics.shader.*
import korlibs.image.atlas.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.io.file.std.*
import korlibs.korge.input.*
import korlibs.korge.internal.*
import korlibs.korge.render.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.korge.view.align.*
import korlibs.math.*
import korlibs.math.geom.*
import korlibs.math.geom.slice.*
import korlibs.time.*
import utils.*
import world.*

class TexturesStore(
    val grass: BmpSlice,
    val water: BmpSlice,
    val coal: BmpSlice,
    val miner: Bitmap,
)

private val FULL_RECT_COORDINATES = RectCoords(
    0f, 0f,
    1f, 0f,
    1f, 1f,
    0f, 1f
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

                    batcher.drawQuad(
                        ctx.getTex(bitmap),
                        ((x + xOffset) * tileSize).toFloat(),
                        ((y + yOffset) * tileSize).toFloat(),
                        tileSize.toFloat(),
                        tileSize.toFloat(),
                        blendMode = BlendMode.NONE,
                        filtering = false
                    )

                    entities[x][y]?.render(
                        ctx,
                        Point((x + xOffset) * tileSize, (y + yOffset) * tileSize),
                        tileSize,
                        textures
                    )
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

    fun tick(globalState: GlobalState) {
        for (chunk in loadedChunks.values) {
            for (x in 0..<16) {
                for (y in 0..<16) {
                    chunk.entities[x][y]?.tick(globalState)
                }
            }
        }
    }
}

fun Program.Builder.TEMP(type: VarType, initialValue: Operand): Temp {
    val temp = TEMP(type)
    SET(temp, initialValue)
    return temp
}

fun Program.Builder.TEMP(initialValue: Operand): Temp {
    val temp = TEMP(initialValue.type)
    SET(temp, initialValue)
    return temp
}

class WorldRenderer(
    val world: World,
    val textures: TexturesStore,
    private val debugOverlay: DebugOverlay
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
    private val program = BatchBuilder2D.PROGRAM.replacingFragment("color") {
        DefaultShaders {
            val shimmerScale = 10f.lit
            val shimmerSpeed = 0.05f.lit
            val shimmerIntensity = 0.3f.lit
            val shimmerBase = 0.7f.lit

            val borderWidth = SelectorUniformBlock.width
            val baseColor = SelectorUniformBlock.color
            val frame = SelectorUniformBlock.frame

            val distToEdgeX = min(abs(borderWidth - v_Tex.x), abs(1f.lit - borderWidth - v_Tex.x))
            val distToEdgeY = min(abs(borderWidth - v_Tex.y), abs(1f.lit - borderWidth - v_Tex.y))
            val minDistToEdge = TEMP(min(distToEdgeX, distToEdgeY))

            IF (minDistToEdge.le(borderWidth)) {
                val shimmerPhase = frame * shimmerSpeed

                val shimmerEffect = sin((v_Tex.x + v_Tex.y) * shimmerScale + shimmerPhase) * shimmerIntensity + shimmerBase

                val alpha = TEMP((1f.lit - smoothstep(0.5f.lit, 1.0f.lit, minDistToEdge / borderWidth)) * shimmerEffect)

                SET(out,
                    vec4(
                        baseColor.x * alpha,
                        baseColor.y * alpha,
                        baseColor.z * alpha,
                        alpha
                    )
                )
            }.ELSE {
                SET(out, vec4(0f.lit))
            }
        }
    }


    override fun renderInternal(ctx: RenderContext) {
        debugOverlay.countFrame()

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

        ctx[SelectorUniformBlock].push {
            it[frame] = currentFrame++
        }

        hoveredTilePosition?.let { pos ->
            val screenX = ((pos.x - currentOffset.x) * tileDisplaySize).toFloat()
            val screenY = ((pos.y - currentOffset.y) * tileDisplaySize).toFloat()

            val hasEntity = getEntityAt(pos) != null

            val highlightColor = if (hasEntity) Colors.YELLOW else Colors["#00FF00"]
            val lineWidth = if (hasEntity) 0.04f else 0.03f

            ctx[SelectorUniformBlock].push {
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
                    blendMode = BlendMode.NORMAL,
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

const val log2_10 = 3.32192809489f

class Money(
    var amount: BigInt,
) {
    fun toScientificLikeString(): String {
        val exponent = (amount.mostSignificant1Bit() / log2_10).toIntCeil() - 3
        if (exponent < 0) return amount.toString()

        val mantissa = amount / 10.toBigInt().pow(exponent)

        val mantissaStr = mantissa.toString()
        val exponentStr = if (exponent > 0) "e$exponent" else ""
        return "$mantissaStr$exponentStr"
    }
}

class GlobalState(
    var money: Money = Money(100.toBigInt()),
)

class GameScene : Scene() {
    private lateinit var world: World
    private lateinit var worldRenderer: WorldRenderer
    private lateinit var entitiesPicker: EntitiesPicker
    private lateinit var moneyText: Text

    // Add debug overlay component
    private lateinit var debugOverlay: DebugOverlay
    private var debugMode = false

    private val globalState = GlobalState()

    override suspend fun SContainer.sceneMain() {
        world = World(NormalWorldGenerator())

        val atlas = resourcesVfs["tiles.atlas.json"].readAtlas()

        val textures = TexturesStore(
            grass = atlas["grass.png"],
            water = atlas["water.png"],
            coal = atlas["coal.png"],
            miner = KR.entities.extractor.read(),
        )
        debugOverlay = DebugOverlay()
        worldRenderer = WorldRenderer(world, textures, debugOverlay)
        worldRenderer.screenSize = size
        addChild(worldRenderer)

        entitiesPicker = EntitiesPicker(textures).apply {
            alignLeftToLeftOf(this@sceneMain, 16.0)
            alignBottomToBottomOf(this@sceneMain, 16.0)
        }
        addChild(entitiesPicker)

        addChild(debugOverlay)

        moneyText = text("Money: ${globalState.money.toScientificLikeString()}", 24.0) {
            alignRightToRightOf(this@sceneMain, 16.0)
            color = Colors.WHITE
        }

        var prevMoneyLength = -1
        addFixedUpdater((1 / 30f).seconds) {
            world.tick(globalState)
            val currentMoneyText = "Money: ${globalState.money.toScientificLikeString()}"
            moneyText.text = currentMoneyText
            if (currentMoneyText.length != prevMoneyLength) {
                moneyText.alignRightToRightOf(this@sceneMain, 16.0)
                prevMoneyLength = currentMoneyText.length
            }

            debugOverlay.countTick()
        }

        onStageResized { width, height ->
            worldRenderer.screenSize = Size(width, height)

            moneyText.xy(width - moneyText.width - 16.0, 16.0)
            entitiesPicker.xy(16.0, height - entitiesPicker.height - 16.0)
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

                    val tile = chunk.tiles[localX][localY]
                    if (tile.type == TileType.COAL_ORE && chunk.entities[localX][localY] == null) {
                        when (entitiesPicker.selectedEntity) {
                            EntityType.Miner -> {
                                if (globalState.money.amount >= BigInt.TEN) {
                                    globalState.money.amount -= BigInt.TEN
                                    chunk.entities[localX][localY] = Entity.Miner(Point(x, y), 0)
                                } else {
                                    println("Not enough money to place a miner!")
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

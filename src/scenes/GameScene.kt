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
    val grassLineNorth: BmpSlice,
    val grassLineEast: BmpSlice,
    val grassLineSouth: BmpSlice,
    val grassLineWest: BmpSlice,
    val grassCornerNE: BmpSlice,
    val grassCornerSE: BmpSlice,
    val grassCornerSW: BmpSlice,
    val grassCornerNW: BmpSlice,
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
    val entities: Array<Array<Entity?>>,
    var world: World? = null
) {
    fun render(ctx: RenderContext, tileSize: Double, xOffset: Double, yOffset: Double, textures: TexturesStore) {
        ctx.useBatcher { batcher ->
            // First pass: Draw base tiles
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
                }
            }
            
            // Second pass: Draw transition tiles (grass edges and corners)
            for (x in 0..<16) {
                for (y in 0..<16) {
                    val tile = tiles[x][y]
                    
                    // Skip if this tile is already grass
                    if (tile.type != TileType.WATER) continue
                    
                    val tileX = ((x + xOffset) * tileSize).toFloat()
                    val tileY = ((y + yOffset) * tileSize).toFloat()
                    val tileWidth = tileSize.toFloat()
                    
                    // Get chunk coordinates for this chunk
                    val currentChunkCoords = world?.loadedChunks?.entries?.find { it.value === this }?.key
                    val chunkX = currentChunkCoords?.first ?: 0
                    val chunkY = currentChunkCoords?.second ?: 0
                    
                    // Check adjacent tiles (N, E, S, W) with cross-chunk support
                    val hasGrassNorth = when {
                        y > 0 -> tiles[x][y - 1].type == TileType.GRASS
                        else -> {
                            // Check tile in the chunk above
                            world?.getChunk(chunkX, chunkY - 1)?.tiles?.get(x)?.get(15)?.type == TileType.GRASS
                        }
                    }
                    
                    val hasGrassEast = when {
                        x < 15 -> tiles[x + 1][y].type == TileType.GRASS
                        else -> {
                            // Check tile in the chunk to the right
                            world?.getChunk(chunkX + 1, chunkY)?.tiles?.get(0)?.get(y)?.type == TileType.GRASS
                        }
                    }
                    
                    val hasGrassSouth = when {
                        y < 15 -> tiles[x][y + 1].type == TileType.GRASS
                        else -> {
                            // Check tile in the chunk below
                            world?.getChunk(chunkX, chunkY + 1)?.tiles?.get(x)?.get(0)?.type == TileType.GRASS
                        }
                    }
                    
                    val hasGrassWest = when {
                        x > 0 -> tiles[x - 1][y].type == TileType.GRASS
                        else -> {
                            // Check tile in the chunk to the left
                            world?.getChunk(chunkX - 1, chunkY)?.tiles?.get(15)?.get(y)?.type == TileType.GRASS
                        }
                    }
                    
                    // Check diagonal tiles with cross-chunk support
                    val hasGrassNorthEast = when {
                        y > 0 && x < 15 -> tiles[x + 1][y - 1].type == TileType.GRASS
                        y > 0 && x == 15 -> world?.getChunk(chunkX + 1, chunkY)?.tiles?.get(0)?.get(y - 1)?.type == TileType.GRASS
                        y == 0 && x < 15 -> world?.getChunk(chunkX, chunkY - 1)?.tiles?.get(x + 1)?.get(15)?.type == TileType.GRASS
                        else -> world?.getChunk(chunkX + 1, chunkY - 1)?.tiles?.get(0)?.get(15)?.type == TileType.GRASS
                    }
                    
                    val hasGrassSouthEast = when {
                        y < 15 && x < 15 -> tiles[x + 1][y + 1].type == TileType.GRASS
                        y < 15 && x == 15 -> world?.getChunk(chunkX + 1, chunkY)?.tiles?.get(0)?.get(y + 1)?.type == TileType.GRASS
                        y == 15 && x < 15 -> world?.getChunk(chunkX, chunkY + 1)?.tiles?.get(x + 1)?.get(0)?.type == TileType.GRASS
                        else -> world?.getChunk(chunkX + 1, chunkY + 1)?.tiles?.get(0)?.get(0)?.type == TileType.GRASS
                    }
                    
                    val hasGrassSouthWest = when {
                        y < 15 && x > 0 -> tiles[x - 1][y + 1].type == TileType.GRASS
                        y < 15 && x == 0 -> world?.getChunk(chunkX - 1, chunkY)?.tiles?.get(15)?.get(y + 1)?.type == TileType.GRASS
                        y == 15 && x > 0 -> world?.getChunk(chunkX, chunkY + 1)?.tiles?.get(x - 1)?.get(0)?.type == TileType.GRASS
                        else -> world?.getChunk(chunkX - 1, chunkY + 1)?.tiles?.get(15)?.get(0)?.type == TileType.GRASS
                    }
                    
                    val hasGrassNorthWest = when {
                        y > 0 && x > 0 -> tiles[x - 1][y - 1].type == TileType.GRASS
                        y > 0 && x == 0 -> world?.getChunk(chunkX - 1, chunkY)?.tiles?.get(15)?.get(y - 1)?.type == TileType.GRASS
                        y == 0 && x > 0 -> world?.getChunk(chunkX, chunkY - 1)?.tiles?.get(x - 1)?.get(15)?.type == TileType.GRASS
                        else -> world?.getChunk(chunkX - 1, chunkY - 1)?.tiles?.get(15)?.get(15)?.type == TileType.GRASS
                    }
                    
                    // Draw line transitions with pre-rotated textures
                    if (hasGrassNorth) {
                        batcher.drawQuad(
                            ctx.getTex(textures.grassLineNorth),
                            tileX,
                            tileY,
                            tileWidth,
                            tileWidth,
                            blendMode = BlendMode.NORMAL,
                            filtering = false
                        )
                    }
                    if (hasGrassEast) {
                        batcher.drawQuad(
                            ctx.getTex(textures.grassLineEast),
                            tileX,
                            tileY,
                            tileWidth,
                            tileWidth,
                            blendMode = BlendMode.NORMAL,
                            filtering = false
                        )
                    }
                    if (hasGrassSouth) {
                        batcher.drawQuad(
                            ctx.getTex(textures.grassLineSouth),
                            tileX,
                            tileY,
                            tileWidth,
                            tileWidth,
                            blendMode = BlendMode.NORMAL,
                            filtering = false
                        )
                    }
                    if (hasGrassWest) {
                        batcher.drawQuad(
                            ctx.getTex(textures.grassLineWest),
                            tileX,
                            tileY,
                            tileWidth,
                            tileWidth,
                            blendMode = BlendMode.NORMAL,
                            filtering = false
                        )
                    }
                    
                    // Draw corner transitions with pre-rotated textures
                    if (!hasGrassNorth && !hasGrassEast && hasGrassNorthEast) {
                        batcher.drawQuad(
                            ctx.getTex(textures.grassCornerNE),
                            tileX,
                            tileY,
                            tileWidth,
                            tileWidth,
                            blendMode = BlendMode.NORMAL,
                            filtering = false
                        )
                    }
                    if (!hasGrassEast && !hasGrassSouth && hasGrassSouthEast) {
                        batcher.drawQuad(
                            ctx.getTex(textures.grassCornerSE),
                            tileX,
                            tileY,
                            tileWidth,
                            tileWidth,
                            blendMode = BlendMode.NORMAL,
                            filtering = false
                        )
                    }
                    if (!hasGrassSouth && !hasGrassWest && hasGrassSouthWest) {
                        batcher.drawQuad(
                            ctx.getTex(textures.grassCornerSW),
                            tileX,
                            tileY,
                            tileWidth,
                            tileWidth,
                            blendMode = BlendMode.NORMAL,
                            filtering = false
                        )
                    }
                    if (!hasGrassWest && !hasGrassNorth && hasGrassNorthWest) {
                        batcher.drawQuad(
                            ctx.getTex(textures.grassCornerNW),
                            tileX,
                            tileY,
                            tileWidth,
                            tileWidth,
                            blendMode = BlendMode.NORMAL,
                            filtering = false
                        )
                    }
                }
            }

            // Third pass: Draw entities
            for (x in 0..<16) {
                for (y in 0..<16) {
                    // Finally render entities on top
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
        return loadedChunks.getOrPut(Pair(x, y)) { 
            generator.generateChunk(x, y).also { chunk -> 
                chunk.world = this 
            }
        }
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

            // clamping returns the nearest point on the frame we are trying to draw
            val clamped = clamp(v_Tex, borderWidth, 1f.lit - borderWidth)
            // this distance defines a filled round corner rectangle
            val outerCorrectDistance = distance(clamped, v_Tex)

            // this distance defines a hollow rectangle
            val innerDistancesVector = TEMP(min(v_Tex - borderWidth, 1f.lit - borderWidth - v_Tex))
            val innerCorrectDistance = min(innerDistancesVector.x, innerDistancesVector.y)

            // taking max of two distances works like intersection of this two shapes
            val correctDistance = TEMP(max(innerCorrectDistance, outerCorrectDistance))

            IF (correctDistance.le(borderWidth)) {
                val shimmerPhase = frame * shimmerSpeed

                val shimmerEffect = sin((v_Tex.x + v_Tex.y) * shimmerScale + shimmerPhase) * shimmerIntensity + shimmerBase

                val alpha = TEMP((1f.lit - smoothstep(0.5f.lit, 1.0f.lit, correctDistance / borderWidth)) * shimmerEffect)

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

        val chunkStartX = (currentOffset.x / 16).toIntFloor()
        val chunkStartY = (currentOffset.y / 16).toIntFloor()
        val chunksCountX = (screenSize.width / tileDisplaySize / 16).toIntCeil() + 1
        val chunksCountY = (screenSize.height / tileDisplaySize / 16).toIntCeil() + 1

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

        // Base textures
        val grassTexture = atlas["grass.png"].slice(RectangleInt(0, 0, 16, 16))
        val waterTexture = atlas["water.png"]
        val coalTexture = atlas["coal.png"]
        
        // Pre-rotated grass line textures
        val grassLineNorth = atlas["grass.png"].slice(RectangleInt(16, 0, 16, 16))
        val grassLineEast = atlas["grass.png"].slice(RectangleInt(32, 0, 16, 16))
        val grassLineSouth = atlas["grass.png"].slice(RectangleInt(0, 16, 16, 16))
        val grassLineWest = atlas["grass.png"].slice(RectangleInt(16, 16, 16, 16))
        
        // Pre-rotated grass corner textures
        val grassCornerNE = atlas["grass.png"].slice(RectangleInt(0, 32, 16, 16))
        val grassCornerSE = atlas["grass.png"].slice(RectangleInt(16, 32, 16, 16))
        val grassCornerSW = atlas["grass.png"].slice(RectangleInt(32, 32, 16, 16))
        val grassCornerNW = atlas["grass.png"].slice(RectangleInt(32, 16, 16, 16))

        val textures = TexturesStore(
            grass = grassTexture,
            grassLineNorth = grassLineNorth,
            grassLineEast = grassLineEast,
            grassLineSouth = grassLineSouth,
            grassLineWest = grassLineWest,
            grassCornerNE = grassCornerNE,
            grassCornerSE = grassCornerSE,
            grassCornerSW = grassCornerSW,
            grassCornerNW = grassCornerNW,
            water = waterTexture,
            coal = coalTexture,
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

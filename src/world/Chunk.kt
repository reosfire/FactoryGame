package world

import korlibs.image.bitmap.*
import korlibs.korge.blend.*
import korlibs.korge.render.*
import korlibs.math.geom.*
import scenes.*

class Chunk(
    val tiles: Array<Array<Tile>>,
    val entities: Array<Array<Entity?>>,
    val chunkX: Int,
    val chunkY: Int,
) {
    lateinit var world: World

    fun render(ctx: RenderContext, tileSize: Double, xOffset: Double, yOffset: Double, textures: TexturesStore) {
        val tileWidth = tileSize.toFloat()

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
                        tileWidth,
                        tileWidth,
                        blendMode = BlendMode.NONE,
                        filtering = false
                    )
                }
            }

            // Second pass: Draw transition tiles (grass edges and corners)
            for (x in 0..<16) {
                for (y in 0..<16) {
                    val tile = tiles[x][y]

                    if (tile.type != TileType.WATER && tile.type != TileType.COAL_ORE) continue
                    if (entities[x][y] != null) continue

                    val tileX = ((x + xOffset) * tileSize).toFloat()
                    val tileY = ((y + yOffset) * tileSize).toFloat()

                    // Check adjacent tiles (N, E, S, W) with cross-chunk support
                    val hasGrassNorth = getTileRelative(x, y - 1) == TileType.GRASS
                    val hasGrassEast = getTileRelative(x + 1, y) == TileType.GRASS
                    val hasGrassSouth = getTileRelative(x, y + 1) == TileType.GRASS
                    val hasGrassWest = getTileRelative(x - 1, y) == TileType.GRASS

                    // Check diagonal tiles with cross-chunk support
                    val hasGrassNorthEast = getTileRelative(x + 1, y - 1) == TileType.GRASS
                    val hasGrassSouthEast = getTileRelative(x + 1, y + 1) == TileType.GRASS
                    val hasGrassSouthWest = getTileRelative(x - 1, y + 1) == TileType.GRASS
                    val hasGrassNorthWest = getTileRelative(x - 1, y - 1) == TileType.GRASS

                    fun drawLineIf(condition: Boolean, texture: BmpSlice) {
                        if (condition) {
                            batcher.drawQuad(
                                ctx.getTex(texture),
                                tileX,
                                tileY,
                                tileWidth,
                                tileWidth,
                                blendMode = BlendMode.NORMAL,
                                filtering = false
                            )
                        }
                    }

                    drawLineIf(hasGrassNorth, textures.grassLineNorth)
                    drawLineIf(hasGrassEast, textures.grassLineEast)
                    drawLineIf(hasGrassSouth, textures.grassLineSouth)
                    drawLineIf(hasGrassWest, textures.grassLineWest)

                    val hasEntityNorth = getEntityRelative(x, y - 1) != null
                    val hasEntityEast = getEntityRelative(x + 1, y) != null
                    val hasEntitySouth = getEntityRelative(x, y + 1) != null
                    val hasEntityWest = getEntityRelative(x - 1, y) != null

                    fun drawCornerIf(condition: Boolean, texture: BmpSlice) {
                        if (condition) {
                            batcher.drawQuad(
                                ctx.getTex(texture),
                                tileX,
                                tileY,
                                tileWidth,
                                tileWidth,
                                blendMode = BlendMode.NORMAL,
                                filtering = false
                            )
                        }
                    }

                    drawCornerIf(!hasGrassNorth && !hasGrassEast && hasGrassNorthEast && (!hasEntityNorth || !hasEntityEast), textures.grassCornerNE)
                    drawCornerIf(!hasGrassEast && !hasGrassSouth && hasGrassSouthEast && (!hasEntitySouth || !hasEntityEast), textures.grassCornerSE)
                    drawCornerIf(!hasGrassSouth && !hasGrassWest && hasGrassSouthWest && (!hasEntitySouth || !hasEntityWest), textures.grassCornerSW)
                    drawCornerIf(!hasGrassWest && !hasGrassNorth && hasGrassNorthWest && (!hasEntityNorth || !hasEntityWest), textures.grassCornerNW)
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

    private fun getTileRelative(x: Int, y: Int): TileType {
        val chunkShiftX = x shr 4
        val chunkShiftY = y shr 4

        return when {
            chunkShiftX == 0 && chunkShiftY == 0 -> tiles[x and 15][y and 15].type
            else -> world.getChunk(chunkX + chunkShiftX, chunkY + chunkShiftY).tiles[x and 15][y and 15].type
        }
    }

    private fun getEntityRelative(x: Int, y: Int): Entity? {
        val chunkShiftX = x shr 4
        val chunkShiftY = y shr 4

        return when {
            chunkShiftX == 0 && chunkShiftY == 0 -> entities[x and 15][y and 15]
            else -> world.getChunk(chunkX + chunkShiftX, chunkY + chunkShiftY).entities[x and 15][y and 15]
        }
    }
}

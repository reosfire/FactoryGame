package world

import korlibs.korge.blend.*
import korlibs.korge.render.*
import korlibs.math.geom.*
import scenes.*

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
                    if (tile.type != TileType.WATER && tile.type != TileType.COAL_ORE) continue
                    if (entities[x][y] != null) continue

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
                        // Check if there are entities in the north or east tiles that would cut the corner
                        val hasEntityNorth = when {
                            y > 0 -> entities[x][y - 1] != null
                            else -> world?.getChunk(chunkX, chunkY - 1)?.entities?.get(x)?.get(15) != null
                        }
                        val hasEntityEast = when {
                            x < 15 -> entities[x + 1][y] != null
                            else -> world?.getChunk(chunkX + 1, chunkY)?.entities?.get(0)?.get(y) != null
                        }

                        if (!hasEntityNorth || !hasEntityEast) {
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
                    }
                    if (!hasGrassEast && !hasGrassSouth && hasGrassSouthEast) {
                        // Check if there are entities in the south or east tiles that would cut the corner
                        val hasEntitySouth = when {
                            y < 15 -> entities[x][y + 1] != null
                            else -> world?.getChunk(chunkX, chunkY + 1)?.entities?.get(x)?.get(0) != null
                        }
                        val hasEntityEast = when {
                            x < 15 -> entities[x + 1][y] != null
                            else -> world?.getChunk(chunkX + 1, chunkY)?.entities?.get(0)?.get(y) != null
                        }

                        if (!hasEntitySouth || !hasEntityEast) {
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
                    }
                    if (!hasGrassSouth && !hasGrassWest && hasGrassSouthWest) {
                        // Check if there are entities in the south or west tiles that would cut the corner
                        val hasEntitySouth = when {
                            y < 15 -> entities[x][y + 1] != null
                            else -> world?.getChunk(chunkX, chunkY + 1)?.entities?.get(x)?.get(0) != null
                        }
                        val hasEntityWest = when {
                            x > 0 -> entities[x - 1][y] != null
                            else -> world?.getChunk(chunkX - 1, chunkY)?.entities?.get(15)?.get(y) != null
                        }

                        if (!hasEntitySouth || !hasEntityWest) {
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
                    }
                    if (!hasGrassWest && !hasGrassNorth && hasGrassNorthWest) {
                        // Check if there are entities in the north or west tiles that would cut the corner
                        val hasEntityNorth = when {
                            y > 0 -> entities[x][y - 1] != null
                            else -> world?.getChunk(chunkX, chunkY - 1)?.entities?.get(x)?.get(15) != null
                        }
                        val hasEntityWest = when {
                            x > 0 -> entities[x - 1][y] != null
                            else -> world?.getChunk(chunkX - 1, chunkY)?.entities?.get(15)?.get(y) != null
                        }

                        if (!hasEntityNorth || !hasEntityWest) {
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

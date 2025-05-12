package world

import kotlin.random.*

interface WorldGenerator {
    fun generateChunk(x: Int, y: Int): Chunk
}

class RandomWorldGenerator : WorldGenerator {
    override fun generateChunk(x: Int, y: Int): Chunk {
        val tiles = Array(16) { Array(16) { Tile.random() } }
        val entities = Array(16) { Array<Entity?>(16) { null } }

        return Chunk(tiles, entities, x, y)
    }
}

class NormalWorldGenerator : WorldGenerator {
    val random = Random(12345)
    private val perlinNoise = PerlinNoise2D(random)

    private val terrainScale = 0.1

    override fun generateChunk(x: Int, y: Int): Chunk {
        val tiles = Array(16) { tileX->
            Array(16) { tileY ->
                val worldX = (x shl 4) + tileX
                val worldY = (y shl 4) + tileY

                val elevation = (perlinNoise.noise(worldX * terrainScale, worldY * terrainScale) + 1) / 2

                when {
                    elevation < 0.5 -> Tile.Water
                    elevation < 0.7 -> Tile.Grass
                    else -> Tile.CoalOre
                }
            }
        }

        val entities = Array(16) { tileY ->
            Array<Entity?>(16) { tileX ->
                null
            }
        }

        return Chunk(tiles, entities, x, y)
    }
}

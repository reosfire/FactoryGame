package world

import scenes.*

class NormalWorldGenerator : WorldGenerator {
    private val perlinNoise = PerlinNoise2D(12345)

    private val terrainScale = 0.1

    override fun generateChunk(x: Int, y: Int): Chunk {
        val tiles = Array(16) { tileX ->
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

        val entities = Array(16) { Array<Entity?>(16) { null } }

        return Chunk(tiles, entities)
    }
}

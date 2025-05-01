package world

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

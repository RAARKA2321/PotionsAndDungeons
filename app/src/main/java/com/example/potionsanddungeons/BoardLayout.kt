package com.example.potionsanddungeons

data class BoardLayout(
    val name: String,
    val size: Int,
    val tiles: Set<Cell>,
    val starts: List<Cell>,
    val bossStart: Cell,
    val potions: Set<Cell> = emptySet(),
    val doors: Set<Cell> = emptySet()
) {
    init {
        require(name.isNotBlank() && name.length <= MAX_NAME)
        require(size == BOARD_SIZE || size == 7)
        require(tiles.size >= MIN_TILES && tiles.all { it.inBounds(size) })
        require(boardDistances(bossStart, tiles, size).size == tiles.size)
        require(starts.size == 4 && starts.toSet().size == 4 && bossStart !in starts)
        require(starts.all { it in tiles } && bossStart in tiles)
        require(doors.all { it in tiles && it !in starts && it != bossStart })
        require(potions.all { it in tiles && it !in doors })
    }

    companion object {
        const val MIN_TILES = 5
        const val MAX_NAME = 40
        const val MAX_LAYOUTS = 12
    }
}

internal fun boardDistances(
    start: Cell, tiles: Set<Cell>, size: Int, diagonal: Boolean = false
): Map<Cell, Int> {
    if (start !in tiles) return emptyMap()
    val distances = mutableMapOf(start to 0)
    val frontier = ArrayDeque(listOf(start))
    while (frontier.isNotEmpty()) {
        val cell = frontier.removeFirst()
        val steps = distances.getValue(cell) + 1
        val adjacent = if (diagonal) cell.neighbors8(size).filter { next ->
            Cell(cell.row, next.col) in tiles || Cell(next.row, cell.col) in tiles
        } else cell.neighbors4(size)
        adjacent.forEach { next ->
            if (next in tiles && distances.putIfAbsent(next, steps) == null) frontier.addLast(next)
        }
    }
    return distances
}

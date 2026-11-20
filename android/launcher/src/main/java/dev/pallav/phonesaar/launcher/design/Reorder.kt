package dev.pallav.phonesaar.launcher.design

/** Drag-and-drop reordering for the grid: moves a block to a column and row, keeping masonry order. */
object Reorder {
    fun move(blocks: List<Block>, from: Int, targetCol: Int, targetRow: Int, columns: Int): List<Block> {
        val moved = blocks.getOrNull(from) ?: return blocks
        val rest = blocks.filterIndexed { i, _ -> i != from }
        val col = if (moved.colSpan == 1) targetCol.coerceIn(0, columns - 1) else -1
        val placed = GridLayout.place(rest, columns)
        val before = placed.firstOrNull { p ->
            val inCol = if (moved.colSpan == 1) col in p.col until p.col + p.colSpan else true
            inCol && p.row >= targetRow
        }
        val at = before?.index ?: rest.size
        return rest.toMutableList().also { it.add(at, moved.copy(col = col)) }
    }

    fun swap(blocks: List<Block>, i: Int, delta: Int): List<Block> {
        val j = i + delta
        if (i !in blocks.indices || j !in blocks.indices) return blocks
        return blocks.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }
}

package dev.pallav.phonesaar.launcher.design

data class Placed(val index: Int, val col: Int, val row: Int, val colSpan: Int, val rowSpan: Int)

/** Masonry placement of visible blocks into columns measured in row units; pure so it can be tested. */
object GridLayout {
    /** Blocks flow in order into the shortest column (or column run); a block with `col` set goes to that column. */
    fun place(blocks: List<Block>, columns: Int): List<Placed> {
        val heights = IntArray(columns)
        val out = mutableListOf<Placed>()
        blocks.forEachIndexed { i, b ->
            if (b.hidden) return@forEachIndexed
            val span = b.colSpan.coerceIn(1, columns)
            val rows = b.rowSpan.coerceAtLeast(1)
            var bestCol = 0
            var bestRow = Int.MAX_VALUE
            val fixed = if (b.col in 0 until columns) b.col.coerceAtMost(columns - span) else -1
            for (c in 0..columns - span) {
                if (fixed >= 0 && c != fixed) continue
                val start = (c until c + span).maxOf { heights[it] }
                if (start < bestRow) { bestRow = start; bestCol = c }
            }
            for (c in bestCol until bestCol + span) heights[c] = bestRow + rows
            out += Placed(i, bestCol, bestRow, span, rows)
        }
        return out
    }

    fun totalRows(placed: List<Placed>) = placed.maxOfOrNull { it.row + it.rowSpan } ?: 0
}

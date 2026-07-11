package com.github.kassett.exceleditor.workbook

data class WorkbookSnapshot(
    val sheets: List<SheetSnapshot>,
)

data class SheetSnapshot(
    val name: String,
    val rowCount: Int,
    val columnCount: Int,
    val cells: MutableMap<Pair<Int, Int>, String>,
) {
    fun setCell(
        rowIndex: Int,
        columnIndex: Int,
        value: String,
    ) {
        if (value.isBlank()) {
            cells.remove(rowIndex to columnIndex)
        } else {
            cells[rowIndex to columnIndex] = value
        }
    }
}

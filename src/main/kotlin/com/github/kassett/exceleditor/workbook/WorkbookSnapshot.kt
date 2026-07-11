package com.github.kassett.exceleditor.workbook

data class WorkbookSnapshot(
    val sheets: List<SheetSnapshot>,
)

data class SheetSnapshot(
    val name: String,
    var rowCount: Int,
    var columnCount: Int,
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

    fun insertRow(rowIndex: Int) {
        val shiftedCells =
            cells
                .entries
                .associate { (address, value) ->
                    val (row, column) = address
                    if (row >= rowIndex) {
                        (row + 1 to column) to value
                    } else {
                        address to value
                    }
                }
        cells.clear()
        cells.putAll(shiftedCells)
        rowCount += 1
    }

    fun deleteRow(rowIndex: Int) {
        val shiftedCells =
            cells
                .entries
                .mapNotNull { (address, value) ->
                    val (row, column) = address
                    when {
                        row == rowIndex -> null
                        row > rowIndex -> (row - 1 to column) to value
                        else -> address to value
                    }
                }.toMap()
        cells.clear()
        cells.putAll(shiftedCells)
        rowCount = maxOf(rowCount - 1, 1)
    }

    fun insertColumn(columnIndex: Int) {
        val shiftedCells =
            cells
                .entries
                .associate { (address, value) ->
                    val (row, column) = address
                    if (column >= columnIndex) {
                        (row to column + 1) to value
                    } else {
                        address to value
                    }
                }
        cells.clear()
        cells.putAll(shiftedCells)
        columnCount += 1
    }

    fun deleteColumn(columnIndex: Int) {
        val shiftedCells =
            cells
                .entries
                .mapNotNull { (address, value) ->
                    val (row, column) = address
                    when {
                        column == columnIndex -> null
                        column > columnIndex -> (row to column - 1) to value
                        else -> address to value
                    }
                }.toMap()
        cells.clear()
        cells.putAll(shiftedCells)
        columnCount = maxOf(columnCount - 1, 1)
    }
}

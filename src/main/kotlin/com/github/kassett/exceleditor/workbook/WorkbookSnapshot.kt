package com.github.kassett.exceleditor.workbook

data class WorkbookSnapshot(
    val sheets: List<SheetSnapshot>,
)

data class SheetSnapshot(
    val name: String,
    val rowCount: Int,
    val columnCount: Int,
    val cells: Map<Pair<Int, Int>, String>,
)

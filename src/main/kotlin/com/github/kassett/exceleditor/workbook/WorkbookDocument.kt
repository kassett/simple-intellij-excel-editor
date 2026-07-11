package com.github.kassett.exceleditor.workbook

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream

class WorkbookDocument private constructor(
    private val workbook: org.apache.poi.ss.usermodel.Workbook,
    val snapshot: WorkbookSnapshot,
) : Closeable {
    fun updateCell(
        sheetIndex: Int,
        rowIndex: Int,
        columnIndex: Int,
        value: String,
    ) {
        val sheet = workbook.getSheetAt(sheetIndex)
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        val cell = row.getCell(columnIndex) ?: row.createCell(columnIndex)

        if (value.isBlank()) {
            row.removeCell(cell)
        } else {
            cell.setCellValue(value)
        }

        snapshot.sheets[sheetIndex].setCell(rowIndex, columnIndex, value)
    }

    fun insertRow(
        sheetIndex: Int,
        rowIndex: Int,
    ) {
        val sheet = workbook.getSheetAt(sheetIndex)
        val lastRow = sheet.lastRowNum
        if (rowIndex <= lastRow) {
            sheet.shiftRows(rowIndex, lastRow, 1, true, false)
        }
        sheet.createRow(rowIndex)
        snapshot.sheets[sheetIndex].insertRow(rowIndex)
    }

    fun deleteRow(
        sheetIndex: Int,
        rowIndex: Int,
    ) {
        val sheet = workbook.getSheetAt(sheetIndex)
        val row = sheet.getRow(rowIndex)
        if (row != null) {
            sheet.removeRow(row)
        }

        if (rowIndex < sheet.lastRowNum) {
            sheet.shiftRows(rowIndex + 1, sheet.lastRowNum, -1, true, false)
        }
        snapshot.sheets[sheetIndex].deleteRow(rowIndex)
    }

    fun insertColumn(
        sheetIndex: Int,
        columnIndex: Int,
    ) {
        val sheet = workbook.getSheetAt(sheetIndex)
        sheet.rowIterator().forEach { row ->
            val lastCellIndex = row.lastCellNum.toIntOrZero()
            if (columnIndex <= lastCellIndex) {
                row.shiftCellsRight(columnIndex, lastCellIndex, 1)
            }
        }
        snapshot.sheets[sheetIndex].insertColumn(columnIndex)
    }

    fun deleteColumn(
        sheetIndex: Int,
        columnIndex: Int,
    ) {
        val sheet = workbook.getSheetAt(sheetIndex)
        sheet.rowIterator().forEach { row ->
            val cell = row.getCell(columnIndex)
            if (cell != null) {
                row.removeCell(cell)
            }

            if (columnIndex < row.lastCellNum.toIntOrZero()) {
                row.shiftCellsLeft(columnIndex + 1, row.lastCellNum.toIntOrZero(), 1)
            }
        }
        snapshot.sheets[sheetIndex].deleteColumn(columnIndex)
    }

    fun writeToBytes(): ByteArray =
        ByteArrayOutputStream().use { output ->
            workbook.write(output)
            output.toByteArray()
        }

    override fun close() {
        workbook.close()
    }

    companion object {
        fun load(inputStream: InputStream): WorkbookDocument =
            inputStream.use { stream ->
                val workbook = WorkbookFactory.create(stream)
                WorkbookDocument(workbook, workbook.toSnapshot())
            }

        private fun org.apache.poi.ss.usermodel.Workbook.toSnapshot(): WorkbookSnapshot {
            val formatter = DataFormatter()
            return WorkbookSnapshot(
                sheets =
                    (0 until numberOfSheets).map { sheetIndex ->
                        val sheet = getSheetAt(sheetIndex)
                        val cells = mutableMapOf<Pair<Int, Int>, String>()
                        var maxColumn = 0

                        sheet.rowIterator().forEach { row ->
                            maxColumn = maxOf(maxColumn, row.lastCellNum.toIntOrZero())
                            row.cellIterator().forEach { cell ->
                                cells[row.rowNum to cell.columnIndex] = cell.displayValue(formatter)
                            }
                        }

                        SheetSnapshot(
                            name = sheet.sheetName,
                            rowCount = maxOf(sheet.lastRowNum + 1, 1),
                            columnCount = maxOf(maxColumn, 1),
                            cells = cells,
                        )
                    },
            )
        }

        private fun Cell.displayValue(formatter: DataFormatter): String =
            when (cellType) {
                CellType.FORMULA -> cellFormula
                else -> formatter.formatCellValue(this)
            }

        private fun Short.toIntOrZero(): Int = if (this < 0) 0 else toInt()
    }
}

package com.github.kassett.exceleditor.workbook

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object WorkbookLoader {
    fun load(inputStream: InputStream): WorkbookSnapshot =
        inputStream.use { stream ->
            WorkbookFactory.create(stream).use { workbook ->
                val formatter = DataFormatter()
                WorkbookSnapshot(
                    sheets =
                        (0 until workbook.numberOfSheets).map { sheetIndex ->
                            val sheet = workbook.getSheetAt(sheetIndex)
                            val cells = mutableMapOf<Pair<Int, Int>, String>()
                            var maxColumn = 0

                            sheet.rowIterator().forEach { row ->
                                maxColumn = maxOf(maxColumn, row.lastCellNum.toIntOrZero())
                                row.cellIterator().forEach { cell ->
                                    cells[row.rowNum to cell.columnIndex] = formatter.formatCellValue(cell)
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
        }

    private fun Short.toIntOrZero(): Int = if (this < 0) 0 else toInt()
}

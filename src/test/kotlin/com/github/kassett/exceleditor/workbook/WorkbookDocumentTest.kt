package com.github.kassett.exceleditor.workbook

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class WorkbookDocumentTest {
    @Test
    fun `loads workbook sheets and formatted cell values`() {
        val snapshot = WorkbookDocument.load(ByteArrayInputStream(testWorkbookBytes())).use { it.snapshot }

        assertEquals(listOf("Data", "Empty"), snapshot.sheets.map { it.name })
        assertEquals(2, snapshot.sheets[0].rowCount)
        assertEquals(2, snapshot.sheets[0].columnCount)
        assertEquals("Name", snapshot.sheets[0].cells[0 to 0])
        assertEquals("42", snapshot.sheets[0].cells[1 to 1])
        assertEquals(1, snapshot.sheets[1].rowCount)
        assertEquals(1, snapshot.sheets[1].columnCount)
    }

    @Test
    fun `updates text cells and writes workbook bytes`() {
        val updatedBytes =
            WorkbookDocument.load(ByteArrayInputStream(testWorkbookBytes())).use { document ->
                document.updateCell(sheetIndex = 0, rowIndex = 1, columnIndex = 0, value = "Gadgets")
                document.writeToBytes()
            }

        WorkbookFactory.create(ByteArrayInputStream(updatedBytes)).use { workbook ->
            assertEquals(
                "Gadgets",
                workbook
                    .getSheet("Data")
                    .getRow(1)
                    .getCell(0)
                    .stringCellValue,
            )
        }
    }

    private fun testWorkbookBytes(): ByteArray =
        ByteArrayOutputStream().use { output ->
            XSSFWorkbook().use { workbook ->
                val firstSheet = workbook.createSheet("Data")
                firstSheet.createRow(0).createCell(0).setCellValue("Name")
                firstSheet.getRow(0).createCell(1).setCellValue("Count")
                firstSheet.createRow(1).createCell(0).setCellValue("Widgets")
                firstSheet.getRow(1).createCell(1).setCellValue(42.0)
                workbook.createSheet("Empty")
                workbook.write(output)
            }
            output.toByteArray()
        }
}

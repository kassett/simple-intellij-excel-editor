package com.github.kassett.exceleditor.workbook

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class WorkbookLoaderTest {
    @Test
    fun `loads workbook sheets and formatted cell values`() {
        val bytes =
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

        val snapshot = WorkbookLoader.load(ByteArrayInputStream(bytes))

        assertEquals(listOf("Data", "Empty"), snapshot.sheets.map { it.name })
        assertEquals(2, snapshot.sheets[0].rowCount)
        assertEquals(2, snapshot.sheets[0].columnCount)
        assertEquals("Name", snapshot.sheets[0].cells[0 to 0])
        assertEquals("42", snapshot.sheets[0].cells[1 to 1])
        assertEquals(1, snapshot.sheets[1].rowCount)
        assertEquals(1, snapshot.sheets[1].columnCount)
    }
}

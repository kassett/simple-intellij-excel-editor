package com.github.kassett.exceleditor.editor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExcelFileTypesTest {
    @Test
    fun `accepts modern Excel workbook extensions`() {
        assertTrue(ExcelFileTypes.isSupportedFileName("budget.xlsx"))
        assertTrue(ExcelFileTypes.isSupportedFileName("macro.XLSM"))
    }

    @Test
    fun `rejects unsupported file extensions`() {
        assertFalse(ExcelFileTypes.isSupportedFileName("legacy.xls"))
        assertFalse(ExcelFileTypes.isSupportedFileName("notes.txt"))
        assertFalse(ExcelFileTypes.isSupportedFileName("xlsx"))
    }
}

package com.github.kassett.exceleditor.editor

object ExcelFileTypes {
    private val supportedExtensions = setOf("xlsx", "xlsm")

    fun isSupportedFileName(fileName: String): Boolean =
        fileName
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase() in supportedExtensions
}

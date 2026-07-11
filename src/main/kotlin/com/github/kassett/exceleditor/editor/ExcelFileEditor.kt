package com.github.kassett.exceleditor.editor

import com.github.kassett.exceleditor.workbook.WorkbookLoader
import com.github.kassett.exceleditor.workbook.WorkbookSnapshot
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel

class ExcelFileEditor(
    private val file: VirtualFile,
) : UserDataHolderBase(),
    FileEditor {
    private val root = JPanel(BorderLayout())

    init {
        root.add(JBLabel("Loading workbook..."), BorderLayout.CENTER)
        loadWorkbook()
    }

    override fun getComponent(): JComponent = root

    override fun getPreferredFocusedComponent(): JComponent = root

    override fun getName(): String = "Excel"

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() = Unit

    private fun loadWorkbook() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result =
                runCatching {
                    WorkbookLoader.load(file.inputStream)
                }

            SwingUtilities.invokeLater {
                root.removeAll()
                root.add(result.fold(::workbookComponent, ::errorComponent), BorderLayout.CENTER)
                root.revalidate()
                root.repaint()
            }
        }
    }

    private fun workbookComponent(workbook: WorkbookSnapshot): JComponent {
        val tabs = JBTabbedPane()
        workbook.sheets.forEach { sheet ->
            val table = JBTable(SheetTableModel(sheet))
            table.autoResizeMode = JBTable.AUTO_RESIZE_OFF
            table.setDefaultEditor(Any::class.java, null)
            tabs.addTab(sheet.name, JBScrollPane(table))
        }
        return tabs
    }

    private fun errorComponent(error: Throwable): JComponent =
        JBLabel("Unable to read workbook: ${error.message ?: error::class.simpleName}")
}

private class SheetTableModel(
    private val sheet: com.github.kassett.exceleditor.workbook.SheetSnapshot,
) : AbstractTableModel() {
    override fun getRowCount(): Int = sheet.rowCount

    override fun getColumnCount(): Int = sheet.columnCount

    override fun getColumnName(column: Int): String = columnName(column)

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any = sheet.cells[rowIndex to columnIndex].orEmpty()

    override fun isCellEditable(
        rowIndex: Int,
        columnIndex: Int,
    ): Boolean = false

    private fun columnName(index: Int): String {
        var value = index + 1
        val name = StringBuilder()
        while (value > 0) {
            val remainder = (value - 1) % 26
            name.append(('A'.code + remainder).toChar())
            value = (value - 1) / 26
        }
        return name.reverse().toString()
    }
}

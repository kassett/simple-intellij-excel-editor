package com.github.kassett.exceleditor.editor

import com.github.kassett.exceleditor.workbook.SheetSnapshot
import com.github.kassett.exceleditor.workbook.WorkbookDocument
import com.github.kassett.exceleditor.workbook.WorkbookSnapshot
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel

class ExcelFileEditor(
    private val file: VirtualFile,
) : UserDataHolderBase(),
    FileEditor {
    private val root = JPanel(BorderLayout())
    private val propertyChangeSupport = PropertyChangeSupport(this)
    private var document: WorkbookDocument? = null
    private var modified = false
    private var saveButton: JButton? = null

    init {
        root.add(JBLabel("Loading workbook..."), BorderLayout.CENTER)
        loadWorkbook()
    }

    override fun getComponent(): JComponent = root

    override fun getPreferredFocusedComponent(): JComponent = root

    override fun getName(): String = "Excel"

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun isModified(): Boolean = modified

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        document?.close()
        document = null
    }

    private fun loadWorkbook() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result =
                runCatching {
                    WorkbookDocument.load(file.inputStream)
                }

            SwingUtilities.invokeLater {
                root.removeAll()
                root.add(
                    result.fold(
                        onSuccess = { loadedDocument ->
                            document = loadedDocument
                            workbookComponent(loadedDocument.snapshot)
                        },
                        onFailure = ::errorComponent,
                    ),
                    BorderLayout.CENTER,
                )
                root.revalidate()
                root.repaint()
            }
        }
    }

    private fun workbookComponent(workbook: WorkbookSnapshot): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        val toolbar = JBPanel<JBPanel<*>>(BorderLayout())
        val save = JButton("Save")
        save.isEnabled = false
        save.addActionListener { saveWorkbook() }
        saveButton = save
        toolbar.add(save, BorderLayout.WEST)
        panel.add(toolbar, BorderLayout.NORTH)

        val tabs = JBTabbedPane()
        val editable = file.extension.equals("xlsx", ignoreCase = true)
        workbook.sheets.forEachIndexed { sheetIndex, sheet ->
            val table = JBTable(SheetTableModel(sheet, ::updateCell, sheetIndex, editable))
            table.autoResizeMode = JBTable.AUTO_RESIZE_OFF
            tabs.addTab(sheet.name, JBScrollPane(table))
        }
        panel.add(tabs, BorderLayout.CENTER)
        return panel
    }

    private fun errorComponent(error: Throwable): JComponent =
        JBLabel("Unable to read workbook: ${error.message ?: error::class.simpleName}")

    private fun updateCell(
        sheetIndex: Int,
        rowIndex: Int,
        columnIndex: Int,
        value: String,
    ) {
        document?.updateCell(sheetIndex, rowIndex, columnIndex, value)
        setModified(true)
    }

    private fun saveWorkbook() {
        val bytes = document?.writeToBytes() ?: return
        ApplicationManager.getApplication().runWriteAction {
            file.setBinaryContent(bytes)
        }
        setModified(false)
    }

    private fun setModified(nextModified: Boolean) {
        if (modified == nextModified) {
            return
        }

        val previousModified = modified
        modified = nextModified
        saveButton?.isEnabled = nextModified
        propertyChangeSupport.firePropertyChange(
            PropertyChangeEvent(this, FileEditor.getPropModified(), previousModified, nextModified),
        )
    }
}

private class SheetTableModel(
    private val sheet: SheetSnapshot,
    private val updateCell: (Int, Int, Int, String) -> Unit,
    private val sheetIndex: Int,
    private val editable: Boolean,
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
    ): Boolean = editable

    override fun setValueAt(
        value: Any?,
        rowIndex: Int,
        columnIndex: Int,
    ) {
        val text = value?.toString().orEmpty()
        updateCell(sheetIndex, rowIndex, columnIndex, text)
        fireTableCellUpdated(rowIndex, columnIndex)
    }

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

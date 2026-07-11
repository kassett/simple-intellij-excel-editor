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
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

class ExcelFileEditor(
    private val file: VirtualFile,
) : UserDataHolderBase(),
    FileEditor {
    private val root = JPanel(BorderLayout())
    private val propertyChangeSupport = PropertyChangeSupport(this)
    private var document: WorkbookDocument? = null
    private var modified = false
    private var workbookComponent: JComponent? = null
    private var selectedSheetIndex = 0
    private var selectedRowIndex = 0
    private var selectedColumnIndex = 0
    private var restoreButton: JButton? = null
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
                            runCatching {
                                createWorkbookComponent(loadedDocument.snapshot)
                            }.getOrElse(::errorComponent)
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

    private fun createWorkbookComponent(workbook: WorkbookSnapshot): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        val toolbar = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT))
        val editable = file.extension.equals("xlsx", ignoreCase = true)

        val restore = JButton("Restore")
        restore.isEnabled = false
        restore.addActionListener { restoreWorkbook() }
        restoreButton = restore
        toolbar.add(restore)

        val save = JButton("Save")
        save.isEnabled = false
        save.addActionListener { saveWorkbook() }
        saveButton = save
        toolbar.add(save)
        panel.add(toolbar, BorderLayout.NORTH)

        val tabs = JBTabbedPane()
        workbook.sheets.forEachIndexed { sheetIndex, sheet ->
            val table = JBTable(SheetTableModel(sheet, ::updateCell, sheetIndex, editable))
            table.autoResizeMode = JBTable.AUTO_RESIZE_OFF
            table.tableHeader.defaultRenderer = SpreadsheetHeaderRenderer(table.tableHeader.defaultRenderer)
            table.selectionModel.addListSelectionListener {
                if (!it.valueIsAdjusting) {
                    selectedSheetIndex = sheetIndex
                    selectedRowIndex = maxOf(table.selectedRow, 0).toWorkbookRowIndex()
                }
            }
            table.columnModel.selectionModel.addListSelectionListener {
                if (!it.valueIsAdjusting) {
                    selectedSheetIndex = sheetIndex
                    selectedColumnIndex = maxOf(table.selectedColumn, 0)
                }
            }
            if (editable) {
                installColumnHeaderMenu(table, sheetIndex)
            }

            val scrollPane = JBScrollPane(table)
            val rowHeader = JBTable(RowHeaderTableModel(sheet))
            rowHeader.rowHeight = table.rowHeight
            rowHeader.autoResizeMode = JBTable.AUTO_RESIZE_OFF
            rowHeader.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            rowHeader.preferredScrollableViewportSize = Dimension(48, 0)
            rowHeader.tableHeader = null
            rowHeader.setDefaultRenderer(Any::class.java, SpreadsheetHeaderRenderer())
            rowHeader.selectionModel.addListSelectionListener {
                if (!it.valueIsAdjusting) {
                    selectedSheetIndex = sheetIndex
                    selectedRowIndex = maxOf(rowHeader.selectedRow, 0)
                    if (rowHeader.selectedRow >= 0) {
                        table.setRowSelectionInterval(rowHeader.selectedRow, rowHeader.selectedRow)
                    }
                }
            }
            if (editable) {
                installRowHeaderMenu(rowHeader, sheetIndex)
            }
            scrollPane.setRowHeaderView(rowHeader)

            tabs.addTab(sheet.name, scrollPane)
        }
        tabs.addChangeListener {
            selectedSheetIndex = maxOf(tabs.selectedIndex, 0)
        }
        panel.add(tabs, BorderLayout.CENTER)
        workbookComponent = panel
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
        document?.updateCell(sheetIndex, rowIndex.toWorkbookRowIndex(), columnIndex, value)
        setModified(true)
    }

    private fun alterWorkbook(alteration: WorkbookDocument.(Int) -> Unit) {
        val currentDocument = document ?: return
        currentDocument.alteration(selectedSheetIndex)
        setModified(true)
        reloadCurrentDocument()
    }

    private fun installColumnHeaderMenu(
        table: JBTable,
        sheetIndex: Int,
    ) {
        table.tableHeader.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) = maybeShowColumnMenu(event)

                override fun mouseReleased(event: MouseEvent) = maybeShowColumnMenu(event)

                private fun maybeShowColumnMenu(event: MouseEvent) {
                    if (!event.isPopupTrigger) {
                        return
                    }

                    val columnIndex = table.columnAtPoint(event.point)
                    if (columnIndex < 0) {
                        return
                    }

                    selectedSheetIndex = sheetIndex
                    selectedColumnIndex = columnIndex
                    table.setColumnSelectionInterval(columnIndex, columnIndex)

                    JPopupMenu()
                        .addAction("Insert Column Left") {
                            alterWorkbook { insertColumn(it, columnIndex) }
                        }.addAction("Insert Column Right") {
                            alterWorkbook { insertColumn(it, columnIndex + 1) }
                        }.addAction("Delete Column") {
                            alterWorkbook { deleteColumn(it, columnIndex) }
                        }.show(event.component, event.x, event.y)
                }
            },
        )
    }

    private fun installRowHeaderMenu(
        rowHeader: JBTable,
        sheetIndex: Int,
    ) {
        rowHeader.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) = maybeShowRowMenu(event)

                override fun mouseReleased(event: MouseEvent) = maybeShowRowMenu(event)

                private fun maybeShowRowMenu(event: MouseEvent) {
                    if (!event.isPopupTrigger) {
                        return
                    }

                    val rowIndex = rowHeader.rowAtPoint(event.point)
                    if (rowIndex < 0) {
                        return
                    }

                    selectedSheetIndex = sheetIndex
                    selectedRowIndex = rowIndex.toWorkbookRowIndex()
                    rowHeader.setRowSelectionInterval(rowIndex, rowIndex)

                    JPopupMenu()
                        .addAction("Insert Row Above") {
                            alterWorkbook { insertRow(it, rowIndex.toWorkbookRowIndex()) }
                        }.addAction("Insert Row Below") {
                            alterWorkbook { insertRow(it, rowIndex.toWorkbookRowIndex() + 1) }
                        }.addAction("Delete Row") {
                            alterWorkbook { deleteRow(it, rowIndex.toWorkbookRowIndex()) }
                        }.show(event.component, event.x, event.y)
                }
            },
        )
    }

    private fun restoreWorkbook() {
        document?.close()
        document = null
        setModified(false)
        root.removeAll()
        root.add(JBLabel("Loading workbook..."), BorderLayout.CENTER)
        root.revalidate()
        root.repaint()
        loadWorkbook()
    }

    private fun saveWorkbook() {
        val bytes = document?.writeToBytes() ?: return
        ApplicationManager.getApplication().runWriteAction {
            file.setBinaryContent(bytes)
        }
        setModified(false)
    }

    private fun reloadCurrentDocument() {
        val currentDocument = document ?: return
        val oldComponent = workbookComponent
        val newComponent = createWorkbookComponent(currentDocument.snapshot)
        if (oldComponent != null) {
            root.remove(oldComponent)
        } else {
            root.removeAll()
        }
        root.add(newComponent, BorderLayout.CENTER)
        root.revalidate()
        root.repaint()
    }

    private fun setModified(nextModified: Boolean) {
        if (modified == nextModified) {
            return
        }

        val previousModified = modified
        modified = nextModified
        restoreButton?.isEnabled = nextModified
        saveButton?.isEnabled = nextModified
        propertyChangeSupport.firePropertyChange(
            PropertyChangeEvent(this, FileEditor.getPropModified(), previousModified, nextModified),
        )
    }

    private fun JPopupMenu.addAction(
        label: String,
        action: () -> Unit,
    ): JPopupMenu {
        val item = JMenuItem(label)
        item.addActionListener { action() }
        add(item)
        return this
    }

    private fun Int.toWorkbookRowIndex(): Int = this + 1
}

private class SpreadsheetHeaderRenderer(
    private val delegate: TableCellRenderer? = null,
) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val component =
            delegate?.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                ?: super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        component.background = EXCEL_GREEN
        component.foreground = Color.WHITE
        if (component is DefaultTableCellRenderer) {
            component.horizontalAlignment = CENTER
        }
        return component
    }

    companion object {
        private val EXCEL_GREEN = Color(33, 115, 70)
    }
}

private class RowHeaderTableModel(
    private val sheet: SheetSnapshot,
) : AbstractTableModel() {
    override fun getRowCount(): Int = maxOf(sheet.rowCount - 1, 0)

    override fun getColumnCount(): Int = 1

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any = rowIndex + 2

    override fun isCellEditable(
        rowIndex: Int,
        columnIndex: Int,
    ): Boolean = false
}

private class SheetTableModel(
    private val sheet: SheetSnapshot,
    private val updateCell: (Int, Int, Int, String) -> Unit,
    private val sheetIndex: Int,
    private val editable: Boolean,
) : AbstractTableModel() {
    override fun getRowCount(): Int = maxOf(sheet.rowCount - 1, 0)

    override fun getColumnCount(): Int = sheet.columnCount

    override fun getColumnName(column: Int): String =
        sheet.cells[0 to column]
            ?.takeIf { it.isNotBlank() }
            ?: columnName(column)

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any = sheet.cells[rowIndex + 1 to columnIndex].orEmpty()

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

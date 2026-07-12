package com.github.kassett.exceleditor.editor

import com.github.kassett.exceleditor.workbook.SheetSnapshot
import com.github.kassett.exceleditor.workbook.WorkbookDocument
import com.github.kassett.exceleditor.workbook.WorkbookSnapshot
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ExcelFileEditor(
    private val file: VirtualFile,
) : UserDataHolderBase(),
    FileEditor {
    private val root = JPanel(BorderLayout())
    private val propertyChangeSupport = PropertyChangeSupport(this)
    private val gson = Gson()
    private var document: WorkbookDocument? = null
    private var browser: JBCefBrowser? = null
    private var bridgeQuery: JBCefJSQuery? = null
    private var modified = false
    private val lafListener =
        LafManagerListener {
            applyThemeToBrowser()
        }

    init {
        root.add(JBLabel("Loading workbook..."), BorderLayout.CENTER)
        ApplicationManager
            .getApplication()
            .messageBus
            .connect(this)
            .subscribe(LafManagerListener.TOPIC, lafListener)
        loadWorkbook(recreateBrowser = true)
    }

    override fun getComponent(): JComponent = root

    override fun getPreferredFocusedComponent(): JComponent = browser?.component ?: root

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
        disposeBrowser()
        document?.close()
        document = null
    }

    private fun loadWorkbook(recreateBrowser: Boolean) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result =
                runCatching {
                    WorkbookDocument.load(file.inputStream)
                }

            SwingUtilities.invokeLater {
                result.fold(
                    onSuccess = { loadedDocument ->
                        document?.close()
                        document = loadedDocument
                        setModified(false)

                        if (recreateBrowser || browser == null) {
                            root.removeAll()
                            root.add(createBrowserComponent(loadedDocument.snapshot), BorderLayout.CENTER)
                            root.revalidate()
                            root.repaint()
                        } else {
                            sendWorkbookToBrowser()
                        }
                    },
                    onFailure = { error ->
                        root.removeAll()
                        root.add(errorComponent(error), BorderLayout.CENTER)
                        root.revalidate()
                        root.repaint()
                    },
                )
            }
        }
    }

    private fun createBrowserComponent(workbook: WorkbookSnapshot): JComponent {
        if (!JBCefApp.isSupported()) {
            return JBLabel("The Excel editor requires a JetBrains Runtime with JCEF support.")
        }

        disposeBrowser()

        val nextBrowser = JBCefBrowser()
        val nextBridgeQuery = JBCefJSQuery.create(nextBrowser as JBCefBrowserBase)
        nextBridgeQuery.addHandler { payload ->
            handleBridgeMessage(payload)
            null
        }

        browser = nextBrowser
        bridgeQuery = nextBridgeQuery

        val html =
            webAppHtml()
                .replace("/*__INTELLIJ_BRIDGE__*/", bridgeScript(nextBridgeQuery))
                .replace("/*__INITIAL_WORKBOOK__*/", "window.initialWorkbook = ${workbook.toGridModel().toJson()};")
                .replace("/*__INITIAL_EDITABLE__*/", "window.initialEditable = $isEditable;")
                .replace("/*__INITIAL_THEME__*/", "window.initialTheme = ${currentTheme().toJson()};")

        nextBrowser.loadHTML(html, WEB_APP_URL)
        nextBrowser.setPageBackgroundColor(currentTheme().background)
        return nextBrowser.component
    }

    private fun handleBridgeMessage(payload: String) {
        val message =
            runCatching {
                JsonParser.parseString(payload).asJsonObject
            }.getOrNull() ?: return

        when (message["action"].asString) {
            "cellChanged" -> {
                document?.updateCell(
                    sheetIndex = message["sheetIndex"].asInt,
                    rowIndex = message["rowIndex"].asInt,
                    columnIndex = message["columnIndex"].asInt,
                    value = message["value"]?.asString.orEmpty(),
                )
                setModifiedOnEdt(true)
            }
            "insertRow" -> {
                document?.insertRow(
                    sheetIndex = message["sheetIndex"].asInt,
                    rowIndex = message["rowIndex"].asInt,
                )
                setModifiedAndReload()
            }
            "deleteRow" -> {
                document?.deleteRow(
                    sheetIndex = message["sheetIndex"].asInt,
                    rowIndex = message["rowIndex"].asInt,
                )
                setModifiedAndReload()
            }
            "insertColumn" -> {
                document?.insertColumn(
                    sheetIndex = message["sheetIndex"].asInt,
                    columnIndex = message["columnIndex"].asInt,
                )
                setModifiedAndReload()
            }
            "deleteColumn" -> {
                document?.deleteColumn(
                    sheetIndex = message["sheetIndex"].asInt,
                    columnIndex = message["columnIndex"].asInt,
                )
                setModifiedAndReload()
            }
            "save" -> saveWorkbook()
            "restore" -> restoreWorkbook()
        }
    }

    private fun setModifiedAndReload() {
        setModifiedOnEdt(true)
        SwingUtilities.invokeLater {
            sendWorkbookToBrowser()
        }
    }

    private fun saveWorkbook() {
        val bytes = document?.writeToBytes() ?: return
        ApplicationManager.getApplication().runWriteAction {
            file.setBinaryContent(bytes)
        }
        setModifiedOnEdt(false)
    }

    private fun restoreWorkbook() {
        loadWorkbook(recreateBrowser = false)
    }

    private fun sendWorkbookToBrowser() {
        val workbook = document?.snapshot ?: return
        browser?.runJavaScript(
            "window.loadWorkbook(${workbook.toGridModel().toJson()}, $isEditable);",
            WEB_APP_URL,
            0,
        )
    }

    private fun applyThemeToBrowser() {
        val theme = currentTheme()
        browser?.setPageBackgroundColor(theme.background)
        browser?.runJavaScript(
            "window.applyIntellijTheme(${theme.toJson()});",
            WEB_APP_URL,
            0,
        )
    }

    private fun setModifiedOnEdt(nextModified: Boolean) {
        if (SwingUtilities.isEventDispatchThread()) {
            setModified(nextModified)
        } else {
            SwingUtilities.invokeLater {
                setModified(nextModified)
            }
        }
    }

    private fun setModified(nextModified: Boolean) {
        if (modified == nextModified) {
            return
        }

        val previousModified = modified
        modified = nextModified
        browser?.runJavaScript(
            "window.setDirtyState($nextModified);",
            WEB_APP_URL,
            0,
        )
        propertyChangeSupport.firePropertyChange(
            PropertyChangeEvent(this, FileEditor.getPropModified(), previousModified, nextModified),
        )
    }

    private fun errorComponent(error: Throwable): JComponent =
        JBLabel("Unable to read workbook: ${error.message ?: error::class.simpleName}")

    private fun webAppHtml(): String =
        requireNotNull(javaClass.getResource("/web/excel-editor.html")) {
            "Missing bundled Excel editor web app"
        }.readText()

    private fun bridgeScript(query: JBCefJSQuery): String =
        """
        window.intellijBridge = function(message) {
          ${query.inject("JSON.stringify(message)")}
        };
        """.trimIndent()

    private fun WorkbookSnapshot.toGridModel(): GridWorkbook =
        GridWorkbook(
            sheets =
                sheets.mapIndexed { sheetIndex, sheet ->
                    sheet.toGridSheet(sheetIndex)
                },
        )

    private fun SheetSnapshot.toGridSheet(sheetIndex: Int): GridSheet =
        GridSheet(
            index = sheetIndex,
            name = name,
            columns =
                (0 until columnCount).map { columnIndex ->
                    GridColumn(
                        index = columnIndex,
                        field = "c$columnIndex",
                        headerName = cells[0 to columnIndex]?.takeIf { it.isNotBlank() } ?: columnName(columnIndex),
                    )
                },
            rows =
                (1 until rowCount).map { rowIndex ->
                    GridRow(
                        rowIndex = rowIndex,
                        values =
                            (0 until columnCount).associate { columnIndex ->
                                "c$columnIndex" to cells[rowIndex to columnIndex].orEmpty()
                            },
                    )
                },
        )

    private fun Any.toJson(): String = gson.toJson(this)

    private fun currentTheme(): IntellijTheme =
        IntellijTheme(
            background = UIUtil.getTableBackground().toCssColor(),
            foreground = UIUtil.getTableForeground().toCssColor(),
            panel = UIUtil.getPanelBackground().toCssColor(),
            panelForeground = UIUtil.getLabelForeground().toCssColor(),
            border = JBColor.border().toCssColor(),
            control = UIUtil.getControlColor().toCssColor(),
            textField = UIUtil.getTextFieldBackground().toCssColor(),
            textFieldForeground = UIUtil.getTextFieldForeground().toCssColor(),
            selection = UIUtil.getTableSelectionBackground(true).toCssColor(),
            selectionForeground = UIUtil.getTableSelectionForeground(true).toCssColor(),
            disabledForeground = UIUtil.getLabelDisabledForeground().toCssColor(),
            hover = JBColor.namedColor("List.hoverBackground", UIUtil.getTableBackground()).toCssColor(),
            accent = JBColor.namedColor("Component.focusColor", Color(53, 116, 240)).toCssColor(),
            header = JBColor.namedColor("TableHeader.background", UIUtil.getPanelBackground()).toCssColor(),
            headerForeground = JBColor.namedColor("TableHeader.foreground", UIUtil.getLabelForeground()).toCssColor(),
        )

    private fun Color.toCssColor(): String = "#%02x%02x%02x".format(red, green, blue)

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

    private val isEditable: Boolean
        get() = file.extension.equals("xlsx", ignoreCase = true)

    private fun disposeBrowser() {
        bridgeQuery?.let(Disposer::dispose)
        bridgeQuery = null
        browser?.let(Disposer::dispose)
        browser = null
    }

    companion object {
        private const val WEB_APP_URL = "https://excel-editor.local/index.html"
    }
}

private data class GridWorkbook(
    val sheets: List<GridSheet>,
)

private data class GridSheet(
    val index: Int,
    val name: String,
    val columns: List<GridColumn>,
    val rows: List<GridRow>,
)

private data class GridColumn(
    val index: Int,
    val field: String,
    val headerName: String,
)

private data class GridRow(
    val rowIndex: Int,
    val values: Map<String, String>,
)

private data class IntellijTheme(
    val background: String,
    val foreground: String,
    val panel: String,
    val panelForeground: String,
    val border: String,
    val control: String,
    val textField: String,
    val textFieldForeground: String,
    val selection: String,
    val selectionForeground: String,
    val disabledForeground: String,
    val hover: String,
    val accent: String,
    val header: String,
    val headerForeground: String,
)

package io.github.mnaami.environmentswitcher.ui

import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import io.github.mnaami.environmentswitcher.EnvSwitcherBundle
import java.awt.Component
import javax.swing.DefaultCellEditor
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

private const val COL_KEY = 0
private const val COL_VALUE = 1
private const val COL_SECRET = 2

/** Key / value (/ secret) table editing a list of [VariableRow] in place. */
class VariablesTablePanel(
    allowSecrets: Boolean,
) {
    /** Whether the Secret column is shown; off when the project stores everything in plain text. */
    var allowSecrets: Boolean = allowSecrets
        set(value) {
            if (field == value) return
            field = value
            model.fireTableStructureChanged()
            applyColumnWidths()
        }

    /** Called with a freshly typed variable name; true marks the row secret (auto-mark policy). */
    var autoMarkSecret: (String) -> Boolean = { false }

    private var rows: MutableList<VariableRow> = ArrayList()
    private val model = Model()
    private val table =
        object : JBTable(model) {
            override fun getCellRenderer(
                row: Int,
                column: Int,
            ): TableCellRenderer =
                when {
                    column == COL_SECRET -> BooleanTableCellRenderer()
                    column == COL_VALUE && rows[row].secret -> SecretRenderer
                    else -> super.getCellRenderer(row, column)
                }

            override fun getCellEditor(
                row: Int,
                column: Int,
            ): TableCellEditor =
                when {
                    column == COL_SECRET -> BooleanTableCellEditor()
                    column == COL_VALUE && rows[row].secret -> DefaultCellEditor(JPasswordField())
                    else -> super.getCellEditor(row, column)
                }
        }

    val component: JComponent =
        ToolbarDecorator
            .createDecorator(table)
            .setAddAction {
                stopEditing()
                rows += VariableRow()
                model.fireTableRowsInserted(rows.size - 1, rows.size - 1)
                table.editCellAt(rows.size - 1, COL_KEY)
                table.editorComponent?.requestFocus()
            }.setRemoveAction {
                stopEditing()
                table.selectedRows.sortedDescending().forEach { rows.removeAt(it) }
                model.fireTableDataChanged()
            }.disableUpDownActions()
            .createPanel()

    init {
        table.emptyText.text = EnvSwitcherBundle.message("table.empty")
        table.setShowGrid(false)
        table.putClientProperty("terminateEditOnFocusLost", true) // commit the cell when the user clicks OK/Apply
        applyColumnWidths()
    }

    private fun applyColumnWidths() {
        table.columnModel.getColumn(COL_KEY).preferredWidth = 220
        table.columnModel.getColumn(COL_VALUE).preferredWidth = 420
        if (allowSecrets && table.columnModel.columnCount > COL_SECRET) table.columnModel.getColumn(COL_SECRET).maxWidth = 70
    }

    fun bind(rows: MutableList<VariableRow>) {
        stopEditing()
        this.rows = rows
        model.fireTableDataChanged()
    }

    /** True while a cell editor is open; the pending text is not yet in the row. */
    val isEditing: Boolean get() = table.isEditing

    fun stopEditing() {
        if (table.isEditing) table.cellEditor.stopCellEditing()
    }

    private inner class Model : AbstractTableModel() {
        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = if (allowSecrets) 3 else 2

        override fun getColumnName(column: Int): String =
            when (column) {
                COL_KEY -> EnvSwitcherBundle.message("table.column.name")
                COL_VALUE -> EnvSwitcherBundle.message("table.column.value")
                else -> EnvSwitcherBundle.message("table.column.secret")
            }

        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex ==
                COL_SECRET
            ) {
                java.lang.Boolean::class.java
            } else {
                String::class.java
            }

        override fun isCellEditable(
            rowIndex: Int,
            columnIndex: Int,
        ): Boolean = true

        override fun getValueAt(
            rowIndex: Int,
            columnIndex: Int,
        ): Any? {
            val row = rows[rowIndex]
            return when (columnIndex) {
                COL_KEY -> row.key
                COL_VALUE -> if (row.secret) SecretDisplay(row) else row.value
                else -> row.secret
            }
        }

        override fun setValueAt(
            aValue: Any?,
            rowIndex: Int,
            columnIndex: Int,
        ) {
            val row = rows[rowIndex]
            when (columnIndex) {
                COL_KEY -> {
                    val newKey = (aValue as? String).orEmpty().trim()
                    val renamed = newKey != row.key
                    row.key = newKey
                    if (renamed && allowSecrets && !row.secret && !row.stored && autoMarkSecret(newKey)) {
                        row.secret = true
                        row.value = row.value?.takeIf { it.isNotEmpty() }
                    }
                }
                COL_VALUE -> row.value = (aValue as? String).orEmpty()
                COL_SECRET -> {
                    val secret = aValue == true
                    if (secret != row.secret) {
                        row.secret = secret
                        if (secret) row.value = row.value?.takeIf { it.isNotEmpty() } else row.value = row.value ?: ""
                        row.stored = false
                    }
                }
            }
            fireTableRowsUpdated(rowIndex, rowIndex)
        }
    }

    private class SecretDisplay(
        val row: VariableRow,
    )

    private object SecretRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val display = value as? SecretDisplay
            val text =
                when {
                    display == null -> ""
                    display.row.value != null -> "••••••••  " + EnvSwitcherBundle.message("table.secret.pending")
                    display.row.stored -> "••••••••"
                    else -> EnvSwitcherBundle.message("table.secret.notSet")
                }
            return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
        }
    }
}

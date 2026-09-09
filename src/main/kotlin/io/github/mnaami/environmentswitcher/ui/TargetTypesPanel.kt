package io.github.mnaami.environmentswitcher.ui

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.mnaami.environmentswitcher.EnvSwitcherBundle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer

/** Checklist of run configuration types that receive the environment variables, with each type's icon. */
class TargetTypesPanel {
    private class Row(
        val id: String,
        val name: String,
        val icon: Icon,
        val installed: Boolean,
        var enabled: Boolean,
    )

    private var rows: List<Row> = emptyList()
    private val model = Model()
    private val table =
        JBTable(model).apply {
            setShowGrid(false)
            tableHeader = null
            rowHeight = JBUI.scale(22)
            columnModel.getColumn(0).apply {
                maxWidth = JBUI.scale(28)
                cellRenderer = BooleanTableCellRenderer()
                cellEditor = BooleanTableCellEditor()
            }
            columnModel.getColumn(1).cellRenderer = TypeRenderer()
            emptyText.text = EnvSwitcherBundle.message("settings.targets.empty")
        }

    val component: JComponent = ScrollPaneFactory.createScrollPane(table)

    val selectedIds: Set<String>
        get() {
            if (table.isEditing) table.cellEditor.stopCellEditing()
            return rows.filter { it.enabled }.map { it.id }.toSet()
        }

    fun bind(selected: Collection<String>) {
        val known = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.associateBy { it.id }
        rows =
            (known.keys + selected)
                .distinct()
                .map { id ->
                    val type = known[id]
                    Row(
                        id = id,
                        name = type?.displayName ?: id,
                        icon = type?.icon ?: AllIcons.RunConfigurations.Application,
                        installed = type != null,
                        enabled = id in selected,
                    )
                }.sortedWith(compareByDescending<Row> { it.enabled }.thenBy { it.name.lowercase() })
        model.fireTableDataChanged()
    }

    private inner class Model : AbstractTableModel() {
        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = 2

        override fun getColumnClass(columnIndex: Int): Class<*> = if (columnIndex == 0) java.lang.Boolean::class.java else Row::class.java

        override fun isCellEditable(
            rowIndex: Int,
            columnIndex: Int,
        ): Boolean = columnIndex == 0

        override fun getValueAt(
            rowIndex: Int,
            columnIndex: Int,
        ): Any = if (columnIndex == 0) rows[rowIndex].enabled else rows[rowIndex]

        override fun setValueAt(
            aValue: Any?,
            rowIndex: Int,
            columnIndex: Int,
        ) {
            if (columnIndex == 0) rows[rowIndex].enabled = aValue == true
            fireTableRowsUpdated(rowIndex, rowIndex)
        }
    }

    private class TypeRenderer :
        com.intellij.ui.ColoredTableCellRenderer(),
        TableCellRenderer {
        override fun customizeCellRenderer(
            table: JTable,
            value: Any?,
            selected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ) {
            val type = value as? Row ?: return
            icon = type.icon
            append(type.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append("  ${type.id}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            if (!type.installed) {
                append(
                    "  (${EnvSwitcherBundle.message("settings.targets.unknown")})",
                    SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES,
                )
            }
        }
    }
}

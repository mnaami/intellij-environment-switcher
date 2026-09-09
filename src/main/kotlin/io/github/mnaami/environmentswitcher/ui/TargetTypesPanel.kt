package io.github.mnaami.environmentswitcher.ui

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ScrollPaneFactory
import io.github.mnaami.environmentswitcher.EnvSwitcherBundle
import javax.swing.JComponent

/** Checklist of run configuration types that receive the environment variables. */
class TargetTypesPanel {
    private val checkList = CheckBoxList<String>()
    val component: JComponent = ScrollPaneFactory.createScrollPane(checkList)

    val selectedIds: Set<String>
        get() = (0 until checkList.itemsCount).mapNotNull { i -> checkList.getItemAt(i)?.takeIf { checkList.isItemSelected(i) } }.toSet()

    fun bind(selected: Collection<String>) {
        checkList.clear()
        val known = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.associateBy { it.id }
        val ids = (known.keys + selected).distinct().sortedBy { known[it]?.displayName ?: it }
        for (id in ids) {
            val label = known[id]?.let { "${it.displayName}  ($id)" } ?: "$id  (${EnvSwitcherBundle.message("settings.targets.unknown")})"
            checkList.addItem(id, label, id in selected)
        }
    }
}

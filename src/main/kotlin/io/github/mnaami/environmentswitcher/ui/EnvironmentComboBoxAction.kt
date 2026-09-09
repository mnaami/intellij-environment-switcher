package io.github.mnaami.environmentswitcher.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import io.github.mnaami.environmentswitcher.EnvSwitcherBundle
import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.state.EnvironmentsService
import io.github.mnaami.environmentswitcher.state.SelectedEnvironmentService
import io.github.mnaami.environmentswitcher.state.ToolbarSettings
import javax.swing.Icon
import javax.swing.JComponent

/** The environment dropdown shown next to the run configuration selector. */
class EnvironmentComboBoxAction :
    ComboBoxAction(),
    DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation
        if (project == null || project.isDisposed) {
            presentation.isEnabledAndVisible = false
            return
        }
        val environments = EnvironmentsService.getInstance(project).state.environments
        val selected = SelectedEnvironmentService.getInstance(project).environmentName
        val prefs = ToolbarSettings.getInstance().state
        presentation.isEnabledAndVisible = true
        presentation.text =
            EnvironmentMenuModel.buttonText(
                environments,
                selected,
                EnvSwitcherBundle.message("toolbar.noEnvironments"),
                EnvSwitcherBundle.message("toolbar.select"),
            ) { if (prefs.showPrefix && prefs.prefix.isNotBlank()) "${prefs.prefix.trim()} $it" else it }
        presentation.icon = if (prefs.showColorDot) environments.firstOrNull { it.name == selected }?.let { dot(it.color) } else null
        presentation.description = EnvSwitcherBundle.message("toolbar.description")
    }

    override fun createPopupActionGroup(
        button: JComponent,
        dataContext: DataContext,
    ): DefaultActionGroup {
        val group = DefaultActionGroup()
        val project = dataContext.getData(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT) ?: return group
        val environments = EnvironmentsService.getInstance(project).state.environments
        val selected = SelectedEnvironmentService.getInstance(project).environmentName
        for (item in EnvironmentMenuModel.items(environments, selected)) {
            when (item) {
                is EnvironmentMenuModel.Item.Env -> group.add(SelectAction(project, item))
                EnvironmentMenuModel.Item.Separator -> group.addSeparator()
                EnvironmentMenuModel.Item.Edit -> group.add(EditAction(project))
            }
        }
        return group
    }

    private class SelectAction(
        private val project: Project,
        item: EnvironmentMenuModel.Item.Env,
    ) : AnAction(item.name, null, dot(item.color)),
        DumbAware {
        private val name = item.name

        override fun actionPerformed(e: AnActionEvent) {
            SelectedEnvironmentService.getInstance(project).environmentName = name
        }
    }

    private class EditAction(
        private val project: Project,
    ) : AnAction(EnvSwitcherBundle.message("toolbar.configure")),
        DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            EditEnvironmentsDialog(project).show()
        }
    }

    companion object {
        fun dot(hex: String): Icon {
            val color = runCatching { ColorUtil.fromHex(hex) }.getOrElse { ColorUtil.fromHex(Environment.DEFAULT_COLOR) }
            return JBUIScale.scaleIcon(ColorIcon(10, color))
        }
    }
}

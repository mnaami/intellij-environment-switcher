package io.github.mnaami.environmentswitcher.ui

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import io.github.mnaami.environmentswitcher.EnvSwitcherBundle
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

/** "Edit Environments…" dialog, the environment counterpart of Run | Edit Configurations…. */
class EditEnvironmentsDialog(
    private val project: Project,
) : DialogWrapper(project, true) {
    private val configurable = EnvironmentsConfigurable(project)
    private val content: JComponent = configurable.createComponent()

    private val applyAction =
        object : AbstractAction(EnvSwitcherBundle.message("dialog.apply")) {
            override fun actionPerformed(e: ActionEvent) {
                if (applyChanges()) isEnabled = false
            }
        }

    init {
        title = EnvSwitcherBundle.message("dialog.title")
        configurable.reset()
        setOKButtonText(EnvSwitcherBundle.message("dialog.ok"))
        init()
        content.preferredSize = Dimension(950, 620)
        applyAction.isEnabled = false
        startModificationTracking()
    }

    override fun createCenterPanel(): JComponent = content

    override fun createActions(): Array<Action> = arrayOf(okAction, cancelAction, applyAction)

    override fun getDimensionServiceKey(): String = "io.github.mnaami.environmentswitcher.EditEnvironmentsDialog"

    override fun doOKAction() {
        if (!configurable.isModified || applyChanges()) super.doOKAction()
    }

    override fun dispose() {
        configurable.disposeUIResources()
        super.dispose()
    }

    private fun applyChanges(): Boolean =
        try {
            configurable.apply()
            true
        } catch (e: ConfigurationException) {
            Messages.showErrorDialog(project, e.messageHtml.toString(), title)
            false
        }

    /** Cheap polling keeps Apply in sync without wiring listeners into every table. */
    private fun startModificationTracking() {
        val timer = javax.swing.Timer(400) { applyAction.isEnabled = configurable.isModified }
        timer.isRepeats = true
        timer.start()
        com.intellij.openapi.util.Disposer
            .register(disposable) { timer.stop() }
    }
}

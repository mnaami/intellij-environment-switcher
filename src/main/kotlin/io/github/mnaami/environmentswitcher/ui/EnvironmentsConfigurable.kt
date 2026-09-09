package io.github.mnaami.environmentswitcher.ui

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ColorPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.TitledSeparator
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.mnaami.environmentswitcher.EnvSwitcherBundle
import io.github.mnaami.environmentswitcher.importer.EnvFolderImporter
import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.state.EnvironmentsService
import io.github.mnaami.environmentswitcher.state.PasswordSafeSecretStore
import io.github.mnaami.environmentswitcher.state.SelectedEnvironmentService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Settings | Tools | Environment Switcher. */
class EnvironmentsConfigurable(
    private val project: Project,
) : SearchableConfigurable {
    private val service get() = EnvironmentsService.getInstance(project)
    private val secrets get() = PasswordSafeSecretStore.getInstance(project)

    private var model = SettingsModel()
    private var snapshot = SettingsModel()

    private val environmentsTab = EnvironmentsTab()
    private val commonTable = VariablesTablePanel(allowSecrets = false)
    private val overridesTab = OverridesTab()
    private val targetsTab = TargetsTab()
    private var root: JComponent? = null

    override fun getId(): String = ID

    override fun getDisplayName(): String = EnvSwitcherBundle.message("settings.title")

    override fun createComponent(): JComponent {
        val tabs = JBTabbedPane()
        tabs.addTab(EnvSwitcherBundle.message("settings.tab.environments"), padded(environmentsTab.component))
        tabs.addTab(EnvSwitcherBundle.message("settings.tab.common"), padded(commonTable.component))
        tabs.addTab(EnvSwitcherBundle.message("settings.tab.overrides"), padded(overridesTab.component))
        tabs.addTab(EnvSwitcherBundle.message("settings.tab.targets"), padded(targetsTab.component))

        val importButton = JButton(EnvSwitcherBundle.message("settings.import")).apply { addActionListener { importFolder() } }
        val top =
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(importButton)
                add(JBLabel(EnvSwitcherBundle.message("settings.import.hint")).apply { border = JBUI.Borders.emptyLeft(8) })
            }
        return JPanel(BorderLayout(0, JBUI.scale(8)))
            .apply {
                add(top, BorderLayout.NORTH)
                add(tabs, BorderLayout.CENTER)
            }.also { root = it }
    }

    /** Must not touch cell editors: it is polled while the user is typing. */
    override fun isModified(): Boolean = model != snapshot || hasActiveEditor()

    private fun hasActiveEditor(): Boolean = environmentsTab.isEditing() || commonTable.isEditing || overridesTab.isEditing()

    override fun apply() {
        stopEditing()
        val problems = model.validate()
        if (problems.isNotEmpty()) throw ConfigurationException(problems.joinToString("\n"))
        val previousSelection = SelectedEnvironmentService.getInstance(project).environmentName
        service.update { model.applyTo(this, secrets) }
        if (previousSelection != null && service.state.environment(previousSelection) == null) {
            SelectedEnvironmentService.getInstance(project).environmentName = null
        }
        snapshot = model.deepCopy()
    }

    override fun reset() {
        model = SettingsModel.from(service.state, secrets)
        snapshot = model.deepCopy()
        environmentsTab.bind(model)
        commonTable.bind(model.common)
        overridesTab.bind(model)
        targetsTab.bind(model)
    }

    override fun disposeUIResources() {
        root = null
    }

    private fun stopEditing() {
        environmentsTab.stopEditing()
        commonTable.stopEditing()
        overridesTab.stopEditing()
    }

    private fun importFolder() {
        val descriptor =
            FileChooserDescriptorFactory
                .createSingleFolderDescriptor()
                .withTitle(EnvSwitcherBundle.message("settings.import.chooser"))
        val dir = FileChooser.chooseFile(descriptor, project, project.guessProjectDir()) ?: return
        val plan = EnvFolderImporter().scan(File(dir.path))
        if (plan.isEmpty) {
            Messages.showWarningDialog(project, EnvSwitcherBundle.message("settings.import.nothing", dir.path), displayName)
            return
        }
        val answer =
            Messages.showYesNoDialog(
                project,
                EnvSwitcherBundle.message(
                    "settings.import.confirm",
                    plan.environments.size,
                    plan.environments.joinToString(", ") { it.name },
                    plan.overrides.size,
                    plan.secrets.values.sumOf { it.size },
                ),
                displayName,
                Messages.getQuestionIcon(),
            )
        if (answer != Messages.YES) return
        stopEditing()
        model.importPlan(plan)
        environmentsTab.bind(model)
        commonTable.bind(model.common)
        overridesTab.bind(model)
    }

    // ---------------------------------------------------------------- tabs

    private inner class EnvironmentsTab {
        private val listModel = DefaultListModel<EnvironmentDraft>()
        private val list =
            JBList(listModel).apply {
                cellRenderer =
                    com.intellij.ui.SimpleListCellRenderer.create { label, draft, _ ->
                        label.text = draft.name.ifBlank { "<unnamed>" }
                        label.icon = EnvironmentComboBoxAction.dot(draft.color)
                    }
            }
        private val name = JBTextField()
        private val color = ColorPanel()
        private val confirm = JBCheckBox(EnvSwitcherBundle.message("settings.env.confirm"))
        private val table = VariablesTablePanel(allowSecrets = true)
        private var current: EnvironmentDraft? = null
        private var updating = false

        val component: JComponent

        init {
            val listPanel =
                ToolbarDecorator
                    .createDecorator(list)
                    .setAddAction {
                        val draft = EnvironmentDraft(uniqueName(), nextColor())
                        model.environments += draft
                        listModel.addElement(draft)
                        list.setSelectedValue(draft, true)
                    }.setRemoveAction {
                        val draft = list.selectedValue ?: return@setRemoveAction
                        model.environments -= draft
                        listModel.removeElement(draft)
                    }.setMoveUpAction { move(-1) }
                    .setMoveDownAction { move(1) }
                    .addExtraAction(
                        object : DumbAwareAction(
                            EnvSwitcherBundle.message("settings.env.duplicate"),
                            EnvSwitcherBundle.message("settings.env.duplicate.description"),
                            AllIcons.Actions.Copy,
                        ) {
                            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                            override fun update(e: AnActionEvent) {
                                e.presentation.isEnabled = list.selectedValue != null
                            }

                            override fun actionPerformed(e: AnActionEvent) {
                                val source = list.selectedValue ?: return
                                stopEditing()
                                val copy = duplicate(source)
                                val index = list.selectedIndex + 1
                                model.environments.add(index, copy)
                                listModel.add(index, copy)
                                list.setSelectedValue(copy, true)
                                name.requestFocusInWindow()
                                name.selectAll()
                            }
                        },
                    ).createPanel()
            list.emptyText.text = EnvSwitcherBundle.message("settings.env.empty")
            disableHorizontalScroll(listPanel)

            name.document.addDocumentListener(
                object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent) = onNameChanged()

                    override fun removeUpdate(e: DocumentEvent) = onNameChanged()

                    override fun changedUpdate(e: DocumentEvent) = onNameChanged()
                },
            )
            color.addActionListener {
                if (!updating) current?.color = "#" + ColorUtil.toHex(color.selectedColor ?: return@addActionListener)
                list.repaint()
            }
            confirm.addActionListener { if (!updating) current?.confirmBeforeRun = confirm.isSelected }

            val form =
                FormBuilder
                    .createFormBuilder()
                    .addLabeledComponent(EnvSwitcherBundle.message("settings.env.name"), name)
                    .addLabeledComponent(
                        EnvSwitcherBundle.message("settings.env.color"),
                        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(color) },
                    ).addComponent(confirm)
                    .addComponent(TitledSeparator(EnvSwitcherBundle.message("settings.env.variables")))
                    .panel
            val right =
                JPanel(BorderLayout(0, JBUI.scale(4))).apply {
                    border = JBUI.Borders.emptyLeft(UIUtil.DEFAULT_HGAP * 2)
                    add(form, BorderLayout.NORTH)
                    add(table.component, BorderLayout.CENTER)
                }
            list.addListSelectionListener { if (!it.valueIsAdjusting) show(list.selectedValue) }
            component =
                OnePixelSplitter(false, "EnvironmentSwitcher.environments.splitter", 0.24f).apply {
                    firstComponent = listPanel
                    secondComponent = right
                }
            show(null)
        }

        fun bind(model: SettingsModel) {
            stopEditing()
            listModel.clear()
            model.environments.forEach(listModel::addElement)
            if (!listModel.isEmpty) list.selectedIndex = 0 else show(null)
        }

        fun stopEditing() = table.stopEditing()

        fun isEditing(): Boolean = table.isEditing

        private fun show(draft: EnvironmentDraft?) {
            stopEditing()
            updating = true
            current = draft
            val enabled = draft != null
            name.isEnabled = enabled
            color.isEnabled = enabled
            confirm.isEnabled = enabled
            name.text = draft?.name.orEmpty()
            color.selectedColor = runCatching { ColorUtil.fromHex(draft?.color ?: Environment.DEFAULT_COLOR) }.getOrNull()
            confirm.isSelected = draft?.confirmBeforeRun == true
            table.bind(draft?.rows ?: ArrayList())
            updating = false
        }

        private fun onNameChanged() {
            if (updating) return
            current?.name = name.text
            list.repaint()
        }

        private fun move(delta: Int) {
            val index = list.selectedIndex
            val target = index + delta
            if (index < 0 || target !in 0 until listModel.size) return
            val draft = listModel.remove(index)
            listModel.add(target, draft)
            model.environments.removeAt(index)
            model.environments.add(target, draft)
            list.selectedIndex = target
        }

        private fun uniqueName(): String {
            val existing = model.environments.map { it.name }.toSet()
            var i = 1
            while ("env$i" in existing) i++
            return "env$i"
        }

        /**
         * Copies name (suffixed), colour, flag and all rows. Stored secret values are
         * carried over as pending values so the copy is complete once applied.
         */
        private fun duplicate(source: EnvironmentDraft): EnvironmentDraft {
            val existing = model.environments.map { it.name }.toSet()
            var candidate = "${source.name} copy"
            var i = 2
            while (candidate in existing) candidate = "${source.name} copy $i".also { i++ }
            val rows =
                source.rows.map { row ->
                    val pending =
                        when {
                            !row.secret -> row.value
                            row.value != null -> row.value
                            row.stored -> secrets.get(source.name.trim(), row.key.trim())
                            else -> null
                        }
                    VariableRow(row.key, pending, row.secret, stored = false)
                }
            return EnvironmentDraft(candidate, source.color, source.confirmBeforeRun, rows.toMutableList())
        }

        private fun nextColor(): String = PALETTE[model.environments.size % PALETTE.size]
    }

    private inner class OverridesTab {
        private val listModel = DefaultListModel<OverrideDraft>()
        private val list =
            JBList(listModel).apply {
                cellRenderer =
                    com.intellij.ui.SimpleListCellRenderer
                        .create("") { it.moduleName }
            }
        private val table = VariablesTablePanel(allowSecrets = false)
        val component: JComponent

        init {
            val listPanel =
                ToolbarDecorator
                    .createDecorator(list)
                    .setAddAction {
                        val modules =
                            ModuleManager
                                .getInstance(project)
                                .modules
                                .map { it.name }
                                .sorted()
                                .toTypedArray()
                        val chosen =
                            Messages
                                .showEditableChooseDialog(
                                    EnvSwitcherBundle.message("settings.override.prompt"),
                                    EnvSwitcherBundle.message("settings.tab.overrides"),
                                    null,
                                    modules,
                                    modules.firstOrNull() ?: "",
                                    null,
                                )?.trim()
                        if (chosen.isNullOrEmpty()) return@setAddAction
                        val draft =
                            model.overrides.firstOrNull { it.moduleName == chosen } ?: OverrideDraft(chosen).also {
                                model.overrides += it
                                listModel.addElement(it)
                            }
                        list.setSelectedValue(draft, true)
                    }.setRemoveAction {
                        val draft = list.selectedValue ?: return@setRemoveAction
                        model.overrides -= draft
                        listModel.removeElement(draft)
                    }.disableUpDownActions()
                    .createPanel()
            list.emptyText.text = EnvSwitcherBundle.message("settings.override.empty")
            disableHorizontalScroll(listPanel)
            list.addListSelectionListener {
                if (!it.valueIsAdjusting) {
                    table.stopEditing()
                    table.bind(list.selectedValue?.rows ?: ArrayList())
                }
            }
            val right =
                JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.emptyLeft(UIUtil.DEFAULT_HGAP * 2)
                    add(table.component, BorderLayout.CENTER)
                }
            component =
                OnePixelSplitter(false, "EnvironmentSwitcher.overrides.splitter", 0.28f).apply {
                    firstComponent = listPanel
                    secondComponent = right
                }
        }

        fun bind(model: SettingsModel) {
            stopEditing()
            listModel.clear()
            model.overrides.forEach(listModel::addElement)
            if (!listModel.isEmpty) list.selectedIndex = 0 else table.bind(ArrayList())
        }

        fun stopEditing() = table.stopEditing()

        fun isEditing(): Boolean = table.isEditing
    }

    private inner class TargetsTab {
        private val checkList = CheckBoxList<String>()
        val component: JComponent =
            JPanel(BorderLayout(0, JBUI.scale(6))).apply {
                add(JBLabel(EnvSwitcherBundle.message("settings.targets.hint")), BorderLayout.NORTH)
                add(
                    com.intellij.ui.ScrollPaneFactory
                        .createScrollPane(checkList),
                    BorderLayout.CENTER,
                )
            }

        init {
            checkList.setCheckBoxListListener { index, checked ->
                val id = checkList.getItemAt(index) ?: return@setCheckBoxListListener
                if (checked) model.targetConfigTypeIds += id else model.targetConfigTypeIds -= id
            }
        }

        fun bind(model: SettingsModel) {
            checkList.clear()
            val known = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.associateBy { it.id }
            val ids = (known.keys + model.targetConfigTypeIds).distinct().sortedBy { known[it]?.displayName ?: it }
            for (id in ids) {
                val label =
                    known[id]?.let { "${it.displayName}  ($id)" } ?: "$id  (${EnvSwitcherBundle.message("settings.targets.unknown")})"
                checkList.addItem(id, label, id in model.targetConfigTypeIds)
            }
        }
    }

    companion object {
        const val ID = "io.github.mnaami.environmentswitcher.settings"

        /** Standard dialog inset around each tab's content (tabs themselves add none). */
        private fun padded(content: JComponent): JComponent =
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP * 2, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP)
                add(content, BorderLayout.CENTER)
            }

        /** Lists never need horizontal scrolling; the decorator's scroll pane shows a bar otherwise. */
        private fun disableHorizontalScroll(panel: JComponent) {
            UIUtil.findComponentOfType(panel, JScrollPane::class.java)?.horizontalScrollBarPolicy =
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        private val PALETTE = listOf("#4C9AFF", "#36B37E", "#FFAB00", "#FF5630", "#6554C0", "#00B8D9")
    }
}

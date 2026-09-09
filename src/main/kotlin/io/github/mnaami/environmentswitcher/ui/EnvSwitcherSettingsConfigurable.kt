package io.github.mnaami.environmentswitcher.ui

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.ui.JBUI
import io.github.mnaami.environmentswitcher.EnvSwitcherBundle
import io.github.mnaami.environmentswitcher.model.ConfirmScope
import io.github.mnaami.environmentswitcher.model.MissingSecretPolicy
import io.github.mnaami.environmentswitcher.model.SecretStorage
import io.github.mnaami.environmentswitcher.state.DEFAULT_PREFIX
import io.github.mnaami.environmentswitcher.state.EnvironmentsService
import io.github.mnaami.environmentswitcher.state.ToolbarSettings
import javax.swing.JComponent

/**
 * Settings | Tools | Environment Switcher: behaviour, not data.
 * Team policies live in the shared project state; toolbar preferences are per user.
 */
class EnvSwitcherSettingsConfigurable(
    private val project: Project,
) : BoundSearchableConfigurable(EnvSwitcherBundle.message("settings.title"), "environment.switcher", ID) {
    private val state get() = EnvironmentsService.getInstance(project).state
    private val toolbar get() = ToolbarSettings.getInstance().state
    private val targets = TargetTypesPanel()

    override fun createPanel() =
        panel {
            group(EnvSwitcherBundle.message("settings.group.injection")) {
                row {
                    label(EnvSwitcherBundle.message("settings.targets.hint"))
                }
                row {
                    cell(targets.component)
                        .align(Align.FILL)
                        .applyToComponent { preferredSize = JBUI.size(500, 160) }
                        .onIsModified { targets.selectedIds != state.targetConfigTypeIds.toSet() }
                        .onApply { state.targetConfigTypeIds = targets.selectedIds.toMutableList() }
                        .onReset { targets.bind(state.targetConfigTypeIds) }
                }.resizableRow()
                buttonsGroup(EnvSwitcherBundle.message("settings.missingSecret")) {
                    row {
                        radioButton(EnvSwitcherBundle.message("settings.missingSecret.warn"), MissingSecretPolicy.WARN)
                    }
                    row {
                        radioButton(EnvSwitcherBundle.message("settings.missingSecret.block"), MissingSecretPolicy.BLOCK)
                    }
                }.bind(MutableProperty({ state.missingSecretPolicy }, { state.missingSecretPolicy = it }), MissingSecretPolicy::class.java)
                buttonsGroup(EnvSwitcherBundle.message("settings.confirmScope")) {
                    row {
                        radioButton(EnvSwitcherBundle.message("settings.confirmScope.session"), ConfirmScope.ONCE_PER_SESSION)
                    }
                    row {
                        radioButton(EnvSwitcherBundle.message("settings.confirmScope.every"), ConfirmScope.EVERY_RUN)
                    }
                }.bind(MutableProperty({ state.confirmScope }, { state.confirmScope = it }), ConfirmScope::class.java)
                row {
                    checkBox(EnvSwitcherBundle.message("settings.expandVariables"))
                        .comment(EnvSwitcherBundle.message("settings.expandVariables.comment"))
                        .bindSelected({ state.expandVariables }, { state.expandVariables = it })
                }
            }
            group(EnvSwitcherBundle.message("settings.group.secrets")) {
                buttonsGroup(EnvSwitcherBundle.message("settings.secretStorage")) {
                    row {
                        radioButton(EnvSwitcherBundle.message("settings.secretStorage.safe"), SecretStorage.PASSWORD_SAFE)
                            .comment(EnvSwitcherBundle.message("settings.secretStorage.safe.comment"))
                    }
                    row {
                        radioButton(EnvSwitcherBundle.message("settings.secretStorage.file"), SecretStorage.PROJECT_FILE)
                            .comment(EnvSwitcherBundle.message("settings.secretStorage.file.comment"))
                    }
                }.bind(MutableProperty({ state.secretStorage }, { state.secretStorage = it }), SecretStorage::class.java)
                row {
                    checkBox(EnvSwitcherBundle.message("settings.autoMarkSecrets"))
                        .comment(EnvSwitcherBundle.message("settings.autoMarkSecrets.comment"))
                        .bindSelected({ state.autoMarkSecrets }, { state.autoMarkSecrets = it })
                }
            }
            group(EnvSwitcherBundle.message("settings.group.toolbar")) {
                lateinit var prefixEnabled: com.intellij.ui.dsl.builder.Cell<javax.swing.JCheckBox>
                row {
                    prefixEnabled =
                        checkBox(EnvSwitcherBundle.message("settings.toolbar.prefix"))
                            .bindSelected({ toolbar.showPrefix }, { toolbar.showPrefix = it })
                    textField()
                        .bindText({ toolbar.prefix }, { toolbar.prefix = it })
                        .columns(12)
                        .enabledIf(prefixEnabled.selected)
                        .comment(EnvSwitcherBundle.message("settings.toolbar.prefix.comment", DEFAULT_PREFIX))
                }
                row {
                    checkBox(EnvSwitcherBundle.message("settings.toolbar.dot"))
                        .bindSelected({ toolbar.showColorDot }, { toolbar.showColorDot = it })
                }
            }
        }

    override fun getPreferredFocusedComponent(): JComponent = targets.component

    companion object {
        const val ID = "io.github.mnaami.environmentswitcher.settings"
    }
}

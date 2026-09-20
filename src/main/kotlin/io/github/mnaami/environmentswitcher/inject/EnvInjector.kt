package io.github.mnaami.environmentswitcher.inject

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.github.mnaami.environmentswitcher.EnvSwitcherBundle
import io.github.mnaami.environmentswitcher.model.ConfirmScope
import io.github.mnaami.environmentswitcher.model.MissingSecretPolicy
import io.github.mnaami.environmentswitcher.resolve.Resolution
import io.github.mnaami.environmentswitcher.resolve.VariableResolver
import io.github.mnaami.environmentswitcher.state.EnvironmentsService
import io.github.mnaami.environmentswitcher.state.SecretStore
import io.github.mnaami.environmentswitcher.state.SelectedEnvironmentService
import java.util.concurrent.ConcurrentHashMap

/**
 * Language-agnostic core of the injection: resolves the selected environment and
 * writes it into a run configuration's environment map. Touches no language
 * plugin API, so every per-language entry point can share it.
 */
object EnvInjector {
    const val NOTIFICATION_GROUP = "Environment Switcher"

    private val confirmedThisSession = ConcurrentHashMap<String, Boolean>()

    /**
     * Returns null when nothing applies: the configuration type is not enabled,
     * or no environment is selected. Throws [ExecutionException] to abort the
     * launch when a secret is missing under BLOCK, or the user declines.
     */
    fun inject(
        project: Project,
        configuration: RunConfiguration,
        env: MutableMap<String, String>,
        secrets: SecretStore,
    ): Resolution? {
        val state = EnvironmentsService.getInstance(project).state
        if (configuration.type.id !in state.targetConfigTypeIds) return null
        val envName = SelectedEnvironmentService.getInstance(project).environmentName ?: return null
        val moduleName = (configuration as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module?.name
        val resolution = VariableResolver(state, secrets).resolve(envName, moduleName)

        if (resolution.missingEnvironment != null) {
            notify(project, EnvSwitcherBundle.message("notify.missingEnvironment", envName), NotificationType.WARNING)
            return resolution
        }
        if (resolution.missingSecrets.isNotEmpty() && state.missingSecretPolicy == MissingSecretPolicy.BLOCK) {
            throw ExecutionException(
                EnvSwitcherBundle.message("notify.missingSecrets.blocked", envName, resolution.missingSecrets.joinToString(", ")),
            )
        }
        confirmIfRequired(
            project,
            state.environment(envName)?.confirmBeforeRun == true,
            envName,
            everyRun = state.confirmScope == ConfirmScope.EVERY_RUN,
        )

        for ((key, value) in resolution.variables) {
            env.putIfAbsent(key, value) // variables set explicitly on the run configuration win
        }
        if (resolution.missingSecrets.isNotEmpty()) {
            notify(
                project,
                EnvSwitcherBundle.message("notify.missingSecrets", envName, resolution.missingSecrets.joinToString(", ")),
                NotificationType.WARNING,
            )
        }
        thisLogger().debug("Injected ${resolution.variables.size} variables from '$envName' into '${configuration.name}'")
        return resolution
    }

    /** True when this configuration type is enabled and an environment is selected. */
    fun isEnabledFor(
        project: Project,
        configuration: RunConfiguration,
    ): Boolean {
        val state = EnvironmentsService.getInstance(project).state
        return configuration.type.id in state.targetConfigTypeIds &&
            SelectedEnvironmentService.getInstance(project).environmentName != null
    }

    private fun confirmIfRequired(
        project: Project,
        required: Boolean,
        envName: String,
        everyRun: Boolean,
    ) {
        if (!required || ApplicationManager.getApplication().isUnitTestMode) return
        val sessionKey = "${project.locationHash}/$envName"
        if (!everyRun && confirmedThisSession.containsKey(sessionKey)) return
        var accepted = false
        ApplicationManager.getApplication().invokeAndWait {
            accepted =
                Messages.showYesNoDialog(
                    project,
                    EnvSwitcherBundle.message("confirm.run.message", envName),
                    EnvSwitcherBundle.message("confirm.run.title"),
                    Messages.getWarningIcon(),
                ) == Messages.YES
        }
        if (!accepted) throw ExecutionException(EnvSwitcherBundle.message("confirm.run.cancelled", envName))
        confirmedThisSession[sessionKey] = true
    }

    private fun notify(
        project: Project,
        text: String,
        type: NotificationType,
    ) {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(EnvSwitcherBundle.message("plugin.name"), text, type)
            .notify(project)
    }
}

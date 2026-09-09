package io.github.mnaami.environmentswitcher.inject

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.JavaProgramPatcher
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.github.mnaami.environmentswitcher.EnvSwitcherBundle
import io.github.mnaami.environmentswitcher.resolve.Resolution
import io.github.mnaami.environmentswitcher.resolve.VariableResolver
import io.github.mnaami.environmentswitcher.state.EnvironmentsService
import io.github.mnaami.environmentswitcher.state.PasswordSafeSecretStore
import io.github.mnaami.environmentswitcher.state.SecretStore
import io.github.mnaami.environmentswitcher.state.SelectedEnvironmentService
import java.util.concurrent.ConcurrentHashMap

/**
 * Adds the selected environment's variables to every Java-based run
 * configuration whose type is enabled in settings. Runs on every launch.
 */
class EnvProgramPatcher : JavaProgramPatcher() {
    override fun patchJavaParameters(
        executor: Executor?,
        configuration: RunProfile?,
        javaParameters: JavaParameters,
    ) {
        val runConfiguration = configuration as? RunConfiguration ?: return
        val project = runConfiguration.project
        patch(project, runConfiguration, javaParameters, PasswordSafeSecretStore.getInstance(project))
    }

    /** Testable core: no static lookups except the two state services. */
    fun patch(
        project: Project,
        configuration: RunConfiguration,
        javaParameters: JavaParameters,
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
        confirmIfRequired(project, state.environment(envName)?.confirmBeforeRun == true, envName)

        val env = javaParameters.env
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

    private fun confirmIfRequired(
        project: Project,
        required: Boolean,
        envName: String,
    ) {
        if (!required || ApplicationManager.getApplication().isUnitTestMode) return
        val sessionKey = "${project.locationHash}/$envName"
        if (confirmedThisSession.containsKey(sessionKey)) return
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

    companion object {
        const val NOTIFICATION_GROUP = "Environment Switcher"
        private val confirmedThisSession = ConcurrentHashMap<String, Boolean>()
    }
}

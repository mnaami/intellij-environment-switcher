package io.github.mnaami.environmentswitcher.inject

import com.goide.execution.GoRunConfigurationBase
import com.goide.execution.GoRunningState
import com.goide.execution.extension.GoRunConfigurationExtension
import com.goide.util.GoExecutor
import com.intellij.execution.configurations.RunnerSettings
import io.github.mnaami.environmentswitcher.state.PasswordSafeSecretStore

/**
 * Go counterpart of [EnvProgramPatcher].
 *
 * Go launches through GoExecutor rather than a plain command line, so the
 * environment is added there. Seeding from the configuration's own custom
 * environment keeps explicitly set variables winning, as everywhere else.
 *
 * Registered from envswitcher-go.xml, so this class is only ever loaded where
 * the Go plugin is present (GoLand, or any IDE with the Go plugin installed).
 */
class GoEnvRunConfigurationExtension : GoRunConfigurationExtension() {
    override fun isApplicableFor(configuration: GoRunConfigurationBase<*>): Boolean = true

    override fun isEnabledFor(
        configuration: GoRunConfigurationBase<*>,
        runnerSettings: RunnerSettings?,
    ): Boolean = EnvInjector.isEnabledFor(configuration.project, configuration)

    override fun patchExecutor(
        configuration: GoRunConfigurationBase<*>,
        runnerSettings: RunnerSettings?,
        executor: GoExecutor,
        runnerId: String,
        state: GoRunningState<out GoRunConfigurationBase<*>>,
        commandLineType: GoRunningState.CommandLineType,
    ) {
        val project = configuration.project
        val merged = LinkedHashMap(configuration.customEnvironment.orEmpty())
        EnvInjector.inject(project, configuration, merged, PasswordSafeSecretStore.getInstance(project)) ?: return
        executor.withExtraEnvironment(merged)
    }
}

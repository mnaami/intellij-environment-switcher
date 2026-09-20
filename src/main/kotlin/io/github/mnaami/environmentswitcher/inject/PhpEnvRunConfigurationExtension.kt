package io.github.mnaami.environmentswitcher.inject

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.jetbrains.php.config.interpreters.PhpInterpreter
import com.jetbrains.php.run.PhpRunConfiguration
import com.jetbrains.php.run.PhpRunConfigurationExtension
import io.github.mnaami.environmentswitcher.state.PasswordSafeSecretStore

/**
 * PHP counterpart of [EnvProgramPatcher].
 *
 * Registered from envswitcher-php.xml, so this class is only ever loaded where
 * the PHP plugin is present (PhpStorm, or any IDE with the PHP plugin).
 */
class PhpEnvRunConfigurationExtension : PhpRunConfigurationExtension() {
    /** Applies to every interpreter: the variables are interpreter-agnostic. */
    override fun isApplicable(interpreter: PhpInterpreter?): Boolean = true

    override fun isApplicableFor(configuration: PhpRunConfiguration<*>): Boolean = true

    override fun isEnabledFor(
        configuration: PhpRunConfiguration<*>,
        runnerSettings: RunnerSettings?,
    ): Boolean = EnvInjector.isEnabledFor(configuration.project, configuration)

    override fun patchCommandLine(
        configuration: PhpRunConfiguration<*>,
        runnerSettings: RunnerSettings?,
        cmdLine: GeneralCommandLine,
        runnerId: String,
    ) {
        val project = configuration.project
        EnvInjector.inject(project, configuration, cmdLine.environment, PasswordSafeSecretStore.getInstance(project))
    }
}

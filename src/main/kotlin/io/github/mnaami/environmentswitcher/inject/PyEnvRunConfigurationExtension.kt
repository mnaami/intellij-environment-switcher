package io.github.mnaami.environmentswitcher.inject

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PythonRunConfigurationExtension
import io.github.mnaami.environmentswitcher.state.PasswordSafeSecretStore

/**
 * Python counterpart of [EnvProgramPatcher]: injects the selected environment
 * into Python run configurations just before launch.
 *
 * Registered from envswitcher-python.xml, so this class is only ever loaded in
 * IDEs that bundle the Python plugin (PyCharm Community and Professional, and
 * IDEA with the Python plugin installed).
 */
class PyEnvRunConfigurationExtension : PythonRunConfigurationExtension() {
    override fun isApplicableFor(configuration: AbstractPythonRunConfiguration<*>): Boolean = true

    override fun isEnabledFor(
        configuration: AbstractPythonRunConfiguration<*>,
        runnerSettings: RunnerSettings?,
    ): Boolean = EnvInjector.isEnabledFor(configuration.project, configuration)

    override fun patchCommandLine(
        configuration: AbstractPythonRunConfiguration<*>,
        runnerSettings: RunnerSettings?,
        cmdLine: GeneralCommandLine,
        runnerId: String,
    ) {
        val project = configuration.project
        EnvInjector.inject(project, configuration, cmdLine.environment, PasswordSafeSecretStore.getInstance(project))
    }
}

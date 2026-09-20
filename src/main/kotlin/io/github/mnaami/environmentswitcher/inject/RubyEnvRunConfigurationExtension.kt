package io.github.mnaami.environmentswitcher.inject

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import io.github.mnaami.environmentswitcher.state.PasswordSafeSecretStore
import org.jetbrains.plugins.ruby.ruby.run.configuration.AbstractRubyRunConfiguration
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyRunConfigurationExtension

/**
 * Ruby counterpart of [EnvProgramPatcher].
 *
 * Registered from envswitcher-ruby.xml, so this class is only ever loaded where
 * the Ruby plugin is present (RubyMine, or any IDE with the Ruby plugin).
 */
class RubyEnvRunConfigurationExtension : RubyRunConfigurationExtension() {
    override fun isApplicableFor(configuration: AbstractRubyRunConfiguration<*>): Boolean = true

    override fun isEnabledFor(
        configuration: AbstractRubyRunConfiguration<*>,
        runnerSettings: RunnerSettings?,
    ): Boolean = EnvInjector.isEnabledFor(configuration.project, configuration)

    override fun patchCommandLine(
        configuration: AbstractRubyRunConfiguration<*>,
        runnerSettings: RunnerSettings?,
        cmdLine: GeneralCommandLine,
        runnerId: String,
    ) {
        val project = configuration.project
        EnvInjector.inject(project, configuration, cmdLine.environment, PasswordSafeSecretStore.getInstance(project))
    }
}

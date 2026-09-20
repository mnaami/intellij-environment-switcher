package io.github.mnaami.environmentswitcher.inject

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.javascript.nodejs.execution.AbstractNodeTargetRunProfile
import com.intellij.javascript.nodejs.execution.NodeTargetRun
import com.intellij.javascript.nodejs.execution.runConfiguration.AbstractNodeRunConfigurationExtension
import com.intellij.javascript.nodejs.execution.runConfiguration.NodeRunConfigurationLaunchSession
import io.github.mnaami.environmentswitcher.EnvSwitcherBundle
import io.github.mnaami.environmentswitcher.state.PasswordSafeSecretStore

/**
 * Node counterpart of [EnvProgramPatcher].
 *
 * Node configurations do not go through patchCommandLine - it is final in the
 * base class - so the environment is applied from the launch session instead,
 * just before the process is configured.
 *
 * Registered from envswitcher-node.xml, so this class is only ever loaded where
 * the JavaScript plugin is present (WebStorm, IDEA Ultimate).
 */
class NodeEnvRunConfigurationExtension : AbstractNodeRunConfigurationExtension() {
    override fun getEditorTitle(): String = EnvSwitcherBundle.message("plugin.name")

    override fun isApplicableFor(configuration: AbstractNodeTargetRunProfile): Boolean = true

    override fun createLaunchSession(
        configuration: AbstractNodeTargetRunProfile,
        environment: ExecutionEnvironment,
    ): NodeRunConfigurationLaunchSession =
        object : NodeRunConfigurationLaunchSession() {
            override fun addNodeOptionsTo(targetRun: NodeTargetRun) {
                val project = configuration.project
                val existing = targetRun.envData
                // seed with the configuration's own variables so they keep precedence
                val merged = LinkedHashMap(existing.envs)
                EnvInjector.inject(project, configuration, merged, PasswordSafeSecretStore.getInstance(project))
                    ?: return
                targetRun.envData = EnvironmentVariablesData.create(merged, existing.isPassParentEnvs)
            }
        }
}

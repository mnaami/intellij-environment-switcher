package io.github.mnaami.environmentswitcher.inject

import com.intellij.execution.Executor
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.JavaProgramPatcher
import com.intellij.openapi.project.Project
import io.github.mnaami.environmentswitcher.resolve.Resolution
import io.github.mnaami.environmentswitcher.state.PasswordSafeSecretStore
import io.github.mnaami.environmentswitcher.state.SecretStore

/**
 * Adds the selected environment's variables to every Java-based run
 * configuration whose type is enabled in settings. Runs on every launch.
 *
 * Registered from envswitcher-java.xml, so this class is only ever loaded in
 * IDEs that bundle the Java plugin. All logic lives in [EnvInjector].
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
    ): Resolution? = EnvInjector.inject(project, configuration, javaParameters.env, secrets)

    companion object {
        const val NOTIFICATION_GROUP = EnvInjector.NOTIFICATION_GROUP
    }
}

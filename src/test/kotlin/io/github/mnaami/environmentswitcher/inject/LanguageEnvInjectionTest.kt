package io.github.mnaami.environmentswitcher.inject

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.model.EnvironmentsState
import io.github.mnaami.environmentswitcher.state.EnvironmentsService
import io.github.mnaami.environmentswitcher.state.InMemorySecretStore
import io.github.mnaami.environmentswitcher.state.SelectedEnvironmentService

/**
 * Every non-Java run configuration type shipped in the defaults must reach the
 * injector. Guards the type ids in [EnvironmentsState.DEFAULT_TARGET_TYPES]:
 * a typo there silently disables injection for that language.
 */
class LanguageEnvInjectionTest : BasePlatformTestCase() {
    private val secrets = InMemorySecretStore()

    override fun setUp() {
        super.setUp()
        EnvironmentsService.getInstance(project).update {
            commonVariables.clear()
            environments.clear()
            overrides.clear()
            commonVariables["HOST"] = "127.0.0.1"
            environments += Environment("staging", mapOf("PROFILE" to "staging"))
            targetConfigTypeIds = EnvironmentsState.DEFAULT_TARGET_TYPES.toMutableList()
            expandVariables = false
        }
        SelectedEnvironmentService.getInstance(project).environmentName = "staging"
    }

    private fun template(typeId: String): RunConfiguration {
        val type =
            ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.firstOrNull { it.id == typeId }
                ?: error("run configuration type '$typeId' is not registered in this test IDE")
        return type.configurationFactories.first().createTemplateConfiguration(project)
    }

    private fun assertInjects(typeId: String) {
        val configuration = template(typeId)
        assertTrue("$typeId should be enabled by default", EnvInjector.isEnabledFor(project, configuration))

        val env = hashMapOf("PROFILE" to "mine")
        val result = EnvInjector.inject(project, configuration, env, secrets)

        assertNotNull("$typeId should resolve an environment", result)
        assertEquals("$typeId should receive common variables", "127.0.0.1", env["HOST"])
        assertEquals("$typeId must not overwrite explicit variables", "mine", env["PROFILE"])
    }

    fun testPython() = assertInjects("PythonConfigurationType")

    fun testNode() = assertInjects("NodeJSConfigurationType")

    fun testGo() = assertInjects("GoApplicationRunConfiguration")

    fun testPhp() = assertInjects("PhpLocalRunConfigurationType")

    fun testRuby() = assertInjects("RubyRunConfigurationType")
}

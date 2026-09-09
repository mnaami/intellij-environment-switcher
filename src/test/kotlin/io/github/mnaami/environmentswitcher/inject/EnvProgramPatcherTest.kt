package io.github.mnaami.environmentswitcher.inject

import com.intellij.execution.ExecutionException
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.jar.JarApplicationConfiguration
import com.intellij.execution.jar.JarApplicationConfigurationType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.model.MissingSecretPolicy
import io.github.mnaami.environmentswitcher.model.ModuleOverride
import io.github.mnaami.environmentswitcher.state.EnvironmentsService
import io.github.mnaami.environmentswitcher.state.InMemorySecretStore
import io.github.mnaami.environmentswitcher.state.SelectedEnvironmentService

class EnvProgramPatcherTest : BasePlatformTestCase() {
    private val patcher = EnvProgramPatcher()
    private val secrets = InMemorySecretStore()

    override fun setUp() {
        super.setUp()
        EnvironmentsService.getInstance(project).update {
            commonVariables.clear()
            environments.clear()
            overrides.clear()
            commonVariables["HOST"] = "127.0.0.1"
            environments += Environment("staging", mapOf("PROFILE" to "staging"), secretKeys = listOf("DB_PASSWORD"))
            overrides += ModuleOverride(module.name, mapOf("PORT" to "9092"))
            targetConfigTypeIds = mutableListOf("Application")
            missingSecretPolicy = MissingSecretPolicy.WARN
            expandVariables = false
        }
        secrets.set("staging", "DB_PASSWORD", "pw")
        SelectedEnvironmentService.getInstance(project).environmentName = "staging"
    }

    private fun applicationConfiguration(): ApplicationConfiguration =
        ApplicationConfiguration("app", project, ApplicationConfigurationType.getInstance()).also { it.setModule(module) }

    /** A Java run configuration whose type ("JarApplication") is not enabled in the test state. */
    private fun jarConfiguration(): RunConfiguration =
        JarApplicationConfiguration(project, JarApplicationConfigurationType.getInstance().configurationFactories[0], "jar")

    fun testEnabledTypeReceivesCommonEnvOverrideAndSecretVariables() {
        val params = JavaParameters()

        val result = patcher.patch(project, applicationConfiguration(), params, secrets)

        assertNotNull(result)
        assertEquals("127.0.0.1", params.env["HOST"])
        assertEquals("staging", params.env["PROFILE"])
        assertEquals("9092", params.env["PORT"])
        assertEquals("pw", params.env["DB_PASSWORD"])
    }

    fun testVariablesSetExplicitlyOnTheRunConfigurationWin() {
        val params = JavaParameters().apply { env["PROFILE"] = "mine" }

        patcher.patch(project, applicationConfiguration(), params, secrets)

        assertEquals("mine", params.env["PROFILE"])
        assertEquals("127.0.0.1", params.env["HOST"])
    }

    fun testDisabledTypeIsUntouchedUntilEnabled() {
        val params = JavaParameters()

        assertNull(patcher.patch(project, jarConfiguration(), params, secrets))
        assertTrue(params.env.isEmpty())

        EnvironmentsService.getInstance(project).update { targetConfigTypeIds += "JarApplication" }
        assertNotNull(patcher.patch(project, jarConfiguration(), params, secrets))
        assertEquals("staging", params.env["PROFILE"])
    }

    fun testNothingHappensWhenNoEnvironmentIsSelected() {
        SelectedEnvironmentService.getInstance(project).environmentName = null
        val params = JavaParameters()

        assertNull(patcher.patch(project, applicationConfiguration(), params, secrets))
        assertTrue(params.env.isEmpty())
    }

    fun testMissingSecretIsReportedAndNotInjected() {
        secrets.delete("staging", "DB_PASSWORD")
        val params = JavaParameters()

        val result = patcher.patch(project, applicationConfiguration(), params, secrets)!!

        assertEquals(listOf("DB_PASSWORD"), result.missingSecrets)
        assertFalse(params.env.containsKey("DB_PASSWORD"))
    }

    fun testBlockPolicyRefusesToLaunchWhenASecretIsMissing() {
        secrets.delete("staging", "DB_PASSWORD")
        EnvironmentsService.getInstance(project).update { missingSecretPolicy = MissingSecretPolicy.BLOCK }
        val params = JavaParameters()

        try {
            patcher.patch(project, applicationConfiguration(), params, secrets)
            fail("expected the launch to be blocked")
        } catch (e: ExecutionException) {
            assertTrue(e.message!!.contains("DB_PASSWORD"))
        }
        assertTrue(params.env.isEmpty())
    }

    fun testExpansionAppliesWhenEnabled() {
        EnvironmentsService.getInstance(project).update {
            expandVariables = true
            environment("staging")!!.variables["URL"] = "http://\${HOST}:\${PORT}"
        }
        val params = JavaParameters()

        patcher.patch(project, applicationConfiguration(), params, secrets)

        assertEquals("http://127.0.0.1:9092", params.env["URL"])
    }
}

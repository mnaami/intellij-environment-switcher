package io.github.mnaami.environmentswitcher.resolve

import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.model.EnvironmentsState
import io.github.mnaami.environmentswitcher.model.ModuleOverride
import io.github.mnaami.environmentswitcher.state.InMemorySecretStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VariableResolverTest {
    private val secrets = InMemorySecretStore()
    private val state =
        EnvironmentsState().apply {
            commonVariables["HOST"] = "127.0.0.1"
            commonVariables["PORT"] = "8080"
            environments +=
                Environment(
                    name = "dev",
                    variables = mapOf("PROFILE" to "dev", "PORT" to "9000"),
                    secretKeys = listOf("DB_PASSWORD", "TOKEN"),
                )
            overrides += ModuleOverride("billing", mapOf("PORT" to "9092"))
        }
    private val resolver = VariableResolver(state, secrets)

    @Test
    fun `environment overrides common and module override wins over both`() {
        secrets.set("dev", "DB_PASSWORD", "s3cr3t")
        secrets.set("dev", "TOKEN", "t")

        assertEquals("9000", resolver.resolve("dev", null).variables["PORT"])
        assertEquals("9092", resolver.resolve("dev", "billing").variables["PORT"])
        assertEquals("8080", state.commonVariables["PORT"], "state must not be mutated")
        assertEquals("127.0.0.1", resolver.resolve("dev", "billing").variables["HOST"])
    }

    @Test
    fun `secrets come from the store and missing ones are reported not injected`() {
        secrets.set("dev", "DB_PASSWORD", "s3cr3t")

        val result = resolver.resolve("dev", null)

        assertEquals("s3cr3t", result.variables["DB_PASSWORD"])
        assertFalse(result.variables.containsKey("TOKEN"))
        assertEquals(listOf("TOKEN"), result.missingSecrets)
    }

    @Test
    fun `secrets are scoped per environment`() {
        secrets.set("prod", "DB_PASSWORD", "prod-pw")
        assertTrue(resolver.resolve("dev", null).missingSecrets.contains("DB_PASSWORD"))
    }

    @Test
    fun `unknown environment yields nothing and is flagged`() {
        val result = resolver.resolve("qa", "billing")
        assertTrue(result.variables.isEmpty())
        assertEquals("qa", result.missingEnvironment)
    }
}

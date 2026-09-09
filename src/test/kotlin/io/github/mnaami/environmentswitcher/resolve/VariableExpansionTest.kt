package io.github.mnaami.environmentswitcher.resolve

import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.model.EnvironmentsState
import io.github.mnaami.environmentswitcher.state.InMemorySecretStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VariableExpansionTest {
    private fun state(expand: Boolean) =
        EnvironmentsState().apply {
            expandVariables = expand
            commonVariables["HOST"] = "iam.example.test"
            environments +=
                Environment(
                    "dev",
                    mapOf(
                        "KEYCLOAK_URL" to "http://\${HOST}:8080/auth",
                        "ISSUER" to "\${KEYCLOAK_URL}/realms/app",
                        "UNKNOWN" to "\${NOPE}/x",
                        "SELF" to "\${SELF}",
                    ),
                    secretKeys = listOf("SECRET_URL"),
                )
        }

    @Test
    fun `references are left literal when expansion is off`() {
        val r = VariableResolver(state(false), InMemorySecretStore()).resolve("dev", null)
        assertEquals("http://\${HOST}:8080/auth", r.variables["KEYCLOAK_URL"])
    }

    @Test
    fun `chained references expand and unknown or self references stay literal`() {
        val secrets = InMemorySecretStore().apply { set("dev", "SECRET_URL", "\${ISSUER}/token") }
        val r = VariableResolver(state(true), secrets).resolve("dev", null)

        assertEquals("http://iam.example.test:8080/auth", r.variables["KEYCLOAK_URL"])
        assertEquals("http://iam.example.test:8080/auth/realms/app", r.variables["ISSUER"])
        assertEquals("http://iam.example.test:8080/auth/realms/app/token", r.variables["SECRET_URL"])
        assertEquals("\${NOPE}/x", r.variables["UNKNOWN"])
        assertEquals("\${SELF}", r.variables["SELF"])
    }

    @Test
    fun `cycles terminate`() {
        val out = VariableResolver.expand(linkedMapOf("A" to "\${B}", "B" to "\${A}"))
        assertEquals(2, out.size)
    }
}

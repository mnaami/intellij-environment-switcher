package io.github.mnaami.environmentswitcher.ui

import io.github.mnaami.environmentswitcher.importer.EnvFolderImporter
import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.model.EnvironmentsState
import io.github.mnaami.environmentswitcher.state.InMemorySecretStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SettingsModelTest {
    private val secrets = InMemorySecretStore()
    private val state =
        EnvironmentsState().apply {
            commonVariables["HOST"] = "127.0.0.1"
            environments += Environment("dev", mapOf("PROFILE" to "dev"), secretKeys = listOf("DB_PASSWORD", "TOKEN"))
        }

    @Test
    fun `loads secret rows without values and flags which ones are stored`() {
        secrets.set("dev", "DB_PASSWORD", "pw")

        val rows = SettingsModel.from(state, secrets).environments[0].rows

        val db = rows.first { it.key == "DB_PASSWORD" }
        val token = rows.first { it.key == "TOKEN" }
        assertTrue(db.secret && db.stored)
        assertNull(db.value)
        assertTrue(token.secret)
        assertFalse(token.stored)
        assertEquals("dev", rows.first { it.key == "PROFILE" }.value)
    }

    @Test
    fun `apply writes plain values to state and pending secrets to the store only`() {
        val model = SettingsModel.from(state, secrets)
        val env = model.environments[0]
        env.rows.first { it.key == "TOKEN" }.value = "new-token"
        env.rows += VariableRow("API_URL", "https://x")
        env.confirmBeforeRun = true
        model.common += VariableRow("REGION", "eu")

        model.applyTo(state, secrets)

        val dev = state.environment("dev")!!
        assertEquals(mapOf("PROFILE" to "dev", "API_URL" to "https://x"), dev.variables)
        assertEquals(listOf("DB_PASSWORD", "TOKEN"), dev.secretKeys)
        assertTrue(dev.confirmBeforeRun)
        assertEquals("new-token", secrets.get("dev", "TOKEN"))
        assertEquals("eu", state.commonVariables["REGION"])
        assertNull(env.rows.first { it.key == "TOKEN" }.value, "pending value is cleared after apply")
        assertTrue(env.rows.first { it.key == "TOKEN" }.stored)
    }

    @Test
    fun `unchanged secrets keep their stored value and removed secrets are forgotten`() {
        secrets.set("dev", "DB_PASSWORD", "pw")
        secrets.set("dev", "TOKEN", "t")
        val model = SettingsModel.from(state, secrets)
        model.environments[0].rows.removeIf { it.key == "TOKEN" }

        model.applyTo(state, secrets)

        assertEquals("pw", secrets.get("dev", "DB_PASSWORD"))
        assertNull(secrets.get("dev", "TOKEN"))
        assertEquals(listOf("DB_PASSWORD"), state.environment("dev")!!.secretKeys)
    }

    @Test
    fun `deep copy is equal until edited which drives isModified`() {
        val model = SettingsModel.from(state, secrets)
        val snapshot = model.deepCopy()
        assertEquals(snapshot, model)
        model.environments[0].rows[0].value = "changed"
        assertFalse(snapshot == model)
    }

    @Test
    fun `validation catches empty and duplicate names`() {
        val model = SettingsModel.from(state, secrets)
        model.environments += EnvironmentDraft("dev")
        model.environments += EnvironmentDraft(" ")
        model.common += VariableRow("HOST", "dup")

        val problems = model.validate()

        assertTrue(problems.any { it.contains("Duplicate environment name 'dev'") }, problems.toString())
        assertTrue(problems.any { it.contains("must not be empty") }, problems.toString())
        assertTrue(problems.any { it.contains("Duplicate variable 'HOST'") }, problems.toString())
    }

    @Test
    fun `import replaces environments and carries developer secrets as pending values`() {
        state.targetConfigTypeIds = mutableListOf("Application")
        val model = SettingsModel.from(state, secrets)

        model.importPlan(EnvFolderImporter().scan(File("src/test/resources/env-sample")))

        assertEquals(listOf("dev", "prod"), model.environments.map { it.name })
        val dbRow = model.environments[0].rows.first { it.key == "DB_PASSWORD" }
        assertTrue(dbRow.secret)
        assertEquals("dev-pw", dbRow.value)
        assertEquals(setOf("Application"), model.targetConfigTypeIds, "target types are untouched by import")

        model.applyTo(state, secrets)
        assertEquals("dev-pw", secrets.get("dev", "DB_PASSWORD"))
        assertFalse(state.environment("dev")!!.variables.containsKey("DB_PASSWORD"))
    }
}

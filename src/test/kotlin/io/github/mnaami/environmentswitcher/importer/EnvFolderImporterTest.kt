package io.github.mnaami.environmentswitcher.importer

import io.github.mnaami.environmentswitcher.model.EnvironmentsState
import io.github.mnaami.environmentswitcher.state.InMemorySecretStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class EnvFolderImporterTest {
    private val folder = File("src/test/resources/env-sample")
    private val importer = EnvFolderImporter()

    @Test
    fun `scans environments common overrides and secret keys`() {
        val plan = importer.scan(folder)

        assertEquals(listOf("dev", "prod"), plan.environments.map { it.name })
        assertEquals("127.0.0.1", plan.common["HOST_IP_ADDRESS"])
        assertEquals("dev", plan.environments[0].variables["PROFILE"])
        assertEquals("https://api.acme.test", plan.environments[1].variables["API_URL"])
        assertEquals(listOf("DB_PASSWORD", "CLIENT_SECRET"), plan.environments[0].secretKeys)
        assertEquals(listOf("billing-service", "catalog-service"), plan.overrides.map { it.moduleName })
        assertEquals("9092", plan.overrides[0].variables["PORT"])
    }

    @Test
    fun `developer secret files feed the store only and blanks are skipped`() {
        val plan = importer.scan(folder)

        assertEquals(mapOf("DB_PASSWORD" to "dev-pw"), plan.secrets["dev"])
        assertNull(plan.secrets["prod"])
        assertFalse(plan.environments.any { it.variables.containsKey("DB_PASSWORD") })
    }

    @Test
    fun `apply replaces state and writes secrets`() {
        val state =
            EnvironmentsState().apply {
                commonVariables["OLD"] = "x"
                targetConfigTypeIds = mutableListOf("Application")
            }
        val secrets = InMemorySecretStore()

        importer.apply(importer.scan(folder), state, secrets)

        assertFalse(state.commonVariables.containsKey("OLD"))
        assertEquals(2, state.environments.size)
        assertEquals("dev-pw", secrets.get("dev", "DB_PASSWORD"))
        assertEquals(listOf("Application"), state.targetConfigTypeIds, "target types are not part of the import")
    }

    @Test
    fun `empty or missing folder yields an empty plan`() {
        assertTrue(importer.scan(File("does/not/exist")).isEmpty)
    }
}

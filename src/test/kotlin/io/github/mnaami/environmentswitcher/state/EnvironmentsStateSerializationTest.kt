package io.github.mnaami.environmentswitcher.state

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.model.EnvironmentsState
import io.github.mnaami.environmentswitcher.model.ModuleOverride
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnvironmentsStateSerializationTest {
    private fun sample(): EnvironmentsState =
        EnvironmentsState().apply {
            commonVariables["HOST_IP_ADDRESS"] = "127.0.0.1"
            environments +=
                Environment(
                    name = "dev",
                    variables = linkedMapOf("PROFILE" to "dev", "API_URL" to "http://dev.example.test/api?x=1&y=2"),
                    secretKeys = listOf("DB_PASSWORD", "CLIENT_SECRET"),
                    color = "#4C9AFF",
                )
            environments += Environment(name = "prod", variables = mapOf("PROFILE" to "prod"), confirmBeforeRun = true)
            overrides += ModuleOverride("billing-service", mapOf("PORT" to "9092"))
            targetConfigTypeIds = mutableListOf("Application")
        }

    @Test
    fun `round trips through xml`() {
        val original = sample()
        val xml = XmlSerializer.serialize(original)
        val restored = XmlSerializer.deserialize(xml, EnvironmentsState::class.java)

        assertEquals(original.commonVariables, restored.commonVariables)
        assertEquals(original.environments, restored.environments)
        assertEquals(original.overrides, restored.overrides)
        assertEquals(original.targetConfigTypeIds, restored.targetConfigTypeIds)
        assertTrue(restored.environment("prod")!!.confirmBeforeRun)
        assertEquals("#4C9AFF", restored.environment("dev")!!.color)
    }

    @Test
    fun `serialized form names secret keys but has no place for their values`() {
        val text = JDOMUtil.write(XmlSerializer.serialize(sample()))
        assertTrue(text.contains("""<secret name="DB_PASSWORD" />"""), text)
        assertFalse(text.contains("DB_PASSWORD\" value"), text)
    }

    @Test
    fun `defaults enable common java run configuration types`() {
        val state = EnvironmentsState()
        assertTrue(state.targetConfigTypeIds.contains("Application"))
        assertTrue(state.targetConfigTypeIds.contains("SpringBootApplicationConfigurationType"))
        assertEquals(emptyMap<String, String>(), state.overridesFor("unknown"))
        assertEquals(emptyMap<String, String>(), state.overridesFor(null))
    }
}

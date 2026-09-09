package io.github.mnaami.environmentswitcher.ui

import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.ui.EnvironmentMenuModel.Item
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnvironmentMenuModelTest {
    private val envs = listOf(Environment("dev", color = "#111111"), Environment("prod", color = "#222222"))

    @Test
    fun `lists environments then separator then edit and marks the selected one`() {
        assertEquals(
            listOf(Item.Env("dev", "#111111", false), Item.Env("prod", "#222222", true), Item.Separator, Item.Edit),
            EnvironmentMenuModel.items(envs, "prod"),
        )
    }

    @Test
    fun `offers only edit when there are no environments`() {
        assertEquals(listOf(Item.Edit), EnvironmentMenuModel.items(emptyList(), null))
    }

    @Test
    fun `button text falls back when selection is missing or stale`() {
        assertEquals("none", EnvironmentMenuModel.buttonText(emptyList(), "dev", "none", "pick"))
        assertEquals("pick", EnvironmentMenuModel.buttonText(envs, null, "none", "pick"))
        assertEquals("pick", EnvironmentMenuModel.buttonText(envs, "deleted", "none", "pick"))
        assertEquals("dev", EnvironmentMenuModel.buttonText(envs, "dev", "none", "pick"))
    }
}

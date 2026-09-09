package io.github.mnaami.environmentswitcher.ui

import io.github.mnaami.environmentswitcher.model.Environment

/** Pure description of what the toolbar dropdown shows; kept UI-free so it can be unit tested. */
object EnvironmentMenuModel {
    sealed interface Item {
        data class Env(
            val name: String,
            val color: String,
            val selected: Boolean,
        ) : Item

        data object Separator : Item

        data object Edit : Item
    }

    fun items(
        environments: List<Environment>,
        selected: String?,
    ): List<Item> {
        val envs = environments.map { Item.Env(it.name, it.color, it.name == selected) }
        return if (envs.isEmpty()) listOf(Item.Edit) else envs + Item.Separator + Item.Edit
    }

    /** Text shown on the toolbar button. */
    fun buttonText(
        environments: List<Environment>,
        selected: String?,
        noEnvironments: String,
        selectPrompt: String,
    ): String =
        when {
            environments.isEmpty() -> noEnvironments
            selected != null && environments.any { it.name == selected } -> selected
            else -> selectPrompt
        }
}

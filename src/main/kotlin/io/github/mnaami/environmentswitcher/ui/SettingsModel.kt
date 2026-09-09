package io.github.mnaami.environmentswitcher.ui

import io.github.mnaami.environmentswitcher.importer.EnvFolderImporter
import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.model.EnvironmentsState
import io.github.mnaami.environmentswitcher.model.ModuleOverride
import io.github.mnaami.environmentswitcher.state.SecretStore

/** One editable variable. For secret rows `value` is a *pending* new value; null means "leave the stored one". */
data class VariableRow(
    var key: String = "",
    var value: String? = "",
    var secret: Boolean = false,
    /** A value already exists in the secret store (informational, drives the "stored" rendering). */
    var stored: Boolean = false,
)

data class EnvironmentDraft(
    var name: String,
    var color: String = Environment.DEFAULT_COLOR,
    var confirmBeforeRun: Boolean = false,
    val rows: MutableList<VariableRow> = ArrayList(),
)

data class OverrideDraft(
    var moduleName: String,
    val rows: MutableList<VariableRow> = ArrayList(),
)

/**
 * Editable working copy behind the settings page. UI-free so the state mapping
 * and the secret handling can be unit tested.
 */
data class SettingsModel(
    val common: MutableList<VariableRow> = ArrayList(),
    val environments: MutableList<EnvironmentDraft> = ArrayList(),
    val overrides: MutableList<OverrideDraft> = ArrayList(),
) {
    fun deepCopy(): SettingsModel =
        SettingsModel(
            common.map { it.copy() }.toMutableList(),
            environments.map { e -> e.copy(rows = e.rows.map { it.copy() }.toMutableList()) }.toMutableList(),
            overrides.map { o -> o.copy(rows = o.rows.map { it.copy() }.toMutableList()) }.toMutableList(),
        )

    /** Problems that block Apply. */
    fun validate(): List<String> {
        val problems = ArrayList<String>()
        val names = environments.map { it.name.trim() }
        names.filter { it.isEmpty() }.forEach { _ -> problems += "Environment name must not be empty" }
        names
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
            .forEach { problems += "Duplicate environment name '$it'" }
        for (env in environments) problems += rowProblems("environment '${env.name}'", env.rows)
        problems += rowProblems("common variables", common)
        for (o in overrides) problems += rowProblems("override '${o.moduleName}'", o.rows)
        return problems
    }

    private fun rowProblems(
        where: String,
        rows: List<VariableRow>,
    ): List<String> {
        val keys = rows.map { it.key.trim() }
        val problems = ArrayList<String>()
        if (keys.any { it.isEmpty() }) problems += "Empty variable name in $where"
        keys.groupBy { it }.filterValues { it.size > 1 }.keys.filter { it.isNotEmpty() }.forEach {
            problems += "Duplicate variable '$it' in $where"
        }
        return problems
    }

    /** Writes plain values into [state] and pending secret values into [secrets]. */
    fun applyTo(
        state: EnvironmentsState,
        secrets: SecretStore,
    ) {
        state.commonVariables = plainMap(common)
        state.overrides = overrides.map { ModuleOverride(it.moduleName.trim(), plainMap(it.rows)) }.toMutableList()

        val previousSecretKeys = state.environments.associate { it.name to it.secretKeys.toSet() }
        state.environments =
            environments
                .map { draft ->
                    val name = draft.name.trim()
                    val secretRows = draft.rows.filter { it.secret }
                    for (row in secretRows) {
                        val pending = row.value
                        if (pending != null) {
                            secrets.set(name, row.key.trim(), pending.ifEmpty { null })
                            row.value = null
                            row.stored = pending.isNotEmpty()
                        }
                    }
                    // keys that stopped being secret or were removed: forget their stored value
                    val keptSecretKeys = secretRows.map { it.key.trim() }.toSet()
                    previousSecretKeys[name].orEmpty().minus(keptSecretKeys).forEach { secrets.delete(name, it) }
                    Environment(
                        name = name,
                        variables = plainMap(draft.rows),
                        secretKeys = secretRows.map { it.key.trim() },
                        color = draft.color,
                        confirmBeforeRun = draft.confirmBeforeRun,
                    )
                }.toMutableList()
    }

    /** Merges an imported folder into this model: environments, common and overrides are replaced. */
    fun importPlan(plan: EnvFolderImporter.Plan) {
        common.clear()
        common += plan.common.map { (k, v) -> VariableRow(k, v) }
        environments.clear()
        for (env in plan.environments) {
            val draft = EnvironmentDraft(env.name)
            draft.rows += env.variables.map { (k, v) -> VariableRow(k, v) }
            val imported = plan.secrets[env.name].orEmpty()
            draft.rows +=
                env.secretKeys.map { key ->
                    VariableRow(key, value = imported[key], secret = true, stored = false)
                }
            environments += draft
        }
        overrides.clear()
        overrides +=
            plan.overrides.map { o -> OverrideDraft(o.moduleName, o.variables.map { (k, v) -> VariableRow(k, v) }.toMutableList()) }
    }

    companion object {
        fun from(
            state: EnvironmentsState,
            secrets: SecretStore,
        ): SettingsModel {
            val model = SettingsModel()
            model.common += state.commonVariables.map { (k, v) -> VariableRow(k, v) }
            for (env in state.environments) {
                val draft = EnvironmentDraft(env.name, env.color, env.confirmBeforeRun)
                draft.rows += env.variables.map { (k, v) -> VariableRow(k, v) }
                draft.rows +=
                    env.secretKeys.map { key -> VariableRow(key, null, secret = true, stored = secrets.get(env.name, key) != null) }
                model.environments += draft
            }
            model.overrides +=
                state.overrides.map { o -> OverrideDraft(o.moduleName, o.variables.map { (k, v) -> VariableRow(k, v) }.toMutableList()) }
            return model
        }

        private fun plainMap(rows: List<VariableRow>): LinkedHashMap<String, String> =
            rows.filterNot { it.secret }.associateTo(LinkedHashMap()) { it.key.trim() to (it.value ?: "") }
    }
}

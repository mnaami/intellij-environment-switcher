package io.github.mnaami.environmentswitcher.importer

import io.github.mnaami.environmentswitcher.model.Environment
import io.github.mnaami.environmentswitcher.model.EnvironmentsState
import io.github.mnaami.environmentswitcher.model.ModuleOverride
import io.github.mnaami.environmentswitcher.state.SecretStore
import java.io.File

/**
 * Imports a folder laid out as:
 * ```
 * <name>.env                 one environment per file
 * common.env                 optional, shared by all environments
 * local.env                  optional, ignored (developer overlay)
 * overrides/<module>.env     optional per-module overrides (services/ also accepted)
 * *.example                  optional secrets template: its keys become secret keys
 * secrets.<name>[.local].env optional developer secrets, sent to the secret store only
 * ```
 */
class EnvFolderImporter {
    data class Plan(
        val common: Map<String, String>,
        val environments: List<Environment>,
        val overrides: List<ModuleOverride>,
        /** environment -> key -> value; never persisted to state */
        val secrets: Map<String, Map<String, String>>,
    ) {
        val isEmpty: Boolean get() = environments.isEmpty()
    }

    fun scan(folder: File): Plan {
        val files = folder.listFiles { f -> f.isFile && !f.name.startsWith("current.") }.orEmpty().sortedBy { it.name }
        val secretKeys =
            files
                .filter { it.name.endsWith(".example") }
                .flatMap { DotEnvParser.parse(it.readText()).keys }
                .distinct()
        val secrets = LinkedHashMap<String, Map<String, String>>()
        val environments = ArrayList<Environment>()
        var common: Map<String, String> = emptyMap()

        for (file in files) {
            val name = file.name
            when {
                name.endsWith(".example") -> Unit
                name == "common.env" -> common = DotEnvParser.parse(file.readText())
                name == "local.env" -> Unit
                name.startsWith("secrets.") && name.endsWith(".env") -> {
                    val env = name.removePrefix("secrets.").removeSuffix(".env").removeSuffix(".local")
                    secrets[env] = DotEnvParser.parse(file.readText()).filterValues { it.isNotBlank() }
                }
                name.endsWith(".env") -> {
                    val envName = name.removeSuffix(".env")
                    environments += Environment(envName, DotEnvParser.parse(file.readText()), secretKeys)
                }
            }
        }
        val overrides =
            listOf("overrides", "services")
                .map { File(folder, it) }
                .filter { it.isDirectory }
                .flatMap { dir -> dir.listFiles { f -> f.isFile && f.name.endsWith(".env") }.orEmpty().sortedBy { it.name } }
                .map { ModuleOverride(it.name.removeSuffix(".env"), DotEnvParser.parse(it.readText())) }

        return Plan(common, environments, overrides, secrets.filterKeys { env -> environments.any { it.name == env } })
    }

    /** Replaces environments, common variables and overrides; writes secret values to the store. */
    fun apply(
        plan: Plan,
        state: EnvironmentsState,
        secrets: SecretStore,
    ) {
        state.commonVariables = LinkedHashMap(plan.common)
        state.environments = plan.environments.toMutableList()
        state.overrides = plan.overrides.toMutableList()
        for ((env, values) in plan.secrets) {
            for ((key, value) in values) secrets.set(env, key, value)
        }
    }
}

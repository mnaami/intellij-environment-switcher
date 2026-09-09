package io.github.mnaami.environmentswitcher.resolve

import io.github.mnaami.environmentswitcher.model.EnvironmentsState
import io.github.mnaami.environmentswitcher.state.SecretStore

/** Result of resolving an environment: the variables to inject plus what could not be resolved. */
data class Resolution(
    val variables: Map<String, String>,
    val missingSecrets: List<String> = emptyList(),
    val missingEnvironment: String? = null,
) {
    companion object {
        fun empty(missingEnvironment: String? = null) = Resolution(emptyMap(), missingEnvironment = missingEnvironment)
    }
}

/**
 * Precedence, lowest to highest: common variables, environment variables,
 * module override, secrets from the store. Variables already set on the run
 * configuration are handled by the caller and always win.
 */
class VariableResolver(
    private val state: EnvironmentsState,
    private val secrets: SecretStore,
) {
    fun resolve(
        environmentName: String,
        moduleName: String?,
    ): Resolution {
        val env = state.environment(environmentName) ?: return Resolution.empty(missingEnvironment = environmentName)
        val merged = LinkedHashMap(state.commonVariables)
        merged.putAll(env.variables)
        merged.putAll(state.overridesFor(moduleName))
        val missing = ArrayList<String>()
        for (key in env.secretKeys) {
            val value = secrets.get(environmentName, key)
            if (value != null) merged[key] = value else missing += key
        }
        return Resolution(merged, missingSecrets = missing)
    }
}

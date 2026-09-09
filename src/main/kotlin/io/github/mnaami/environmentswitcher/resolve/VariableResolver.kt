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
 * With [EnvironmentsState.expandVariables] on, `${NAME}` references are expanded
 * against the merged map after precedence is applied.
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
        val variables = if (state.expandVariables) expand(merged) else merged
        return Resolution(variables, missingSecrets = missing)
    }

    companion object {
        private val REFERENCE = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_.-]*)}")
        private const val MAX_PASSES = 10

        /**
         * Replaces `${NAME}` with the value of NAME from the same map, repeatedly, so chained
         * references work. Unknown names are left untouched; cycles stop after [MAX_PASSES].
         */
        fun expand(variables: Map<String, String>): LinkedHashMap<String, String> {
            val result = LinkedHashMap(variables)
            repeat(MAX_PASSES) {
                var changed = false
                for ((key, value) in result) {
                    val replaced =
                        REFERENCE.replace(value) { m ->
                            val ref = m.groupValues[1]
                            if (ref != key && result.containsKey(ref)) result.getValue(ref) else m.value
                        }
                    if (replaced != value) {
                        result[key] = replaced
                        changed = true
                    }
                }
                if (!changed) return result
            }
            return result
        }
    }
}

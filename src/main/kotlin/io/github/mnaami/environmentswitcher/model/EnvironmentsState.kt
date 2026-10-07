package io.github.mnaami.environmentswitcher.model

import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

/** Variables applied only to run configurations of one module (e.g. a different PORT per service). */
@Tag("override")
class ModuleOverride() {
    constructor(moduleName: String, variables: Map<String, String>, disabledKeys: Collection<String> = emptyList()) : this() {
        this.moduleName = moduleName
        this.variables = LinkedHashMap(variables)
        this.disabledKeys = disabledKeys.toMutableList()
    }

    var moduleName: String = ""

    @MapAnnotation(surroundWithTag = false, entryTagName = "var", keyAttributeName = "name", valueAttributeName = "value")
    var variables: MutableMap<String, String> = LinkedHashMap()

    /** Variables kept in the list but not injected. */
    @XCollection(propertyElementName = "disabled", elementName = "var", valueAttributeName = "name")
    var disabledKeys: MutableList<String> = ArrayList()

    override fun equals(other: Any?): Boolean =
        other is ModuleOverride && other.moduleName == moduleName && other.variables == variables && other.disabledKeys == disabledKeys

    override fun hashCode(): Int = moduleName.hashCode()
}

enum class SecretStorage { PASSWORD_SAFE, PROJECT_FILE }

enum class MissingSecretPolicy { WARN, BLOCK }

enum class ConfirmScope { ONCE_PER_SESSION, EVERY_RUN }

/** Names that are treated as secrets when [EnvironmentsState.autoMarkSecrets] is on. */
val SENSITIVE_NAME: Regex = Regex("(PASSWORD|PASSWD|SECRET|TOKEN|PRIVATE_KEY|API_KEY|CREDENTIAL)", RegexOption.IGNORE_CASE)

/** Everything shared through the project file (.idea/environmentSwitcher.xml). Never holds secret values. */
class EnvironmentsState {
    var version: Int = 1

    @Property(surroundWithTag = true)
    @MapAnnotation(surroundWithTag = false, entryTagName = "var", keyAttributeName = "name", valueAttributeName = "value")
    var commonVariables: MutableMap<String, String> = LinkedHashMap()

    /** Common variables kept in the list but not injected. */
    @XCollection(propertyElementName = "disabledCommon", elementName = "var", valueAttributeName = "name")
    var disabledCommonKeys: MutableList<String> = ArrayList()

    @XCollection(propertyElementName = "environments")
    var environments: MutableList<Environment> = ArrayList()

    @XCollection(propertyElementName = "overrides")
    var overrides: MutableList<ModuleOverride> = ArrayList()

    /** Run configuration type ids that receive the variables. */
    @XCollection(propertyElementName = "targets", elementName = "type", valueAttributeName = "id")
    var targetConfigTypeIds: MutableList<String> = DEFAULT_TARGET_TYPES.toMutableList()

    // ---- team-wide policies (Settings | Tools | Environment Switcher) ----

    /** Where values marked secret live. PROJECT_FILE disables the Secret column: everything stays in this file. */
    var secretStorage: SecretStorage = SecretStorage.PASSWORD_SAFE

    /** What happens when a secret has no stored value at launch. */
    var missingSecretPolicy: MissingSecretPolicy = MissingSecretPolicy.WARN

    /** How often an environment flagged "confirm before run" asks. */
    var confirmScope: ConfirmScope = ConfirmScope.ONCE_PER_SESSION

    /** Tick Secret automatically for new variables whose name looks sensitive. */
    var autoMarkSecrets: Boolean = true

    /** Expand `${NAME}` references inside values against the resolved variables. */
    var expandVariables: Boolean = false

    fun environment(name: String): Environment? = environments.firstOrNull { it.name == name }

    fun overridesFor(moduleName: String?): Map<String, String> =
        moduleName
            ?.let { m -> overrides.firstOrNull { it.moduleName == m } }
            ?.let { o -> o.variables - o.disabledKeys.toSet() }
            .orEmpty()

    companion object {
        /** Ids of the run configuration types enabled out of the box. Plain strings: no plugin dependency. */
        val DEFAULT_TARGET_TYPES: List<String> =
            listOf(
                "Application",
                "JarApplication",
                "SpringBootApplicationConfigurationType",
            )
    }
}

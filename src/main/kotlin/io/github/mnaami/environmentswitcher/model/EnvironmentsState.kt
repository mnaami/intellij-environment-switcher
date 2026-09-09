package io.github.mnaami.environmentswitcher.model

import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

/** Variables applied only to run configurations of one module (e.g. a different PORT per service). */
@Tag("override")
class ModuleOverride() {
    constructor(moduleName: String, variables: Map<String, String>) : this() {
        this.moduleName = moduleName
        this.variables = LinkedHashMap(variables)
    }

    var moduleName: String = ""

    @MapAnnotation(surroundWithTag = false, entryTagName = "var", keyAttributeName = "name", valueAttributeName = "value")
    var variables: MutableMap<String, String> = LinkedHashMap()

    override fun equals(other: Any?): Boolean = other is ModuleOverride && other.moduleName == moduleName && other.variables == variables

    override fun hashCode(): Int = moduleName.hashCode()
}

/** Everything shared through the project file (.idea/environmentSwitcher.xml). Never holds secret values. */
class EnvironmentsState {
    var version: Int = 1

    @Property(surroundWithTag = true)
    @MapAnnotation(surroundWithTag = false, entryTagName = "var", keyAttributeName = "name", valueAttributeName = "value")
    var commonVariables: MutableMap<String, String> = LinkedHashMap()

    @XCollection(propertyElementName = "environments")
    var environments: MutableList<Environment> = ArrayList()

    @XCollection(propertyElementName = "overrides")
    var overrides: MutableList<ModuleOverride> = ArrayList()

    /** Run configuration type ids that receive the variables. */
    @XCollection(propertyElementName = "targets", elementName = "type", valueAttributeName = "id")
    var targetConfigTypeIds: MutableList<String> = DEFAULT_TARGET_TYPES.toMutableList()

    fun environment(name: String): Environment? = environments.firstOrNull { it.name == name }

    fun overridesFor(moduleName: String?): Map<String, String> =
        moduleName?.let { m -> overrides.firstOrNull { it.moduleName == m }?.variables }.orEmpty()

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

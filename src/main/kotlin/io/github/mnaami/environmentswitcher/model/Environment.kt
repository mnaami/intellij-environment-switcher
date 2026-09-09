package io.github.mnaami.environmentswitcher.model

import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

/**
 * One named environment. Only variable *names* of secrets are stored here;
 * their values live in the IDE password safe (see SecretStore).
 */
@Tag("environment")
class Environment() {
    constructor(
        name: String,
        variables: Map<String, String> = emptyMap(),
        secretKeys: Collection<String> = emptyList(),
        color: String = DEFAULT_COLOR,
        confirmBeforeRun: Boolean = false,
    ) : this() {
        this.name = name
        this.variables = LinkedHashMap(variables)
        this.secretKeys = secretKeys.toMutableList()
        this.color = color
        this.confirmBeforeRun = confirmBeforeRun
    }

    var name: String = ""

    /** Hex colour shown as a dot in the toolbar, e.g. "#4C9AFF". */
    var color: String = DEFAULT_COLOR

    /** Ask once per session before launching anything against this environment. */
    var confirmBeforeRun: Boolean = false

    @MapAnnotation(surroundWithTag = false, entryTagName = "var", keyAttributeName = "name", valueAttributeName = "value")
    var variables: MutableMap<String, String> = LinkedHashMap()

    @XCollection(propertyElementName = "secrets", elementName = "secret", valueAttributeName = "name")
    var secretKeys: MutableList<String> = ArrayList()

    override fun equals(other: Any?): Boolean =
        other is Environment &&
            other.name == name &&
            other.color == color &&
            other.confirmBeforeRun == confirmBeforeRun &&
            other.variables == variables &&
            other.secretKeys == secretKeys

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "Environment($name, ${variables.size} vars, ${secretKeys.size} secrets)"

    companion object {
        const val DEFAULT_COLOR = "#8A8A8A"
    }
}

package io.github.mnaami.environmentswitcher

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.EnvSwitcherBundle"

object EnvSwitcherBundle : DynamicBundle(EnvSwitcherBundle::class.java, BUNDLE) {
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): String = getMessage(key, *params)
}

package io.github.mnaami.environmentswitcher.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

const val DEFAULT_PREFIX = "Env:"

class ToolbarSettingsState {
    /** Show a prefix before the environment name on the toolbar button, e.g. "Env: dev". */
    var showPrefix: Boolean = true

    /** The prefix text itself; trailing space is added automatically. */
    var prefix: String = DEFAULT_PREFIX

    /** Show the environment colour dot next to the label. */
    var showColorDot: Boolean = true
}

/** Personal display preferences; application level, not shared with the team. */
@Service(Service.Level.APP)
@State(name = "EnvironmentSwitcherToolbar", storages = [Storage("environmentSwitcher.xml")])
class ToolbarSettings : PersistentStateComponent<ToolbarSettingsState> {
    private var state = ToolbarSettingsState()

    override fun getState(): ToolbarSettingsState = state

    override fun loadState(state: ToolbarSettingsState) {
        this.state = state
    }

    companion object {
        fun getInstance(): ToolbarSettings = service()
    }
}

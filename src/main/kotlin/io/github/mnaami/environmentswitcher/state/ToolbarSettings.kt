package io.github.mnaami.environmentswitcher.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

class ToolbarSettingsState {
    /** Show "Env: dev" instead of just "dev" on the toolbar button. */
    var showPrefix: Boolean = true

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

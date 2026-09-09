package io.github.mnaami.environmentswitcher.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import io.github.mnaami.environmentswitcher.model.EnvironmentsState

/** Project-level, shared with the team through `.idea/envSwitcher.xml`. */
@Service(Service.Level.PROJECT)
@State(name = "EnvSwitcher", storages = [Storage("envSwitcher.xml")])
class EnvironmentsService : PersistentStateComponent<EnvironmentsState> {
    private var state = EnvironmentsState()

    override fun getState(): EnvironmentsState = state

    override fun loadState(state: EnvironmentsState) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    fun update(block: EnvironmentsState.() -> Unit) {
        state.block()
    }

    companion object {
        fun getInstance(project: Project): EnvironmentsService = project.service()
    }
}

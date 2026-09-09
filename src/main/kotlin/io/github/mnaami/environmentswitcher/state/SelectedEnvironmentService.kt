package io.github.mnaami.environmentswitcher.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

class SelectionState {
    var environmentName: String? = null
}

/** Listener for toolbar and other UI to refresh when the selection changes. */
fun interface SelectionListener {
    fun selectionChanged(environmentName: String?)
}

/** Per-developer choice, stored in the workspace file so it is never shared. */
@Service(Service.Level.PROJECT)
@State(name = "EnvSwitcherSelection", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SelectedEnvironmentService(
    private val project: Project,
) : PersistentStateComponent<SelectionState> {
    private var state = SelectionState()

    var environmentName: String?
        get() = state.environmentName
        set(value) {
            if (state.environmentName == value) return
            state.environmentName = value
            project.messageBus.syncPublisher(TOPIC).selectionChanged(value)
        }

    override fun getState(): SelectionState = state

    override fun loadState(state: SelectionState) {
        this.state = state
    }

    companion object {
        val TOPIC: Topic<SelectionListener> = Topic.create("EnvironmentSwitcher.selection", SelectionListener::class.java)

        fun getInstance(project: Project): SelectedEnvironmentService = project.service()
    }
}

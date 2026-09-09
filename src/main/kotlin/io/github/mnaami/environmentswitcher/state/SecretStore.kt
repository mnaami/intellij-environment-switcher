package io.github.mnaami.environmentswitcher.state

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** Secret values per (environment, key). Values never touch project files or logs. */
interface SecretStore {
    fun get(
        environment: String,
        key: String,
    ): String?

    fun set(
        environment: String,
        key: String,
        value: String?,
    )

    fun delete(
        environment: String,
        key: String,
    ) = set(environment, key, null)
}

/** In-memory implementation for tests and previews. */
class InMemorySecretStore : SecretStore {
    private val values = HashMap<Pair<String, String>, String>()

    override fun get(
        environment: String,
        key: String,
    ): String? = values[environment to key]

    override fun set(
        environment: String,
        key: String,
        value: String?,
    ) {
        if (value == null) values.remove(environment to key) else values[environment to key] = value
    }
}

/** Backed by the IDE password safe (OS keychain or KeePass, per user setting). Scoped per project. */
@Service(Service.Level.PROJECT)
class PasswordSafeSecretStore(
    private val project: Project,
) : SecretStore {
    private fun attributes(
        environment: String,
        key: String,
    ): CredentialAttributes = CredentialAttributes(generateServiceName(SUBSYSTEM, "${project.locationHash}/$environment"), key)

    override fun get(
        environment: String,
        key: String,
    ): String? = PasswordSafe.instance.getPassword(attributes(environment, key))

    override fun set(
        environment: String,
        key: String,
        value: String?,
    ) {
        PasswordSafe.instance.set(attributes(environment, key), value?.let { Credentials(key, it) })
    }

    companion object {
        private const val SUBSYSTEM = "Env Switcher"

        fun getInstance(project: Project): PasswordSafeSecretStore = project.service()
    }
}

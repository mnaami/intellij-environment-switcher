package io.github.mnaami.environmentswitcher.state;

import com.intellij.credentialStore.CredentialAttributes;

/**
 * Kotlin compiled against 2024.2 binds {@code CredentialAttributes(service, user)} to the
 * default-args synthetic constructor, deprecated in later platforms. javac binds to the
 * plain (String, String) overload, which exists and is non-deprecated across all versions.
 */
final class CredentialAttributesFactory {
    private CredentialAttributesFactory() {}

    static CredentialAttributes create(String serviceName, String userName) {
        return new CredentialAttributes(serviceName, userName);
    }
}

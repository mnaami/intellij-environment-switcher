package io.github.mnaami.environmentswitcher

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The plugin is generic: no product-specific names may leak into shipped sources or resources. */
class NoVendorStringsTest {
    private val forbidden = Regex("snapsim|ooredoo|wataniya", RegexOption.IGNORE_CASE)

    @Test
    fun `src main contains no vendor specific strings`() {
        val offenders =
            File("src/main")
                .walkTopDown()
                .filter { it.isFile }
                .filter { forbidden.containsMatchIn(it.readText()) }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "Vendor-specific strings found in: $offenders")
    }
}

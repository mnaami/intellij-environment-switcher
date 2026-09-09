package io.github.mnaami.environmentswitcher.importer

/**
 * Minimal `.env` reader: `KEY=value` per line, `#` comments, optional `export `,
 * matching surrounding quotes removed, no variable expansion, CRLF tolerant.
 */
object DotEnvParser {
    private val keyPattern = Regex("^[A-Za-z_][A-Za-z0-9_.-]*$")

    fun parse(text: String): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r').trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val body = line.removePrefix("export ").trimStart()
            val eq = body.indexOf('=')
            if (eq <= 0) continue
            val key = body.substring(0, eq).trim()
            if (!keyPattern.matches(key)) continue
            result[key] = unquote(body.substring(eq + 1).trim())
        }
        return result
    }

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            if ((first == '"' || first == '\'') && value.last() == first) return value.substring(1, value.length - 1)
        }
        // strip a trailing inline comment only when it is clearly separated from the value
        val hash = value.indexOf(" #")
        return if (hash >= 0) value.substring(0, hash).trimEnd() else value
    }
}

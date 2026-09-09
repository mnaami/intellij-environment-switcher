package io.github.mnaami.environmentswitcher.importer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DotEnvParserTest {
    @Test
    fun `parses keys values comments blanks and export prefix`() {
        val text =
            """
            # comment
            A=1

            export B=two
               C = spaced
            """.trimIndent()
        assertEquals(linkedMapOf("A" to "1", "B" to "two", "C" to "spaced"), DotEnvParser.parse(text))
    }

    @Test
    fun `keeps equals signs and url characters inside values`() {
        val parsed = DotEnvParser.parse("URL=jdbc:oracle:thin:@host:1521/db?a=1&b=2\nKEY==leading")
        assertEquals("jdbc:oracle:thin:@host:1521/db?a=1&b=2", parsed["URL"])
        assertEquals("=leading", parsed["KEY"])
    }

    @Test
    fun `strips matching quotes but keeps hash inside quotes and dollar signs`() {
        val parsed = DotEnvParser.parse("P='pa\$s#word'\nQ=\"double\"\nR=plain # inline comment\nS=a#b")
        assertEquals("pa\$s#word", parsed["P"])
        assertEquals("double", parsed["Q"])
        assertEquals("plain", parsed["R"])
        assertEquals("a#b", parsed["S"])
    }

    @Test
    fun `tolerates crlf and ignores malformed lines`() {
        val parsed = DotEnvParser.parse("A=1\r\nnot a pair\r\n=novalue\r\n1BAD=x\r\nB=\r\n")
        assertEquals(linkedMapOf("A" to "1", "B" to ""), parsed)
    }

    @Test
    fun `last duplicate wins and order is preserved`() {
        val parsed = DotEnvParser.parse("A=1\nB=2\nA=3")
        assertEquals(listOf("A", "B"), parsed.keys.toList())
        assertEquals("3", parsed["A"])
    }
}

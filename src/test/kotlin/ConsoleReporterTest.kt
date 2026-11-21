import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsoleReporterTest {
    @Test
    fun `renders one complete report without changing its format`() {
        val bytes = ByteArrayOutputStream()
        val reporter = ConsoleReporter(PrintStream(bytes))

        reporter.report(
            FindResult.Page(
                url = "https://example.test/",
                links = listOf("https://example.test/one", "https://example.test/two"),
                statusCode = 200,
            ),
        )

        assertEquals(
            """
                Visited: https://example.test/
                Links:
                  - https://example.test/one
                  - https://example.test/two

            """.trimIndent() + "\n",
            bytes.toString(Charsets.UTF_8),
        )
    }
}

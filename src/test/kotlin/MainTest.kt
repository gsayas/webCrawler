import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainTest {
    @Test
    fun `uses the default worker count`() {
        assertEquals(
            CliArguments("https://example.com/", 8),
            parseArguments(arrayOf("https://example.com")).getOrThrow(),
        )
    }

    @Test
    fun `accepts an explicit worker count`() {
        assertEquals(
            CliArguments("https://example.com/", 16),
            parseArguments(arrayOf("https://example.com", "16")).getOrThrow(),
        )
    }

    @Test
    fun `rejects invalid arguments`() {
        val invalidInputs = listOf(
            emptyArray(),
            arrayOf("not-a-url"),
            arrayOf("https://example.com", "not-a-number"),
            arrayOf("https://example.com", "0"),
            arrayOf("https://example.com", "8", "extra"),
        )

        invalidInputs.forEach { arguments ->
            assertTrue(parseArguments(arguments).isFailure)
        }
    }
}

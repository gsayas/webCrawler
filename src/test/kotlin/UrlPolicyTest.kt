import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlPolicyTest {
    @Test
    fun `normalizes fragments out of URLs`() {
        assertEquals(
            "https://example.com/a?view=full",
            UrlPolicy.normalizeOrNull("HTTPS://EXAMPLE.COM/a?view=full#section"),
        )
    }

    @Test
    fun `accepts only HTTP URLs with the exact hostname`() {
        assertNull(UrlPolicy.normalizeOrNull("mailto:hello@example.com"))
        assertTrue(UrlPolicy.isEligible("https://crawl.example.com/a", "crawl.example.com"))
        assertFalse(UrlPolicy.isEligible("https://example.com/a", "crawl.example.com"))
    }
}

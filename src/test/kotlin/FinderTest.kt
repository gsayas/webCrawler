import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FinderTest {
    private lateinit var server: TestHttpServer
    private lateinit var client: io.ktor.client.HttpClient

    @BeforeTest
    fun setUp() {
        server = TestHttpServer()
        client = createHttpClient()
    }

    @AfterTest
    fun tearDown() {
        client.close()
        server.close()
    }

    @Test
    fun `extracts and resolves all page links`() = runBlocking {
        server.respond(
            "/",
            body = """
                <a href="/about">About</a>
                <a href="https://example.com/external">External</a>
                <a href="/about">Duplicate</a>
            """.trimIndent(),
        )

        val result = assertIs<FindResult.Page>(Finder(client).find(server.url("/")))

        assertEquals(
            listOf(server.url("/about"), "https://example.com/external"),
            result.links,
        )
    }

    @Test
    fun `returns redirects without following them`() = runBlocking {
        server.respond("/start", status = 302, headers = mapOf("Location" to "/target"))
        server.respond("/target", body = "target")

        val result = assertIs<FindResult.Redirect>(Finder(client).find(server.url("/start")))

        assertEquals(server.url("/target"), result.target)
        assertEquals(0, server.requestCount("/target"))
    }

    @Test
    fun `does not parse links from non-HTML responses`() = runBlocking {
        server.respond(
            "/document.pdf",
            body = "<a href=\"/hidden\">Not really HTML</a>",
            headers = mapOf("Content-Type" to "application/pdf"),
        )

        val result = assertIs<FindResult.Page>(Finder(client).find(server.url("/document.pdf")))

        assertTrue(result.links.isEmpty())
    }

    @Test
    fun `does not parse links from unsuccessful responses`() = runBlocking {
        server.respond("/missing", status = 404, body = "<a href=\"/hidden\">Hidden</a>")

        val result = assertIs<FindResult.Page>(Finder(client).find(server.url("/missing")))

        assertTrue(result.links.isEmpty())
    }

    @Test
    fun `returns a failure when the connection cannot be established`() = runBlocking {
        val unusedPort = ServerSocket(0).use { it.localPort }

        val result = Finder(client).find("http://127.0.0.1:$unusedPort/")

        assertIs<FindResult.Failure>(result)
        Unit
    }

    @Test
    fun `allows more than five concurrent requests to the same host`() = runBlocking {
        val requestCount = 6
        val entered = CountDownLatch(requestCount)
        val release = CountDownLatch(1)
        repeat(requestCount) { index ->
            server.respond("/$index", beforeResponse = {
                entered.countDown()
                release.await()
            })
        }

        val requests = List(requestCount) { index ->
            async { client.get(server.url("/$index")) }
        }
        val allEntered = withContext(Dispatchers.IO) {
            entered.await(2, TimeUnit.SECONDS)
        }
        release.countDown()
        requests.awaitAll()

        assertTrue(allEntered)
    }
}

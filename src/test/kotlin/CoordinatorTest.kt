import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CoordinatorTest {
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
    fun `visits cycles and duplicate links once and does not follow external links`() = runBlocking {
        server.respond(
            "/",
            body = """
                <a href="/a">A</a>
                <a href="/a">A again</a>
                <a href="https://example.com/external">External</a>
            """.trimIndent(),
        )
        server.respond("/a", body = "<a href=\"/\">Home</a>")
        val reported = mutableListOf<FindResult>()

        Coordinator(Finder(client), reported::add).coordinate(server.url("/"), workerCount = 2)

        assertEquals(setOf(server.url("/"), server.url("/a")), reported.map { it.url }.toSet())
        assertEquals(1, server.requestCount("/"))
        assertEquals(1, server.requestCount("/a"))
        assertTrue(reported.first { it.url == server.url("/") }.links.contains("https://example.com/external"))
    }

    @Test
    fun `does not follow redirects to another hostname`() = runBlocking {
        val externalTarget = server.url("/target").replace("127.0.0.1", "localhost")
        server.respond("/", status = 302, headers = mapOf("Location" to externalTarget))
        server.respond("/target", body = "target")
        val reported = mutableListOf<FindResult>()

        Coordinator(Finder(client), reported::add).coordinate(server.url("/"), workerCount = 2)

        assertEquals(listOf(server.url("/")), reported.map { it.url })
        assertEquals(0, server.requestCount("/target"))
    }

    @Test
    fun `continues crawling after an expected request failure`() = runBlocking {
        val root = "http://example.test/"
        val failedUrl = "${root}failed"
        val successfulUrl = "${root}successful"
        val finder = PageFinder { url ->
            when (url) {
                root -> FindResult.Page(url, listOf(failedUrl, successfulUrl), 200)
                failedUrl -> FindResult.Failure(url, "connection failed")
                else -> FindResult.Page(url, emptyList(), 200)
            }
        }
        val reported = mutableListOf<FindResult>()

        Coordinator(finder, reported::add).coordinate(root, workerCount = 2)

        assertEquals(setOf(root, failedUrl, successfulUrl), reported.map { it.url }.toSet())
        assertIs<FindResult.Failure>(reported.single { it.url == failedUrl })
        Unit
    }

    @Test
    fun `never exceeds the worker limit`() = runBlocking {
        val root = "http://example.test/"
        val children = (1..3).map { "$root$it" }
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)
        val finder = PageFinder { url ->
            if (url == root) {
                FindResult.Page(url, children, 200)
            } else {
                val current = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, current) }
                entered.send(Unit)
                release.receive()
                active.decrementAndGet()
                FindResult.Page(url, emptyList(), 200)
            }
        }

        val crawl = async {
            Coordinator(finder) {}.coordinate(root, workerCount = 2)
        }
        repeat(2) { entered.receive() }
        repeat(3) { release.send(Unit) }
        crawl.await()

        assertEquals(2, maximum.get())
    }

    @Test
    fun `propagates unexpected worker failures`() = runBlocking {
        val coordinator = Coordinator(PageFinder { error("unexpected") }) {}

        assertFailsWith<IllegalStateException> {
            withTimeout(1_000) {
                coordinator.coordinate("http://example.test/", workerCount = 2)
            }
        }
        Unit
    }
}

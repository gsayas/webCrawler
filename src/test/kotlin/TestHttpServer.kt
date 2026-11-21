import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TestHttpServer : AutoCloseable {
    private data class Stub(
        val status: Int,
        val body: String,
        val headers: Map<String, String>,
        val beforeResponse: () -> Unit,
    )

    private val stubs = ConcurrentHashMap<String, Stub>()
    private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> handle(exchange) }
        this.executor = this@TestHttpServer.executor
        start()
    }

    fun respond(
        path: String,
        status: Int = 200,
        body: String = "",
        headers: Map<String, String> = mapOf("Content-Type" to "text/html; charset=utf-8"),
        beforeResponse: () -> Unit = {},
    ) {
        stubs[path] = Stub(status, body, headers, beforeResponse)
    }

    fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"

    fun requestCount(path: String): Int = requestCounts[path]?.get() ?: 0

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        requestCounts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
        val stub = stubs[path] ?: Stub(404, "Not found", emptyMap()) {}
        stub.beforeResponse()
        stub.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
        val body = stub.body.toByteArray()
        exchange.sendResponseHeaders(stub.status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}

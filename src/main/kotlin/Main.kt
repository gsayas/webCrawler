import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import java.io.PrintStream
import kotlin.system.exitProcess

private const val DEFAULT_WORKER_COUNT = 8
private const val USAGE = "Usage: web-crawler <start-url> [worker-count]"

internal data class CliArguments(
    val startUrl: String,
    val workerCount: Int,
)

fun main(args: Array<String>) {
    val arguments = parseArguments(args).getOrElse { error ->
        System.err.println("Error: ${error.message}")
        System.err.println(USAGE)
        exitProcess(1)
    }

    val client = createHttpClient(arguments.workerCount)

    try {
        runBlocking {
            Coordinator(Finder(client), ConsoleReporter()).coordinate(arguments.startUrl, arguments.workerCount)
        }
    } finally {
        client.close()
    }
}

internal fun parseArguments(args: Array<String>): Result<CliArguments> = runCatching {
    require(args.isNotEmpty()) { "Start URL is required" }
    require(args.size <= 2) { "Too many arguments" }

    val startUrl = UrlPolicy.validateStart(args[0])
    val workerCount = args.getOrNull(1)?.let { rawCount ->
        rawCount.toIntOrNull()
            ?: throw IllegalArgumentException("Worker count must be a positive integer")
    } ?: DEFAULT_WORKER_COUNT
    require(workerCount > 0) { "Worker count must be a positive integer" }

    CliArguments(startUrl, workerCount)
}

fun createHttpClient(workerCount: Int = DEFAULT_WORKER_COUNT): HttpClient {
    require(workerCount > 0) { "workerCount must be greater than zero" }

    return HttpClient(OkHttp) {
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 5_000
            socketTimeoutMillis = 5_000
        }
        engine {
            config {
                dispatcher(
                    Dispatcher().apply {
                        maxRequests = workerCount
                        maxRequestsPerHost = workerCount
                    },
                )
                followRedirects(false)
                followSslRedirects(false)
            }
        }
    }
}

class ConsoleReporter(private val output: PrintStream = System.out) : CrawlReporter {
    override fun report(result: FindResult) {
        val report = buildString {
            appendLine("Visited: ${result.url}")
            appendLine("Links:")
            result.links.forEach { appendLine("  - $it") }

            when (result) {
                is FindResult.Failure -> appendLine("Error: ${result.message}")
                is FindResult.Redirect -> result.target?.let { appendLine("Redirect: $it") }
                is FindResult.Page -> Unit
            }
            appendLine()
        }

        output.print(report)
    }
}

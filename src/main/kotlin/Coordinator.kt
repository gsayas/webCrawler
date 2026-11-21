import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

fun interface CrawlReporter {
    fun report(result: FindResult)
}

class Coordinator(
    private val finder: PageFinder,
    private val reporter: CrawlReporter,
) {
    suspend fun coordinate(startUrl: String, workerCount: Int) {
        require(workerCount > 0) { "workerCount must be greater than zero" }

        val normalizedStart = UrlPolicy.validateStart(startUrl)
        val allowedHost = URI(normalizedStart).host
        val scheduled = HashSet<String>()
        val pending = ArrayDeque<String>()
        var inFlight = 0

        fun schedule(discoveredUrl: String) {
            val candidate = UrlPolicy.normalizeOrNull(discoveredUrl) ?: return
            if (UrlPolicy.isEligible(candidate, allowedHost) && scheduled.add(candidate)) {
                pending.addLast(candidate)
            }
        }

        coroutineScope {
            val work = Channel<String>(workerCount)
            val results = Channel<FindResult>(workerCount)
            val workers = startWorkers(workerCount, work, results)

            try {
                schedule(normalizedStart)

                while (pending.isNotEmpty() || inFlight > 0) {
                    while (pending.isNotEmpty() && inFlight < workerCount) {
                        work.send(pending.removeFirst())
                        inFlight += 1
                    }

                    val result = results.receive()
                    inFlight -= 1
                    reporter.report(result)
                    result.links.forEach(::schedule)

                    if (result is FindResult.Redirect) {
                        result.target?.let(::schedule)
                    }
                }
            } finally {
                work.close()
                workers.forEach { worker ->
                    worker.cancel()
                    withContext(NonCancellable) { worker.join() }
                }
                results.close()
            }
        }
    }

    private fun CoroutineScope.startWorkers(
        workerCount: Int,
        work: Channel<String>,
        results: Channel<FindResult>,
    ) = List(workerCount) {
        launch {
            for (url in work) {
                results.send(finder.find(url))
            }
        }
    }
}

object UrlPolicy {
    fun validateStart(rawUrl: String): String =
        normalizeOrNull(rawUrl)
            ?: throw IllegalArgumentException("Start URL must be an absolute HTTP(S) URL")

    fun normalizeOrNull(rawUrl: String): String? = runCatching {
        val input = URI(rawUrl.trim()).normalize()
        val scheme = input.scheme?.lowercase()
        val host = input.host?.lowercase()
        require(scheme == "http" || scheme == "https")
        require(host != null)

        URI(
            scheme,
            input.userInfo,
            host,
            input.port,
            input.path.ifEmpty { "/" },
            input.query,
            null,
        ).toASCIIString()
    }.getOrNull()

    fun isEligible(normalizedUrl: String, allowedHost: String): Boolean =
        runCatching { URI(normalizedUrl).host.equals(allowedHost, ignoreCase = true) }
            .getOrDefault(false)
}

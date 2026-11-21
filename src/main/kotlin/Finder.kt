import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI

sealed interface FindResult {
    val url: String
    val links: List<String>

    data class Page(
        override val url: String,
        override val links: List<String>,
        val statusCode: Int,
    ) : FindResult

    data class Redirect(
        override val url: String,
        val target: String?,
        val statusCode: Int,
    ) : FindResult {
        override val links: List<String> = emptyList()
    }

    data class Failure(
        override val url: String,
        val message: String,
    ) : FindResult {
        override val links: List<String> = emptyList()
    }
}

fun interface PageFinder {
    suspend fun find(url: String): FindResult
}

class Finder(private val client: HttpClient) : PageFinder {
    override suspend fun find(url: String): FindResult = try {
        val response = client.get(url)
        val statusCode = response.status.value

        when {
            statusCode in 300..399 -> {
                val target = response.headers[HttpHeaders.Location]
                    ?.let { location -> resolve(url, location) }
                response.cancel()
                FindResult.Redirect(url, target, statusCode)
            }

            statusCode !in 200..299 || response.contentType()?.match(ContentType.Text.Html) != true -> {
                response.cancel()
                FindResult.Page(url, emptyList(), statusCode)
            }

            else -> {
                val document = Jsoup.parse(response.bodyAsText(), url)
                val links = document.select("a[href]")
                    .map { it.attr("abs:href") }
                    .filter { it.isNotBlank() }
                    .distinct()
                FindResult.Page(url, links, statusCode)
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: IOException) {
        FindResult.Failure(url, exception.message ?: exception::class.simpleName.orEmpty())
    }

    private fun resolve(baseUrl: String, location: String): String? =
        runCatching { URI(baseUrl).resolve(location).toString() }.getOrNull()
}

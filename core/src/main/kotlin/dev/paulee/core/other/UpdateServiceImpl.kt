package dev.paulee.core.other

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.paulee.api.other.IUpdateService
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory.getLogger
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object UpdateServiceImpl : IUpdateService {

    private val logger = getLogger(UpdateServiceImpl::class.java)

    private val objectMapper = jacksonObjectMapper()

    override suspend fun isNewerVersionAvailable(): Boolean {
        val versionProperty = System.getProperty("app.version") ?: return false

        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build()

        val latestReleaseRequest =
            HttpRequest.newBuilder(URI("https://api.github.com/repos/Paulanerus/TextVariantExplorer/releases/latest"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build()

        val response = try {
            client.sendAsync(latestReleaseRequest, HttpResponse.BodyHandlers.ofString()).await()
        } catch (e: Exception) {
            logger.error("Exception: Failed to check for newer version", e)
            return false
        } finally {
            client.shutdownNow()
        }

        return response.takeIf { it.statusCode() == HttpURLConnection.HTTP_OK }
            ?.let {
                runCatching { objectMapper.readTree(it.body()) }.getOrElse { throwable ->
                    logger.error(
                        "Exception: Failed to parse response",
                        throwable
                    )

                    null
                }?.get("tag_name")?.textValue()
            }
            ?.let { compareVersions(versionProperty, it) }
            ?: false
    }

    private fun compareVersions(versionProperty: String, versionApi: String): Boolean {
        val currentVersion = versionProperty.split(".").map { it.toInt() }
        val apiVersion = versionApi.removePrefix("v").split(".").map { it.toInt() }

        val isAvailable = apiVersion.zip(currentVersion)
            .map { (api, current) -> api.compareTo(current) }
            .firstOrNull { it != 0 }
            ?.let { it > 0 } ?: false

        if (isAvailable)
            logger.info("Newer version available: $versionApi")

        return isAvailable
    }
}
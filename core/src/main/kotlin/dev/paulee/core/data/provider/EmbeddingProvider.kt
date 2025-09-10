package dev.paulee.core.data.provider

import dev.paulee.api.internal.Embedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory.getLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.*

internal object EmbeddingProvider {

    private val logger = getLogger(EmbeddingProvider::class.java)

    private const val HF_URL = "https://huggingface.co/%s/resolve/main"

    @OptIn(ExperimentalPathApi::class)
    suspend fun downloadModel(model: Embedding.Models, path: Path, onProgress: (progress: Int) -> Unit) =
        withContext(Dispatchers.IO) {
            logger.info("Downloading model: ${model.name}")

            val baseUrl = HF_URL.format(model.id)

            val files = listOf(
                model.modelData.model,
                model.modelData.modelData,
                model.modelData.tokenizer,
                model.modelData.tokenizerConfig
            ).filter { it.isNotBlank() }.map { it to URI.create("$baseUrl/$it") }

            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()

            val modelPath = path.resolve(model.name).apply {
                if (exists()) {
                    logger.info("Deleting existing model directory for ${model.name}")
                    deleteRecursively()
                }

                createDirectories()
            }

            var totalBytes = 0L
            files.forEach { (file, uri) ->
                val request = HttpRequest.newBuilder()
                    .uri(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.discarding())

                if (response.statusCode() !in 200..299) {
                    logger.error("Failed to retrieve $file size: ${response.statusCode()}")
                    return@forEach
                }

                val bytes = response.headers().firstValueAsLong("content-length").orElse(0L)

                if (bytes == 0L) {
                    logger.warn("Zero byte file: $file")
                    return@forEach
                }

                totalBytes += bytes
            }

            logger.info("Total download size $totalBytes bytes")

            onProgress(0)

            var downloadSize = 0L
            var lastPercent = -1
            files.forEach { (file, uri) ->
                val request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

                if (response.statusCode() !in 200..299) {
                    logger.error("Failed to download $file: ${response.statusCode()}")
                    return@forEach
                }

                val filePath = modelPath.resolve(file)

                filePath.parent?.let { if (it.notExists()) it.createDirectories() }

                try {
                    response.body().use { input ->
                        filePath.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                            while (true) {
                                coroutineContext.ensureActive()

                                val bytesRead = input.read(buffer)

                                if (bytesRead == -1) break

                                output.write(buffer, 0, bytesRead)

                                downloadSize += bytesRead

                                val percentage = ((downloadSize * 100) / totalBytes).toInt()

                                if (percentage != lastPercent) {
                                    lastPercent = percentage
                                    onProgress(percentage.coerceAtMost(99))
                                }
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    runCatching { modelPath.deleteIfExists() }
                        .onFailure { logger.error("Failed to delete model dir $modelPath", it) }

                    logger.info("Download cancelled.")

                } catch (e: Exception) {
                    runCatching { filePath.deleteIfExists() }
                        .onFailure { logger.error("Failed to delete $file", it) }

                    logger.error("Failed to download $file", e)
                }
            }

            onProgress(100)

            logger.info("Downloaded model: ${model.name}")
        }
}
package dev.paulee.core.data.provider

import ai.djl.huggingface.tokenizers.Encoding
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dev.paulee.api.internal.Embedding
import dev.paulee.core.data.FileService
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
import kotlin.io.DEFAULT_BUFFER_SIZE
import kotlin.io.path.*
import kotlin.use

internal object EmbeddingProvider {

    private val logger = getLogger(EmbeddingProvider::class.java)

    private const val HF_URL = "https://huggingface.co/%s/resolve/main"

    private val env = OrtEnvironment.getEnvironment()

    private val tokenizer = mutableMapOf<Embedding.Model, HuggingFaceTokenizer>()

    private val sessions = mutableMapOf<Embedding.Model, OrtSession?>()

    fun registerTokenizer(model: Embedding.Model) {
        val modelName = model.name

        val modelPath = FileService.modelsDir.resolve(modelName)

        if (modelPath.notExists()) {
            logger.error("Model directory for $modelName does not exist.")
            return
        }

        try {
            tokenizer.computeIfAbsent(model) {
                HuggingFaceTokenizer.builder()
                    .optTokenizerConfigPath(modelPath.resolve(model.modelData.tokenizerConfig).toString())
                    .optTokenizerPath(modelPath.resolve(model.modelData.tokenizer))

                    // TODO store/read values instead of fixed ones
                    .optMaxLength(2048)
                    .optTruncation(true)
                    .optPadding(true)
                    .build()
            }

            logger.info("Registered tokenizer for $modelName.")
        } catch (e: Exception) {
            logger.error("Failed to register tokenizer for $modelName", e)
        }
    }

    fun createEmbeddings(model: Embedding.Model, query: Boolean, values: List<String>): Array<FloatArray> {
        val embeddings = when (model) {
            Embedding.Model.EmbeddingGemma -> {
                val texts =
                    values.map { if (query) "task: search result | query: $it" else "title: none | text: $it" }

                createRawEmbeddings(model, texts)
            }
        }

        return embeddings
    }

    @OptIn(ExperimentalPathApi::class)
    suspend fun downloadModel(model: Embedding.Model, path: Path, onProgress: (progress: Int) -> Unit) =
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

    fun close() {
        tokenizer.values.forEach { it.close() }

        sessions.values.forEach { it?.close() }

        env.close()
    }

    private fun tokenize(model: Embedding.Model, values: List<String>): Pair<Array<LongArray>, Array<LongArray>>? {
        if (values.isEmpty()) return null

        val encodings: Array<Encoding> = tokenizer[model]?.batchEncode(values) ?: return null

        val seqLen = encodings[0].ids.size
        val batch = encodings.size

        encodings.forEach { encoding ->
            require(encoding.ids.size == seqLen && encoding.attentionMask.size == seqLen) {
                "Tokenizer did not produce uniform lengths; check padding settings."
            }
        }

        val inputIds = Array(batch) { i -> encodings[i].ids }
        val attentionMask = Array(batch) { i -> encodings[i].attentionMask }

        return inputIds to attentionMask
    }

    private fun createRawEmbeddings(model: Embedding.Model, values: List<String>): Array<FloatArray> {
        val (inputIds, attentionMask) = tokenize(model, values) ?: return emptyArray()

        val session = sessions.getOrPut(model) {
            createSession(model)
        }

        if (session == null) return emptyArray()

        return OnnxTensor.createTensor(env, inputIds).use { idsTensor ->
            OnnxTensor.createTensor(env, attentionMask).use { maskTensor ->
                val inputs = mapOf(
                    "input_ids" to idsTensor,
                    "attention_mask" to maskTensor
                )
                session.run(inputs).use { result ->
                    val outName = session.outputNames.firstOrNull { it.contains("sentence_embedding") }

                    @Suppress("UNCHECKED_CAST")
                    val embeddings: Array<FloatArray> = when {
                        outName != null -> {
                            val ov = result.get(outName)
                                .orElseThrow { IllegalStateException("No output named $outName") }
                            (ov as OnnxTensor).value as Array<FloatArray>
                        }

                        else -> {
                            val ov = result.get(1)
                            (ov as OnnxTensor).value as Array<FloatArray>
                        }
                    }

                    embeddings
                }
            }
        }
    }

    private fun createSession(model: Embedding.Model): OrtSession? {
        return runCatching {
            env.createSession(
                FileService.modelsDir.resolve(model.name).resolve(model.modelData.model).toString(),
                OrtSession.SessionOptions()
            )
        }.getOrElse {
            logger.error("Failed to create session for model ${model.name}", it)
            null
        }
    }
}
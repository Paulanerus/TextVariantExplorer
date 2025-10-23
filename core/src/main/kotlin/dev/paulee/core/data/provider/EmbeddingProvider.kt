package dev.paulee.core.data.provider

import ai.djl.huggingface.tokenizers.Encoding
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import java.text.Normalizer
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.*
import kotlin.math.max
import kotlin.math.sqrt

internal data class EmbKey(val model: Embedding.Model, val value: String)

internal object EmbeddingProvider {

    private val logger = getLogger(EmbeddingProvider::class.java)

    private const val HF_URL = "https://huggingface.co/%s/resolve/main"

    private val lazyEnv = lazy {
        runCatching {
            OrtEnvironment.getEnvironment().apply {
                setTelemetry(false)
            }
        }.getOrElse {
            logger.error("Failed to create environment.", it)
            null
        }
    }

    private val tokenizer = mutableMapOf<Embedding.Model, HuggingFaceTokenizer>()

    private val sessions = mutableMapOf<Embedding.Model, OrtSession?>()

    private val availableProvider by lazy {
        runCatching { OrtEnvironment.getAvailableProviders() }
            .getOrDefault(emptyList<OrtProvider>()).orEmpty().mapNotNull { it }.toSet()
    }

    private val canCuda = with(FileService.Platform) {
        OrtProvider.CUDA in availableProvider && isCuda12xInstalled && isCuDNNInstalled
    }

    private val lazyOptions = lazy {
        runCatching {
            OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setMemoryPatternOptimization(true)
                setInterOpNumThreads(Runtime.getRuntime().availableProcessors())
                setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors() / 2))

                if (!FileService.Platform.isMacOS && canCuda) {
                    logger.info("Selecting CUDA provider.")
                    addCUDA()
                }

                addCPU(true)
            }
        }.getOrElse {
            logger.error("Failed to create session options.", it)
            null
        }
    }

    private const val CACHE_SIZE = 60_000

    private val embeddingCache: Cache<EmbKey, FloatArray> =
        Caffeine.newBuilder()
            .initialCapacity(CACHE_SIZE)
            .maximumSize(CACHE_SIZE.toLong())
            .build()

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
                    .optMaxLength(model.modelData.maxLength)
                    .optTruncation(true)
                    .optPadding(true)
                    .build()
            }

            logger.info("Registered tokenizer for $modelName.")
        } catch (e: Exception) {
            logger.error("Failed to register tokenizer for $modelName", e)
        }
    }

    fun createEmbeddings(model: Embedding.Model, values: List<String>, query: Boolean = false): Array<FloatArray> =
        when (model) {
            Embedding.Model.EmbeddingGemma -> {
                val texts = values.map { if (query) "task: search result | query: $it" else "title: none | text: $it" }

                createRawEmbeddings(model, texts)
            }

            Embedding.Model.AncientGreekBert -> {
                val texts = values.map { it.stripAccentsAndLowercase() }

                createRawEmbeddings(model, texts)
            }

            Embedding.Model.GreekTransfer -> {
                createRawEmbeddings(model, values)
            }
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

            if (totalBytes == 0L) {
                logger.error("No files to download.")
                return@withContext
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
                    runCatching { if (modelPath.exists()) modelPath.deleteRecursively() }
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

        lazyOptions.closeIfInitialized()
        lazyEnv.closeIfInitialized()
    }

    fun finish() {
        embeddingCache.invalidateAll()
        embeddingCache.cleanUp()
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

    @Suppress("UNCHECKED_CAST")
    private fun createRawEmbeddings(model: Embedding.Model, values: List<String>): Array<FloatArray> {
        if (lazyEnv.value == null || values.isEmpty()) return emptyArray()

        val embBatch = Array<FloatArray?>(values.size) { null }

        val missingIndices = ArrayList<Int>(values.size)
        val missingValues = ArrayList<String>(values.size)

        for (i in values.indices) {
            val embedding = embeddingCache.getIfPresent(EmbKey(model, values[i]))

            if (embedding != null) embBatch[i] = embedding
            else {
                missingValues += values[i]
                missingIndices += i
            }
        }

        if (missingValues.isEmpty()) return embBatch as Array<FloatArray>

        val (inputIds, attentionMask) = tokenize(model, missingValues) ?: return emptyArray()

        val session = sessions.getOrPut(model) {
            createSession(model)
        }

        if (session == null) return emptyArray()

        fun runSession(sessionInputs: Map<String, OnnxTensor>): Array<FloatArray> {
            session.run(sessionInputs).use { result ->
                val batch = when (model) {
                    Embedding.Model.AncientGreekBert, Embedding.Model.GreekTransfer -> {
                        val ov = result.get("last_hidden_state")
                            .orElseThrow { IllegalStateException("No output named last_hidden_state") }

                        val lastHidden = (ov as OnnxTensor).value as Array<Array<FloatArray>>

                        val batchSize = lastHidden.size
                        val seqLen = lastHidden[0].size
                        val dim = lastHidden[0][0].size

                        val embeddings = Array(batchSize) { FloatArray(dim) }

                        for (i in 0 until batchSize) {
                            var validTokens = 0f

                            for (j in 0 until seqLen) {
                                if (attentionMask[i][j] != 0L) {
                                    val tok = lastHidden[i][j]

                                    for (k in 0 until dim)
                                        embeddings[i][k] += tok[k]

                                    validTokens += 1f
                                }
                            }

                            if (validTokens == 0f)
                                validTokens = 1f

                            for (k in 0 until dim)
                                embeddings[i][k] /= validTokens

                            var normSq = 0.0
                            for (k in 0 until dim) {
                                val v = embeddings[i][k]

                                normSq += (v * v).toDouble()
                            }

                            val norm = sqrt(normSq).coerceAtLeast(1e-12)

                            for (k in 0 until dim)
                                embeddings[i][k] = (embeddings[i][k] / norm).toFloat()
                        }

                        embeddings
                    }

                    else -> {
                        val ov = result.get("sentence_embedding")
                            .orElseThrow { IllegalStateException("No output named sentence_embedding") }

                        (ov as OnnxTensor).value as Array<FloatArray>
                    }
                }

                for (i in batch.indices) {
                    val idx = missingIndices[i]

                    val embedding = batch[i]

                    embeddingCache.put(EmbKey(model, values[idx]), embedding)
                    embBatch[idx] = embedding
                }

                return embBatch.requireNoNulls()
            }
        }

        return OnnxTensor.createTensor(lazyEnv.value, inputIds).use { idsTensor ->
            OnnxTensor.createTensor(lazyEnv.value, attentionMask).use { maskTensor ->
                val inputs = mutableMapOf(
                    "input_ids" to idsTensor, "attention_mask" to maskTensor
                )

                val expectsTokenTypes = session.inputNames.any { it.contains("token_type_ids") }

                if (expectsTokenTypes) {
                    val tokenTypes = Array(inputIds.size) { LongArray(inputIds[0].size) { 0L } }

                    OnnxTensor.createTensor(lazyEnv.value, tokenTypes).use { typeTensor ->
                        inputs["token_type_ids"] = typeTensor

                        runSession(inputs)
                    }
                } else runSession(inputs)
            }
        }
    }

    private fun createSession(model: Embedding.Model): OrtSession? {
        if (lazyEnv.value == null || lazyOptions.value == null) return null

        return runCatching {
            lazyEnv.value!!.createSession(
                FileService.modelsDir.resolve(model.name).resolve(model.modelData.model).toString(),
                lazyOptions.value
            )
        }.getOrElse {
            logger.error("Failed to create session for model ${model.name}", it)
            null
        }
    }

    private fun String.stripAccentsAndLowercase(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        val nonSpacingMark = Character.NON_SPACING_MARK.toInt()

        val withoutAccents = buildString {
            for (c in normalized) {
                if (Character.getType(c) != nonSpacingMark) append(c)
            }
        }

        return withoutAccents.lowercase()
    }

    private fun <T : AutoCloseable> Lazy<T?>.closeIfInitialized() {
        if (isInitialized()) value?.close()
    }
}
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
import org.duckdb.DuckDBConnection
import org.slf4j.LoggerFactory.getLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.DEFAULT_BUFFER_SIZE
import kotlin.io.path.*
import kotlin.use

internal object EmbeddingProvider {

    private val logger = getLogger(EmbeddingProvider::class.java)

    private const val HF_URL = "https://huggingface.co/%s/resolve/main"

    private val env = OrtEnvironment.getEnvironment()

    private val connection =
        DriverManager.getConnection("jdbc:duckdb:${FileService.appDir}/embeddings") as DuckDBConnection

    private val tokenizer = mutableMapOf<Embedding.Model, HuggingFaceTokenizer>()

    private val sessions = mutableMapOf<Embedding.Model, OrtSession?>()

    private val models = mutableMapOf<String, Embedding.Model>()

    init {
        connection.createStatement().use {
            it.execute("INSTALL vss; LOAD vss")

            it.execute("SET hnsw_enable_experimental_persistence = true;")
        }
    }

    fun createTable(name: String, model: Embedding.Model) {
        if (name.isBlank()) return

        val tableName = name.replace(".", "_")

        //TODO Check for existing model

        try {
            connection.createStatement().use {
                it.execute("CREATE TABLE IF NOT EXISTS $tableName (id BIGINT PRIMARY KEY, embedding FLOAT[${model.modelData.dimension}]);")
                it.execute("CREATE INDEX IF NOT EXISTS ${tableName}_hnsw ON $tableName USING HNSW(embedding) WITH (metric='ip');")
            }

            tokenizer.computeIfAbsent(model) {
                HuggingFaceTokenizer.builder()
                    .optTokenizerConfigPath(
                        FileService.modelsDir.resolve(model.name).resolve(model.modelData.tokenizerConfig).toString()
                    )
                    .optTokenizerPath(FileService.modelsDir.resolve(model.name).resolve(model.modelData.tokenizer))

                    // TODO store/read values instead of fixed ones
                    .optMaxLength(2048)
                    .optTruncation(true)
                    .optPadding(true)
                    .build()
            }

            models[tableName] = model

            logger.info(tableName)
        } catch (e: Exception) {
            logger.error("Failed to create table $tableName", e)
        }
    }

    fun topKMatching(name: String, query: String, k: Int): Set<Long> {
        if (name.isBlank() || query.isBlank()) return emptySet()

        val tableName = name.replace(".", "_")

        val model = models[tableName] ?: return emptySet()

        val embedding = when (model) {
            Embedding.Model.EmbeddingGemma -> {
                createRawEmbeddings(model, listOf("task: search result | query: $query"))[0]
            }
        }

        val limit = if (k < 1) 1 else 1

        return connection.prepareStatement("SELECT id FROM $tableName ORDER BY array_negative_inner_product(embedding, ?::FLOAT[${model.modelData.dimension}]) LIMIT ?;")
            .use { ps ->
                val qArr: java.sql.Array = connection.createArrayOf("FLOAT", embedding.toTypedArray())
                ps.setArray(1, qArr)
                ps.setInt(2, limit)

                val rs = ps.executeQuery()


                buildSet {
                    while (rs.next()) add(rs.getLong(1))
                }
            }
    }

    fun insertEmbedding(name: String, entries: List<Pair<Long, String>>) {
        if (name.isBlank() || entries.isEmpty()) return

        val tableName = name.replace(".", "_")

        val model = models[tableName] ?: return

        when (model) {
            Embedding.Model.EmbeddingGemma -> {
                val texts = entries.map { (_, text) -> "title: none | text: $text" }
                val embeddings: Array<FloatArray> = createRawEmbeddings(model, texts)

                connection.prepareStatement("INSERT OR REPLACE INTO $tableName (id, embedding) VALUES (?, ?::FLOAT[${model.modelData.dimension}]);")
                    .use { ps ->
                        for (i in entries.indices) {
                            val (id, _) = entries[i]

                            ps.setLong(1, id)

                            val arr: java.sql.Array = connection.createArrayOf(
                                "FLOAT",
                                embeddings[i].map { it }.toTypedArray()
                            )

                            ps.setArray(2, arr)

                            ps.addBatch()
                        }

                        ps.executeBatch()
                    }
            }
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
        connection.close()

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
        val session = sessions.getOrPut(model) {
            createSession(model)
        }

        if (session == null) return emptyArray()

        val (inputIds, attentionMask) = tokenize(model, values) ?: return emptyArray()

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
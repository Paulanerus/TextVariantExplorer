package dev.paulee.api.internal

object Embedding {
    enum class Models(
        val id: String,
        val description: String,
        val author: String,
        val parameter: String,
        val modelData: ModelData,
    ) {
        EmbeddingGemma(
            "onnx-community/embeddinggemma-300m-ONNX",
            "A lightweight open embedding model from Google, built on Gemma 3 and trained on 100+ spoken languages.",
            "Google DeepMind",
            "300M",
            ModelData()
        )
    }

    data class ModelData(
        val dimension: Int = 768,
        val model: String = "onnx/model.onnx",
        val modelData: String = "onnx/model.onnx_data",
        val tokenizer: String = "tokenizer.json",
        val tokenizerConfig: String = "tokenizer_config.json",
    )
}
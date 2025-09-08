package dev.paulee.api.internal

object Embedding {
    enum class Models(val id: String, val description: String, val author: String, val parameter: String) {
        EmbeddingGemma(
            "onnx-community/embeddinggemma-300m-ONNX",
            "A 300M parameter open embedding model from Google, built on Gemma 3 with T5Gemma initialization. Trained on 100+ languages, it creates text embeddings for tasks like search, classification, clustering, and semantic similarity.",
            "Google DeepMind",
            "300M"
        )
    }
}
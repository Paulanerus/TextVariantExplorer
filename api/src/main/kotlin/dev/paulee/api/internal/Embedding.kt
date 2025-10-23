package dev.paulee.api.internal

object Embedding {
    enum class Model(
        val id: String,
        val description: String,
        val author: String,
        val parameter: String,
        val link: String,
        val modelData: ModelData,
    ) {
        EmbeddingGemma(
            "onnx-community/embeddinggemma-300m-ONNX",
            "model_management.embedding_gemma.desc",
            "Google DeepMind",
            "300M",
            "https://huggingface.co/google/embeddinggemma-300m",
            ModelData()
        ),
        AncientGreekBert(
            "onnx-community/Ancient-Greek-BERT-ONNX",
            "model_management.ancient_greek_bert.desc",
            "Pranaydeep Singh, Gorik Rutten, Els Lefever",
            "110M",
            "https://huggingface.co/pranaydeeps/Ancient-Greek-BERT",
            ModelData(
                maxLength = 512,
                modelData = ""
            )
        ),
        GreekTransfer(
            "",
            "model_management.greek_transfer.desc",
            "lighteternal",
            "270M",
            "https://huggingface.co/lighteternal/stsb-xlm-r-greek-transfer",
            ModelData(
                maxLength = 400,
                modelData = ""
            )
        )
    }

    data class ModelData(
        val dimension: Int = 768,
        val maxLength: Int = 2048,
        val model: String = "onnx/model.onnx",
        val modelData: String = "onnx/model.onnx_data",
        val tokenizer: String = "tokenizer.json",
        val tokenizerConfig: String = "tokenizer_config.json",
    )
}
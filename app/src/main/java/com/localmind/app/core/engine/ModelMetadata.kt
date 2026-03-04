package com.localmind.app.core.engine

/**
 * Data class representing key metadata extracted from a GGUF model file.
 * Used for memory estimation and model information display.
 */
data class ModelMetadata(
    val nLayer: Int,
    val nEmbd: Int,
    val nHead: Int,
    val nHeadKv: Int,
    val nCtxTrain: Int,
    val nVocab: Int,
    val nParams: Long,
    val modelSize: Long,
    val modelDesc: String,
    val hparams: Map<String, String> = emptyMap()
)

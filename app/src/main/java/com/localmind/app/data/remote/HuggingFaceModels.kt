package com.localmind.app.data.remote

import com.google.gson.annotations.SerializedName

/**
 * API response DTO from Hugging Face Hub API
 */
data class HFModelResponse(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("id") val modelId: String = "",
    @SerializedName("modelId") val repoId: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("downloads") val downloads: Int = 0,
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("tags") val tags: List<String>? = null,
    @SerializedName("pipeline_tag") val pipelineTag: String? = null,
    @SerializedName("siblings") val siblings: List<HFSibling>? = null,
    @SerializedName("lastModified") val lastModified: String? = null
)

/**
 * File entry in a HF model repo
 */
data class HFSibling(
    @SerializedName("rfilename") val filename: String = "",
    @SerializedName("size") val size: Long? = null,
    @SerializedName("lfs") val lfs: HFLfsInfo? = null
)

data class HFLfsInfo(
    @SerializedName("size") val size: Long? = null
)

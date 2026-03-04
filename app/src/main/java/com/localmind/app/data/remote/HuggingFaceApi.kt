package com.localmind.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

import retrofit2.Call

interface HuggingFaceApi {

    @GET("api/models")
    fun searchModels(
        @Query("search") search: String? = null,
        @Query("filter") filter: String? = "gguf",
        @Query("sort") sort: String? = "downloads",
        @Query("direction") direction: Int? = -1,
        @Query("limit") limit: Int = 50,
        @Query("expand") expand: String? = "siblings"
    ): Call<List<HFModelResponse>>

    @GET("api/models/{repo_id}")
    fun getModelDetails(
        @Path("repo_id", encoded = true) repoId: String,
        @Query("expand") expand: String? = "siblings"
    ): Call<HFModelResponse>
}

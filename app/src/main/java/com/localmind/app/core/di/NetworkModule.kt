package com.localmind.app.core.di

import com.localmind.app.BuildConfig
import com.localmind.app.data.remote.HuggingFaceApi
import com.localmind.app.data.remote.HfAuthHeaderInterceptor
import com.localmind.app.data.remote.RemoteInferenceApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val HF_BASE_URL = "https://huggingface.co/"
    private const val HF_REMOTE_INFERENCE_BASE_URL = "https://router.huggingface.co/"

    @Provides
    @Singleton
    fun provideOkHttpClient(
        hfAuthHeaderInterceptor: HfAuthHeaderInterceptor
    ): OkHttpClient {
        // SECURITY FIX: HTTP logging ONLY in debug builds
        // In release builds, network requests are never logged — prevents token/URL leaks
        // OWASP MSTG-NETWORK-1, MSTG-RESILIENCE-2
        return OkHttpClient.Builder()
            .addInterceptor(hfAuthHeaderInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                    addInterceptor(logging)
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(HF_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideHuggingFaceApi(retrofit: Retrofit): HuggingFaceApi {
        return retrofit.create(HuggingFaceApi::class.java)
    }

    @Provides
    @Singleton
    @Named("remoteInferenceApi")
    fun provideRemoteInferenceApi(
        hfAuthHeaderInterceptor: HfAuthHeaderInterceptor
    ): RemoteInferenceApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(hfAuthHeaderInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(HF_REMOTE_INFERENCE_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RemoteInferenceApi::class.java)
    }

    @Provides
    @Singleton
    @Named("downloadClient")
    fun provideDownloadOkHttpClient(
        hfAuthHeaderInterceptor: HfAuthHeaderInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(hfAuthHeaderInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.MINUTES)
            .build()
    }
}

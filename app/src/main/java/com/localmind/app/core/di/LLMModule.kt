package com.localmind.app.core.di

import com.localmind.app.llm.nativelib.LlamaCppBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LLMModule {

    @Provides
    @Singleton
    fun provideLlamaCppBridge(): LlamaCppBridge {
        return LlamaCppBridge()
    }
}

package com.nehonar.operator.core.ai.di

import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.ai.ConfigurableAIProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    // ConfigurableAIProvider decide en cada llamada Mock/DeepSeek/OpenAI/Gemini según
    // la preferencia guardada (ver core/ai/ConfigurableAIProvider.kt).
    @Binds
    @Singleton
    abstract fun bindAiProvider(impl: ConfigurableAIProvider): AIProvider

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

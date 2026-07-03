package com.nehonar.operator.core.ai.di

import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.ai.MockAIProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    // Fase 2: siempre el mock. La Fase 3 decidirá aquí según preferencia de usuario
    // (DeepSeek / OpenAI-compatible / Mock) en lugar de un binding fijo.
    @Binds
    @Singleton
    abstract fun bindAiProvider(impl: MockAIProvider): AIProvider
}

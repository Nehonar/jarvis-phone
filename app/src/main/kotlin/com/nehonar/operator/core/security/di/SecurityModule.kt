package com.nehonar.operator.core.security.di

import com.nehonar.operator.core.security.AndroidKeystoreApiKeyStore
import com.nehonar.operator.core.security.ApiKeyStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindApiKeyStore(impl: AndroidKeystoreApiKeyStore): ApiKeyStore
}

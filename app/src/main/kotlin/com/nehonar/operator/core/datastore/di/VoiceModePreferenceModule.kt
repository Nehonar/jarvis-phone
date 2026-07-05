package com.nehonar.operator.core.datastore.di

import com.nehonar.operator.core.datastore.OperatorVoiceModePreference
import com.nehonar.operator.core.datastore.VoiceModePreference
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModePreferenceModule {

    @Binds
    @Singleton
    abstract fun bindVoiceModePreference(impl: OperatorVoiceModePreference): VoiceModePreference
}

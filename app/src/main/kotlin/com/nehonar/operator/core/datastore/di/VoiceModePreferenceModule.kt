package com.nehonar.operator.core.datastore.di

import com.nehonar.operator.core.datastore.OperatorVoiceModePreference
import com.nehonar.operator.core.datastore.OperatorWakeWordSettings
import com.nehonar.operator.core.datastore.VoiceModePreference
import com.nehonar.operator.core.datastore.WakeWordSettings
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

    @Binds
    @Singleton
    abstract fun bindWakeWordSettings(impl: OperatorWakeWordSettings): WakeWordSettings
}

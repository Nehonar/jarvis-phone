package com.nehonar.operator.core.location.di

import com.nehonar.operator.core.location.FusedLocationProvider
import com.nehonar.operator.core.location.GeofenceScheduler
import com.nehonar.operator.core.location.LocationProvider
import com.nehonar.operator.core.location.PlayServicesGeofenceScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider

    @Binds
    @Singleton
    abstract fun bindGeofenceScheduler(impl: PlayServicesGeofenceScheduler): GeofenceScheduler
}

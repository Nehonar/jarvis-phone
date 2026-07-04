package com.nehonar.operator.core.calendar.di

import com.nehonar.operator.core.calendar.AndroidCalendarRepository
import com.nehonar.operator.core.calendar.CalendarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: AndroidCalendarRepository): CalendarRepository
}

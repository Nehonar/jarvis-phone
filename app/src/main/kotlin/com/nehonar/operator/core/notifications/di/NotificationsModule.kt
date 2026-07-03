package com.nehonar.operator.core.notifications.di

import com.nehonar.operator.core.notifications.AlarmManagerReminderScheduler
import com.nehonar.operator.core.notifications.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: AlarmManagerReminderScheduler): ReminderScheduler
}

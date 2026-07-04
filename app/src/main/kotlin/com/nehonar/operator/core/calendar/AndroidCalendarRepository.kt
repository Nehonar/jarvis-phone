package com.nehonar.operator.core.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.nehonar.operator.core.common.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lectura del calendario del dispositivo vía CalendarContract.Instances (a
 * diferencia de Events, resuelve bien las recurrencias). Solo lectura; sin
 * permiso devuelve lista vacía. No testeable en JVM (ver docs/fase-9-plan.md).
 */
class AndroidCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : CalendarRepository {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun getEventsForToday(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val zone = ZoneId.systemDefault()
        val startOfDay = timeProvider.today().atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = timeProvider.today().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startOfDay.toString())
            .appendPath(endOfDay.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )

        buildList {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    add(
                        CalendarEvent(
                            id = cursor.getLong(0).toString(),
                            title = cursor.getString(1).orEmpty().ifBlank { "(sin título)" },
                            startAt = Instant.ofEpochMilli(cursor.getLong(2)),
                            endAt = Instant.ofEpochMilli(cursor.getLong(3)),
                            allDay = cursor.getInt(4) == 1,
                        ),
                    )
                }
            }
        }
    }
}

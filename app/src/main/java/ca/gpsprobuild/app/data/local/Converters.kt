package ca.gpsprobuild.app.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Enums are not listed here: Room persists enum types by name automatically.
 * minSdk 26 means java.time is available natively, so no desugaring is needed.
 */
class Converters {

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun localTimeToLong(value: LocalTime?): Long? = value?.toSecondOfDay()?.toLong()

    @TypeConverter
    fun longToLocalTime(value: Long?): LocalTime? = value?.let(LocalTime::ofSecondOfDay)
}

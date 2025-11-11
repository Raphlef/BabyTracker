package com.kouloundissa.twinstracker.data.Firestore

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

object FirestoreTimestampUtils {
    fun getCurrentTimestamp(): Long = System.currentTimeMillis()

    fun Date.toLocalDate(): LocalDate =
        this.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

    fun LocalDate.toStartOfDayDate(): Date =
        Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())

    fun generateDateRange(startDate: Date, endDate: Date): List<LocalDate> {
        val start = startDate.toLocalDate()
        val end = endDate.toLocalDate()
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { it <= end }
            .toList()
    }

    fun SimpleDateFormat.formatDateForGrouping(date: Date): String =
        this.format(date)
}
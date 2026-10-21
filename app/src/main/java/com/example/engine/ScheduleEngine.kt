package com.example.engine

import com.example.data.model.ScheduleEntity
import java.util.Calendar

object ScheduleEngine {

    /**
     * Evaluates if a given schedule is active right now (or at the given calendar timestamp).
     * Fully supports overnight schedules (e.g. 23:00 to 07:00).
     */
    fun isScheduleActive(schedule: ScheduleEntity, calendar: Calendar = Calendar.getInstance()): Boolean {
        if (!schedule.isEnabled) return false

        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 7 = Saturday
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        val startTotalMinutes = schedule.startHour * 60 + schedule.startMinute
        val endTotalMinutes = schedule.endHour * 60 + schedule.endMinute

        val isOvernight = endTotalMinutes < startTotalMinutes

        if (!isOvernight) {
            // Standard daytime schedule on the same day (e.g. 08:00 to 17:00)
            val dayBit = 1 shl (currentDayOfWeek - 1)
            val isDayMatching = (schedule.daysOfWeek and dayBit) != 0
            if (!isDayMatching) return false

            return currentTotalMinutes in startTotalMinutes until endTotalMinutes
        } else {
            // Overnight schedule spanning midnight (e.g. 23:00 to 07:00)
            if (currentTotalMinutes >= startTotalMinutes) {
                // First portion: e.g. 23:00 - 23:59 (matches starting day)
                val dayBit = 1 shl (currentDayOfWeek - 1)
                return (schedule.daysOfWeek and dayBit) != 0
            } else if (currentTotalMinutes < endTotalMinutes) {
                // Second portion: e.g. 00:00 - 06:59 (matches preceding starting day)
                val prevDay = if (currentDayOfWeek == 1) 7 else currentDayOfWeek - 1
                val dayBit = 1 shl (prevDay - 1)
                return (schedule.daysOfWeek and dayBit) != 0
            }
            return false
        }
    }
}

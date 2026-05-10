package com.chronophoto.reminder

import com.chronophoto.ui.components.DurationUnit
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

object ReminderSchedule {

    /**
     * Compute the next fire time strictly after [now] given a wall-clock anchor and an
     * (amount, unit) cadence.
     *
     * - MINUTES/HOURS use raw epoch arithmetic (preserves spacing across DST).
     * - DAYS/WEEKS/MONTHS/YEARS walk forward via [Calendar.add] so wall-clock semantics hold
     *   (same time-of-day, same day-of-month, same date-of-year).
     */
    fun nextFireMillis(
        now: Long,
        amount: Int,
        unit: DurationUnit,
        anchorMillis: Long,
    ): Long {
        require(amount > 0) { "amount must be > 0" }

        if (anchorMillis > now) return anchorMillis

        return when (unit) {
            DurationUnit.MINUTES, DurationUnit.HOURS -> {
                val ms = when (unit) {
                    DurationUnit.MINUTES -> amount * 60_000L
                    DurationUnit.HOURS -> amount * 3_600_000L
                    else -> 1L
                }
                val periods = (now - anchorMillis) / ms
                anchorMillis + (periods + 1) * ms
            }
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
                while (cal.timeInMillis <= now) {
                    cal.add(unit.calendarField, amount)
                }
                cal.timeInMillis
            }
        }
    }

    fun describe(amount: Int, unit: DurationUnit, anchorMillis: Long): String {
        val anchor = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(anchorMillis))
        return "every ${describeAmountUnit(amount, unit)} starting $anchor"
    }

    fun describeAmountUnit(amount: Int, unit: DurationUnit): String {
        val noun = when (unit) {
            DurationUnit.MINUTES -> if (amount == 1) "minute" else "minutes"
            DurationUnit.HOURS -> if (amount == 1) "hour" else "hours"
            DurationUnit.DAYS -> if (amount == 1) "day" else "days"
            DurationUnit.WEEKS -> if (amount == 1) "week" else "weeks"
            DurationUnit.MONTHS -> if (amount == 1) "month" else "months"
            DurationUnit.YEARS -> if (amount == 1) "year" else "years"
        }
        return "$amount $noun"
    }

    fun describeMinutes(min: Int): String = when {
        min < 60 -> "${min}m"
        min == 60 -> "1 hour"
        min < 1440 && min % 60 == 0 -> "${min / 60} hours"
        min < 1440 -> "${min / 60}h ${min % 60}m"
        min == 1440 -> "1 day"
        min % 1440 == 0 -> "${min / 1440} days"
        else -> "${min / 1440}d ${(min % 1440) / 60}h"
    }
}

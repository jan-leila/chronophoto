package com.chronophoto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chronophoto.reminder.ReminderSchedule
import java.util.Calendar

private val REFIRE_UNITS = listOf(DurationUnit.MINUTES, DurationUnit.HOURS, DurationUnit.DAYS)

@Composable
fun ReminderEditor(
    enabled: Boolean,
    intervalAmount: Int?,
    intervalUnit: DurationUnit?,
    anchorMillis: Long?,
    refireMinutes: Int?,
    onChange: (
        enabled: Boolean,
        amount: Int?,
        unit: DurationUnit?,
        anchor: Long?,
        refire: Int?,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeAmount = intervalAmount ?: 1
    val activeUnit = intervalUnit ?: DurationUnit.DAYS
    val activeAnchor = anchorMillis ?: defaultAnchorMillis()
    val activeRefire = refireMinutes
    val refireOn = activeRefire != null

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Reminders")
            Switch(
                checked = enabled,
                onCheckedChange = { newEnabled ->
                    if (newEnabled) onChange(true, activeAmount, activeUnit, activeAnchor, activeRefire)
                    else onChange(false, null, null, null, null)
                },
            )
        }
        if (enabled) {
            Spacer(Modifier.padding(top = 8.dp))
            Text("How often", style = MaterialTheme.typography.labelLarge)
            DurationInput(
                amount = activeAmount,
                unit = activeUnit,
                onChange = { amt, u -> onChange(true, amt, u, activeAnchor, activeRefire) },
            )

            Spacer(Modifier.padding(top = 12.dp))
            Text("Start at", style = MaterialTheme.typography.labelLarge)
            DateAnchorPicker(
                anchorMillis = activeAnchor,
                onChange = { onChange(true, activeAmount, activeUnit, it, activeRefire) },
            )

            Spacer(Modifier.padding(top = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Re-nag if ignored", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = refireOn,
                    onCheckedChange = { on ->
                        if (on) onChange(true, activeAmount, activeUnit, activeAnchor, activeRefire ?: 30)
                        else onChange(true, activeAmount, activeUnit, activeAnchor, null)
                    },
                )
            }
            if (refireOn) {
                val (refireAmt, refireUnit) = decomposeMinutes(activeRefire ?: 30)
                DurationInput(
                    amount = refireAmt,
                    unit = refireUnit,
                    availableUnits = REFIRE_UNITS,
                    onChange = { amt, u ->
                        val totalMin = amt * minutesPerUnit(u)
                        onChange(true, activeAmount, activeUnit, activeAnchor, totalMin)
                    },
                )
            }

            Spacer(Modifier.padding(top = 8.dp))
            Text(
                text = buildString {
                    append("Schedule: ")
                    append(ReminderSchedule.describe(activeAmount, activeUnit, activeAnchor))
                    if (refireOn) {
                        append(" · re-nag every ")
                        append(ReminderSchedule.describeMinutes(activeRefire ?: 30))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun decomposeMinutes(minutes: Int): Pair<Int, DurationUnit> = when {
    minutes > 0 && minutes % 1440 == 0 -> (minutes / 1440) to DurationUnit.DAYS
    minutes > 0 && minutes % 60 == 0 -> (minutes / 60) to DurationUnit.HOURS
    else -> minutes.coerceAtLeast(1) to DurationUnit.MINUTES
}

private fun minutesPerUnit(unit: DurationUnit): Int = when (unit) {
    DurationUnit.MINUTES -> 1
    DurationUnit.HOURS -> 60
    DurationUnit.DAYS -> 1440
    DurationUnit.WEEKS -> 10080
    DurationUnit.MONTHS -> 43200
    DurationUnit.YEARS -> 525600
}

private fun defaultAnchorMillis(now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.MINUTE, 5)
        val m = get(Calendar.MINUTE)
        val rounded = ((m + 4) / 5) * 5
        if (rounded >= 60) {
            set(Calendar.MINUTE, 0)
            add(Calendar.HOUR_OF_DAY, 1)
        } else {
            set(Calendar.MINUTE, rounded)
        }
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

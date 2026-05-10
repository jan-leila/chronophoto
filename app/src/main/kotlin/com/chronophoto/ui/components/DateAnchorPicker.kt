package com.chronophoto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateAnchorPicker(
    anchorMillis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }

    val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
    val dateLabel = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(anchorMillis))
    val timeLabel = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { dateOpen = true }) { Text(dateLabel) }
        OutlinedButton(onClick = { timeOpen = true }) { Text(timeLabel) }
    }

    if (dateOpen) {
        // DatePicker selectedDateMillis is UTC midnight of the picked day.
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMidnightFor(anchorMillis))
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    val pickedUtc = state.selectedDateMillis
                    if (pickedUtc != null) {
                        onChange(combineDateAndTime(pickedUtc, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
                    }
                    dateOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { dateOpen = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (timeOpen) {
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            title = { Text("Anchor time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply {
                        timeInMillis = anchorMillis
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onChange(newCal.timeInMillis)
                    timeOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { timeOpen = false }) { Text("Cancel") }
            },
        )
    }
}

private fun utcMidnightFor(localEpochMs: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localEpochMs }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }
    return utc.timeInMillis
}

private fun combineDateAndTime(utcDateMs: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDateMs }
    val local = Calendar.getInstance().apply {
        clear()
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            hour,
            minute,
            0,
        )
        set(Calendar.MILLISECOND, 0)
    }
    return local.timeInMillis
}

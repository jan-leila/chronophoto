package com.chronophoto.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar

enum class DurationUnit(val label: String, val calendarField: Int) {
    MINUTES("min", Calendar.MINUTE),
    HOURS("hr", Calendar.HOUR_OF_DAY),
    DAYS("day", Calendar.DAY_OF_YEAR),
    WEEKS("wk", Calendar.WEEK_OF_YEAR),
    MONTHS("mo", Calendar.MONTH),
    YEARS("yr", Calendar.YEAR),
}

@Composable
fun DurationInput(
    amount: Int,
    unit: DurationUnit,
    onChange: (amount: Int, unit: DurationUnit) -> Unit,
    modifier: Modifier = Modifier,
    availableUnits: List<DurationUnit> = DurationUnit.values().toList(),
    minAmount: Int = 1,
) {
    var amountText by remember { mutableStateOf(amount.toString()) }
    var localUnit by remember { mutableStateOf(unit) }

    LaunchedEffect(amount, unit) {
        if ((amountText.toIntOrNull() ?: 0) != amount) amountText = amount.toString()
        if (localUnit != unit) localUnit = unit
    }

    fun pushIfChanged(newAmt: Int, newUnit: DurationUnit) {
        val a = newAmt.coerceAtLeast(minAmount)
        if (a != amount || newUnit != unit) onChange(a, newUnit)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = amountText,
            onValueChange = { input ->
                val cleaned = input.filter { it.isDigit() }.take(5)
                amountText = cleaned
                val amt = cleaned.toIntOrNull()
                if (amt != null && amt > 0) pushIfChanged(amt, localUnit)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            availableUnits.forEach { u ->
                FilterChip(
                    selected = localUnit == u,
                    onClick = {
                        localUnit = u
                        val amt = amountText.toIntOrNull() ?: 1
                        pushIfChanged(amt, u)
                    },
                    label = { Text(u.label) },
                )
            }
        }
    }
}

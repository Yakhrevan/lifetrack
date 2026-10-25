package com.yakhrevan.lifetrack

import android.app.DatePickerDialog
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Opens the platform date picker and returns a strict ISO-8601 calendar date. */
internal fun showIsoDatePicker(
    context: Context,
    initialIsoDate: String,
    onDateSelected: (String) -> Unit
) {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val calendar = Calendar.getInstance()
    runCatching { formatter.parse(initialIsoDate) }.getOrNull()?.let { calendar.time = it }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onDateSelected(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

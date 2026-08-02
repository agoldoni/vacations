package it.goldoni.vacations.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

fun LocalDate.formatted(): String = format(dateFormatter)

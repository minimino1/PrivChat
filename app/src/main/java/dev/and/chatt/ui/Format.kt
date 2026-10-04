package dev.and.chatt.ui

import java.text.DateFormat
import java.util.Date

/** Formatierung bleibt bewusst in der UI-Schicht (kein Android-Locale-Drama im Repository). */
object Format {

    private val timeFormat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    private val dateTimeFormat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    fun time(timestamp: Long): String =
        if (timestamp <= 0L) "" else timeFormat.format(Date(timestamp))

    fun dateTime(timestamp: Long): String =
        if (timestamp <= 0L) "" else dateTimeFormat.format(Date(timestamp))

    /** "gerade eben", "vor 5 Min.", "gestern", ... */
    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return ""
        val diff = now - timestamp
        return when {
            diff < 60_000L -> "gerade eben"
            diff < 3_600_000L -> "vor ${diff / 60_000L} Min."
            diff < 86_400_000L -> "vor ${diff / 3_600_000L} Std."
            diff < 172_800_000L -> "gestern"
            diff < 604_800_000L -> "vor ${diff / 86_400_000L} Tagen"
            else -> dateTime(timestamp)
        }
    }

    fun initials(name: String): String =
        name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }

    /** Vorschau in der Chatliste: eigene Nachricht mit Prefix, sonst Rohtext. */
    fun preview(text: String, mine: Boolean): String =
        if (mine) "Du: $text" else text
}
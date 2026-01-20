package com.example.calendar_vol1.utils

import android.util.Log
import com.example.calendar_vol1.data.CalendarEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object IcsHandler {


    fun exportEvents(events: List<CalendarEvent>): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\n")
        sb.append("VERSION:2.0\n")
        sb.append("PRODID:-//My Calendar App//CN\n")


        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")

        for (event in events) {
            sb.append("BEGIN:VEVENT\n")
            sb.append("UID:${event.id}@mycalendarapp\n")
            sb.append("DTSTAMP:${dateFormat.format(Date())}\n")
            sb.append("DTSTART:${dateFormat.format(Date(event.startTime))}\n")
            sb.append("DTEND:${dateFormat.format(Date(event.endTime))}\n")
            sb.append("SUMMARY:${escape(event.title)}\n")
            if (event.description.isNotEmpty()) {
                sb.append("DESCRIPTION:${escape(event.description)}\n")
            }
            if (event.location.isNotEmpty()) {
                sb.append("LOCATION:${escape(event.location)}\n")
            }
            sb.append("END:VEVENT\n")
        }

        sb.append("END:VCALENDAR")
        return sb.toString()
    }


    fun parseIcs(icsContent: String): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val lines = icsContent.lines()

        var currentTitle = ""
        var currentDesc = ""
        var currentLocation = ""
        var currentStart: Long? = null
        var currentEnd: Long? = null
        var inEvent = false

        for (line in lines) {
            // 清理空白字符
            val cleanLine = line.trim()

            if (cleanLine == "BEGIN:VEVENT") {
                inEvent = true
                currentTitle = "无标题"
                currentDesc = ""
                currentLocation = ""
                currentStart = null
                currentEnd = null
            } else if (cleanLine == "END:VEVENT") {
                inEvent = false

                if (currentStart != null) {

                    val startTime = currentStart!!
                    val endTime = currentEnd ?: (startTime + 3600000)

                    events.add(CalendarEvent(
                        title = currentTitle,
                        description = currentDesc,
                        location = currentLocation,
                        startTime = startTime,
                        endTime = endTime,
                        remindMinutes = -1
                    ))
                }
            } else if (inEvent) {
                try {
                    when {
                        cleanLine.startsWith("SUMMARY") -> {
                            // 兼容 SUMMARY:标题 和 SUMMARY;LANGUAGE=zh:标题
                            val parts = cleanLine.split(":", limit = 2)
                            if (parts.size > 1) currentTitle = unescape(parts[1])
                        }
                        cleanLine.startsWith("DESCRIPTION") -> {
                            val parts = cleanLine.split(":", limit = 2)
                            if (parts.size > 1) currentDesc = unescape(parts[1])
                        }
                        cleanLine.startsWith("LOCATION") -> {
                            val parts = cleanLine.split(":", limit = 2)
                            if (parts.size > 1) currentLocation = unescape(parts[1])
                        }
                        cleanLine.startsWith("DTSTART") -> {
                            // 兼容 DTSTART;VALUE=DATE:20250101
                            val value = cleanLine.substringAfter(":")
                            currentStart = parseDate(value)
                        }
                        cleanLine.startsWith("DTEND") -> {
                            val value = cleanLine.substringAfter(":")
                            currentEnd = parseDate(value)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("IcsHandler", "解析行失败: $cleanLine", e)
                }
            }
        }
        return events
    }


    private fun parseDate(dateStr: String): Long? {
        try {
            val cleanDate = dateStr.trim()

            if (cleanDate.length == 8) {
                val format = SimpleDateFormat("yyyyMMdd", Locale.US)

                format.timeZone = TimeZone.getDefault()
                return format.parse(cleanDate)?.time
            }

            if (cleanDate.length == 15 && cleanDate.contains("T")) {
                val format = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                format.timeZone = TimeZone.getDefault()
                return format.parse(cleanDate)?.time
            }


            if (cleanDate.endsWith("Z")) {
                val format = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                return format.parse(cleanDate)?.time
            }

        } catch (e: Exception) {
            Log.e("IcsHandler", "日期解析失败: $dateStr")
        }
        return null
    }

    private fun escape(s: String) = s.replace("\n", "\\n").replace(",", "\\,")
    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\,", ",")
}
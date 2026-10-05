package com.example.theiptv.data

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone

object XmltvParser {
    fun parse(
        input: InputStream,
        onProgramme: (channelId: String, startMs: Long, endMs: Long, title: String, description: String) -> Unit,
    ) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        val zones = HashMap<Int, TimeZone>(8)
        var event = parser.eventType
        var inProgramme = false
        var channelId = ""
        var startMs = 0L
        var endMs = 0L
        var title = ""
        var description = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        inProgramme = true
                        channelId = parser.getAttributeValue(null, "channel").orEmpty()
                        startMs = parseXmltvTime(parser.getAttributeValue(null, "start"), calendar, zones)
                        endMs = parseXmltvTime(parser.getAttributeValue(null, "stop"), calendar, zones)
                        title = ""
                        description = ""
                    }
                    "title" -> if (inProgramme && title.isEmpty()) title = readText(parser)
                    "desc" -> if (inProgramme && description.isEmpty()) description = readText(parser)
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme" && inProgramme) {
                    if (channelId.isNotBlank() && startMs > 0L) {
                        val stop = if (endMs > startMs) endMs else startMs + 30L * 60L * 1000L
                        onProgramme(channelId, startMs, stop, clean(title).ifBlank { "Unknown programme" }, clean(description))
                    }
                    inProgramme = false
                }
            }
            event = parser.next()
        }
    }

    private fun readText(parser: XmlPullParser): String {
        return try {
            parser.nextText().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun clean(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        if (!value.contains('<') && !value.contains('&')) return value
        return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    private fun parseXmltvTime(
        raw: String?,
        calendar: Calendar,
        zones: MutableMap<Int, TimeZone>,
    ): Long {
        if (raw == null || raw.length < 14) return 0L
        val year = parseDigits(raw, 0, 4)
        val month = parseDigits(raw, 4, 6)
        val day = parseDigits(raw, 6, 8)
        val hour = parseDigits(raw, 8, 10)
        val minute = parseDigits(raw, 10, 12)
        val second = parseDigits(raw, 12, 14)
        if (year < 1970 || month !in 1..12 || day !in 1..31 ||
            hour !in 0..23 || minute !in 0..59 || second !in 0..60
        ) {
            return 0L
        }
        val zone = xmltvTimeZone(raw, zones)
        if (calendar.timeZone != zone) calendar.timeZone = zone
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, second)
        return calendar.timeInMillis
    }

    private fun xmltvTimeZone(raw: String, zones: MutableMap<Int, TimeZone>): TimeZone {
        var index = 14
        while (index < raw.length && raw[index].isWhitespace()) index++
        if (index >= raw.length) {
            return zones.getOrPut(Int.MIN_VALUE) { TimeZone.getDefault() }
        }
        val sign = when (raw[index]) {
            '+' -> 1
            '-' -> -1
            else -> return zones.getOrPut(Int.MIN_VALUE) { TimeZone.getDefault() }
        }
        index++
        val hourTens = index
        if (index + 1 >= raw.length) return zones.getOrPut(Int.MIN_VALUE) { TimeZone.getDefault() }
        val hours = parseDigits(raw, hourTens, hourTens + 2)
        index += 2
        if (index < raw.length && raw[index] == ':') index++
        val minutes = if (index + 1 < raw.length) parseDigits(raw, index, index + 2) else 0
        if (hours < 0 || minutes !in 0..59) {
            return zones.getOrPut(Int.MIN_VALUE) { TimeZone.getDefault() }
        }
        val offsetMinutes = sign * (hours * 60 + minutes)
        return zones.getOrPut(offsetMinutes) {
            val hoursAbs = kotlin.math.abs(offsetMinutes) / 60
            val minutesAbs = kotlin.math.abs(offsetMinutes) % 60
            val gmt = buildString(9) {
                append("GMT")
                append(if (offsetMinutes >= 0) '+' else '-')
                append((hoursAbs / 10).digitToChar())
                append((hoursAbs % 10).digitToChar())
                append((minutesAbs / 10).digitToChar())
                append((minutesAbs % 10).digitToChar())
            }
            TimeZone.getTimeZone(gmt)
        }
    }

    private fun parseDigits(value: String, start: Int, end: Int): Int {
        var n = 0
        for (index in start until end) {
            val char = value[index]
            if (char !in '0'..'9') return -1
            n = n * 10 + (char - '0')
        }
        return n
    }
}

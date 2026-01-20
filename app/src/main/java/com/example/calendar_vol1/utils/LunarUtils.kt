package com.example.calendar_vol1.utils

import android.icu.util.ChineseCalendar
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

object LunarUtils {


    private val chineseNumber = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二")
    private val lunarDayStr = arrayOf("初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十")

    fun getLunarDisplay(date: LocalDate): LunarResult {

        val calendar = Calendar.getInstance()
        calendar.time = java.util.Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())


        val chineseCalendar = ChineseCalendar()
        chineseCalendar.timeInMillis = calendar.timeInMillis

        val lunarYear = chineseCalendar.get(ChineseCalendar.EXTENDED_YEAR) - 2637
        val lunarMonth = chineseCalendar.get(ChineseCalendar.MONTH) + 1 // 0-based
        val lunarDay = chineseCalendar.get(ChineseCalendar.DAY_OF_MONTH)


        if (lunarMonth == 1 && lunarDay == 1) return LunarResult("春节", true)
        if (lunarMonth == 1 && lunarDay == 15) return LunarResult("元宵", true)
        if (lunarMonth == 5 && lunarDay == 5) return LunarResult("端午", true)
        if (lunarMonth == 7 && lunarDay == 7) return LunarResult("七夕", true)
        if (lunarMonth == 8 && lunarDay == 15) return LunarResult("中秋", true)
        if (lunarMonth == 9 && lunarDay == 9) return LunarResult("重阳", true)
        if (lunarMonth == 12 && lunarDay == 8) return LunarResult("腊八", true)
        // 除夕处理：需要判断腊月的最后一天，这里简单处理为腊月三十（有时是二十九，精确算法较复杂，此处做简化）
        if (lunarMonth == 12 && lunarDay == 30) return LunarResult("除夕", true)


        if (date.monthValue == 1 && date.dayOfMonth == 1) return LunarResult("元旦", true)
        if (date.monthValue == 10 && date.dayOfMonth == 1) return LunarResult("国庆", true)


        if (lunarDay == 1) {
            return LunarResult(getMonthStr(lunarMonth) + "月", false)
        }


        val dayString = if (lunarDay in 1..30) lunarDayStr[lunarDay - 1] else ""
        return LunarResult(dayString, false)
    }

    private fun getMonthStr(month: Int): String {
        return when (month) {
            1 -> "正"
            12 -> "腊"
            else -> chineseNumber.getOrElse(month - 1) { "" }
        }
    }

    data class LunarResult(val text: String, val isHoliday: Boolean)
}
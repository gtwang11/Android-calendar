package com.example.calendar_vol1

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.calendar_vol1.data.AppDatabase
import com.example.calendar_vol1.data.CalendarEvent
import com.example.calendar_vol1.databinding.ActivityMainBinding
import com.example.calendar_vol1.ui.EventAdapter
import com.example.calendar_vol1.utils.IcsHandler
import com.example.calendar_vol1.utils.ReminderManager
import com.google.android.material.textfield.TextInputEditText
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.WeekDay
import com.kizitonwose.calendar.core.WeekDayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.kizitonwose.calendar.view.WeekDayBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val database by lazy { AppDatabase.getDatabase(this) }

    private val eventAdapter = EventAdapter(
        onEventClick = { event -> showEventDialog(selectedDate, event) },
        onDeleteClick = { event -> deleteEvent(event) }
    )

    private var selectedDate: LocalDate = LocalDate.now()
    private var eventsCache: Map<LocalDate, List<CalendarEvent>> = emptyMap()

    // 年视图相关变量
    private var currentYearViewYear: Int = LocalDate.now().year

    // 临时变量
    private var tempStartCalendar = Calendar.getInstance()
    private var tempEndCalendar = Calendar.getInstance()

    // ================== 新增：导入导出 Launcher ==================
    // 1. 导出文件的 Launcher
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        uri?.let { performExport(it) }
    }

    // 2. 导入文件的 Launcher
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { performImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 初始化各个视图
        setupRecyclerView()
        setupCalendarView()
        setupYearView()
        setupButtons()
        setupMoreButton() // 【新增】设置右上角更多菜单

        // 2. 加载数据
        refreshData()

        // 3. 主动请求通知权限 (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = eventAdapter
        }
    }

    // ================== 新增：导入/导出/订阅 逻辑 ==================

    private fun setupMoreButton() {
        // 尝试找到按钮，如果找不到（还没改布局）就跳过，防止崩溃
        val btnMore = try {
            binding.root.findViewById<ImageButton>(R.id.btnMore)
        } catch (e: Exception) { null }

        btnMore?.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("导出日程 (.ics)")
            popup.menu.add("导入日程")
            popup.menu.add("网络订阅")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.title) {
                    "导出日程 (.ics)" -> {
                        exportLauncher.launch("my_calendar_backup.ics")
                    }
                    "导入日程" -> {
                        importLauncher.launch(arrayOf("text/calendar", "text/plain", "*/*"))
                    }
                    "网络订阅" -> {
                        showSubscribeDialog()
                    }
                }
                true
            }
            popup.show()
        }
    }

    private fun performExport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val events = database.eventDao().getAllEventsSync()
                val icsContent = IcsHandler.exportEvents(events)
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(icsContent.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "导出成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performImport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            sb.append(line).append("\n")
                            line = reader.readLine()
                        }
                    }
                }
                saveImportedEvents(sb.toString())
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSubscribeDialog() {
        val input = TextInputEditText(this)
        input.hint = "例如: https://calendar.google.com/.../basic.ics"
        // 默认填入一个中国节假日订阅源方便测试
        input.setText("https://www.shuyz.com/githubfiles/china-holiday-calender/master/holidayCal.ics")

        AlertDialog.Builder(this)
            .setTitle("输入订阅地址 (iCal/ICS)")
            .setView(input)
            .setPositiveButton("订阅") { _, _ ->
                val url = input.text.toString()
                if (url.isNotEmpty()) {
                    performNetworkSubscription(url)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performNetworkSubscription(url: String) {
        Toast.makeText(this, "正在下载日历...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        saveImportedEvents(body)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "下载失败: Code ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun saveImportedEvents(icsContent: String) {
        val events = IcsHandler.parseIcs(icsContent)
        if (events.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "未发现有效的日历事件", Toast.LENGTH_SHORT).show()
            }
            return
        }

        for (event in events) {
            database.eventDao().insertEvent(event)
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, "成功导入 ${events.size} 个事件", Toast.LENGTH_SHORT).show()
            refreshData()
        }
    }

    // ================== 年视图逻辑 ==================

    private fun setupYearView() {
        binding.rvYearMonths.layoutManager = GridLayoutManager(this, 3)
        updateYearViewAdapter()

        binding.btnPrevYear.setOnClickListener {
            currentYearViewYear--
            updateYearViewAdapter()
        }
        binding.btnNextYear.setOnClickListener {
            currentYearViewYear++
            updateYearViewAdapter()
        }
    }

    private fun updateYearViewAdapter() {
        binding.tvYearTitle.text = "${currentYearViewYear}年"

        binding.rvYearMonths.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = TextView(parent.context).apply {
                    val height = (80 * parent.context.resources.displayMetrics.density).toInt()
                    layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply {
                        setMargins(16, 16, 16, 16)
                    }
                    gravity = Gravity.CENTER
                    textSize = 18f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.BLACK)
                    setBackgroundResource(androidx.appcompat.R.drawable.abc_item_background_holo_dark)
                }
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val month = position + 1
                val textView = holder.itemView as TextView
                textView.text = "${month}月"

                if (currentYearViewYear == selectedDate.year && month == selectedDate.monthValue) {
                    textView.setTextColor(getColor(R.color.purple_500))
                    textView.text = "${month}月\n(当前)"
                } else {
                    textView.setTextColor(Color.BLACK)
                }

                holder.itemView.setOnClickListener {
                    val targetMonth = YearMonth.of(currentYearViewYear, month)

                    binding.toggleGroup.check(R.id.btnMonthView)
                    binding.calendarView.scrollToMonth(targetMonth)

                    if (selectedDate.year != currentYearViewYear || selectedDate.monthValue != month) {
                        val oldDate = selectedDate
                        selectedDate = targetMonth.atDay(1)
                        binding.calendarView.notifyDateChanged(oldDate)
                        binding.calendarView.notifyDateChanged(selectedDate)
                        updateAdapterForDate(selectedDate)
                    }
                }
            }

            override fun getItemCount() = 12
        }
    }

    // ================== 日历视图逻辑 ==================

    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = view.findViewById(R.id.calendarDayText)
        val lunarView: TextView = view.findViewById(R.id.lunarDayText)
        val dotView: View = view.findViewById(R.id.dayDot)
        val selectedBg: View = view.findViewById(R.id.selectedBackground)
        lateinit var dayDate: LocalDate
        val viewRoot = view
    }

    private fun setupCalendarView() {
        val daysOfWeek = daysOfWeek()
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(200)
        val endMonth = currentMonth.plusMonths(200)

        binding.calendarView.setup(startMonth, endMonth, daysOfWeek.first())
        binding.calendarView.scrollToMonth(currentMonth)

        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                bindDayView(container, data.date, data.position == DayPosition.MonthDate)
            }
        }
        binding.calendarView.monthScrollListener = {
            val titleFormatter = DateTimeFormatter.ofPattern("yyyy年 MMMM", Locale.getDefault())
            binding.monthYearText.text = titleFormatter.format(it.yearMonth)
            currentYearViewYear = it.yearMonth.year
        }

        binding.weekCalendarView.setup(startMonth.atDay(1), endMonth.atEndOfMonth(), daysOfWeek.first())
        binding.weekCalendarView.scrollToWeek(LocalDate.now())

        binding.weekCalendarView.dayBinder = object : WeekDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: WeekDay) {
                bindDayView(container, data.date, data.position == WeekDayPosition.RangeDate)
            }
        }
        binding.weekCalendarView.weekScrollListener = {
            val titleFormatter = DateTimeFormatter.ofPattern("yyyy年 MMMM", Locale.getDefault())
            binding.monthYearText.text = titleFormatter.format(YearMonth.from(it.days.first().date))
        }
    }

    private fun bindDayView(container: DayViewContainer, date: LocalDate, isCurrentMonth: Boolean) {
        container.dayDate = date
        container.textView.text = date.dayOfMonth.toString()

        // ================== 修改开始：农历逻辑实现 ==================
        if (isCurrentMonth) {
            container.textView.setTextColor(getColor(R.color.black))
            container.lunarView.visibility = View.VISIBLE

            // 1. 获取农历数据
            val lunarResult = com.example.calendar_vol1.utils.LunarUtils.getLunarDisplay(date)

            // 2. 设置文字
            container.lunarView.text = lunarResult.text

            // 3. 设置颜色 (如果是选中状态，强制白色；否则如果是节日，显示红色；否则灰色)
            if (date == selectedDate || date == LocalDate.now()) {
                container.lunarView.setTextColor(Color.WHITE)
            } else if (lunarResult.isHoliday) {
                container.lunarView.setTextColor(Color.RED) // 节日标红
                // 也可以加粗显示
                // container.lunarView.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                container.lunarView.setTextColor(Color.parseColor("#999999")) // 普通日子灰色
            }

        } else {
            // 非当前月的处理
            container.textView.setTextColor(getColor(R.color.teal_200))
            container.lunarView.visibility = View.INVISIBLE
        }
        // ================== 修改结束 ==================

        container.viewRoot.setOnClickListener {
            val oldDate = selectedDate
            selectedDate = date
            binding.calendarView.notifyDateChanged(oldDate)
            binding.calendarView.notifyDateChanged(selectedDate)
            binding.weekCalendarView.notifyDateChanged(oldDate)
            binding.weekCalendarView.notifyDateChanged(selectedDate)
            updateAdapterForDate(selectedDate)
        }

        val isToday = date == LocalDate.now()
        val isSelected = date == selectedDate

        if (isToday) {
            container.selectedBg.visibility = View.VISIBLE
            container.selectedBg.setBackgroundResource(R.drawable.bg_day_today)
            container.textView.setTextColor(Color.WHITE)
            // 注意：这里已经处理了选中/今日时的颜色，确保农历也是白色
            container.lunarView.setTextColor(Color.WHITE)
        } else if (isSelected) {
            container.selectedBg.visibility = View.VISIBLE
            container.selectedBg.setBackgroundResource(R.drawable.bg_day_selected)
            container.textView.setTextColor(getColor(R.color.purple_500))
            container.lunarView.setTextColor(getColor(R.color.purple_500))
        } else {
            container.selectedBg.visibility = View.INVISIBLE
            // 恢复未选中状态的逻辑已经在上面 "if (isCurrentMonth)" 中处理过了
        }

        val eventsToday = eventsCache[date]
        container.dotView.isVisible = !eventsToday.isNullOrEmpty()
    }

    private fun setupButtons() {
        binding.fabAdd.setOnClickListener {
            showEventDialog(selectedDate, null)
        }

        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnMonthView -> {
                        binding.calendarContainer.visibility = View.VISIBLE
                        binding.calendarView.visibility = View.VISIBLE
                        binding.weekCalendarView.visibility = View.GONE
                        binding.yearViewContainer.visibility = View.GONE
                        binding.weekHeaderLayout.visibility = View.VISIBLE

                        binding.recyclerView.visibility = View.VISIBLE
                        binding.divider2.visibility = View.VISIBLE

                        binding.fabAdd.show()
                        binding.calendarView.scrollToMonth(YearMonth.from(selectedDate))
                    }
                    R.id.btnWeekView -> {
                        binding.calendarContainer.visibility = View.VISIBLE
                        binding.calendarView.visibility = View.GONE
                        binding.weekCalendarView.visibility = View.VISIBLE
                        binding.yearViewContainer.visibility = View.GONE
                        binding.weekHeaderLayout.visibility = View.VISIBLE

                        binding.recyclerView.visibility = View.VISIBLE
                        binding.divider2.visibility = View.VISIBLE

                        binding.fabAdd.show()
                        binding.weekCalendarView.scrollToWeek(selectedDate)
                    }
                    R.id.btnYearView -> {
                        binding.calendarContainer.visibility = View.GONE
                        binding.weekHeaderLayout.visibility = View.GONE
                        binding.fabAdd.hide()

                        binding.recyclerView.visibility = View.GONE
                        binding.divider2.visibility = View.GONE

                        binding.yearViewContainer.visibility = View.VISIBLE

                        currentYearViewYear = selectedDate.year
                        updateYearViewAdapter()
                    }
                }
            }
        }
    }

    private fun refreshData() {
        updateAdapterForDate(selectedDate)
        refreshAllEventsForDots()
    }

    private fun deleteEvent(event: CalendarEvent) {
        lifecycleScope.launch(Dispatchers.IO) {
            ReminderManager.cancelReminder(this@MainActivity, event)
            database.eventDao().deleteEvent(event)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                refreshData()
            }
        }
    }

    private fun updateAdapterForDate(date: LocalDate) {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

        lifecycleScope.launch {
            database.eventDao().getEventsInRange(startOfDay, endOfDay).collectLatest { events ->
                eventAdapter.submitList(events)
            }
        }
    }

    private fun refreshAllEventsForDots() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allEvents = database.eventDao().getAllEventsSync()
            val newCache = allEvents.groupBy { event ->
                Instant.ofEpochMilli(event.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            withContext(Dispatchers.Main) {
                eventsCache = newCache
                binding.calendarView.notifyCalendarChanged()
                binding.weekCalendarView.notifyCalendarChanged()
            }
        }
    }

    private fun showEventDialog(date: LocalDate, eventToEdit: CalendarEvent? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_event, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etEventTitle)
        val etLocation = dialogView.findViewById<TextInputEditText>(R.id.etEventLocation)
        val etDesc = dialogView.findViewById<TextInputEditText>(R.id.etEventDesc)
        val tvStartDate = dialogView.findViewById<TextView>(R.id.tvStartDate)
        val tvStartTime = dialogView.findViewById<TextView>(R.id.tvStartTime)
        val tvEndDate = dialogView.findViewById<TextView>(R.id.tvEndDate)
        val tvEndTime = dialogView.findViewById<TextView>(R.id.tvEndTime)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val spinnerRemind = dialogView.findViewById<Spinner>(R.id.spinnerRemind)

        val isEditMode = eventToEdit != null
        val remindOptions = listOf(-1, 0, 5, 15, 30, 60)

        if (isEditMode) {
            etTitle.setText(eventToEdit!!.title)
            etLocation.setText(eventToEdit.location)
            etDesc.setText(eventToEdit.description)
            tempStartCalendar.timeInMillis = eventToEdit.startTime
            tempEndCalendar.timeInMillis = eventToEdit.endTime
            btnSave.text = "更新"

            val index = remindOptions.indexOf(eventToEdit.remindMinutes)
            if (index >= 0) {
                spinnerRemind.setSelection(index)
            }
        } else {
            tempStartCalendar.set(date.year, date.monthValue - 1, date.dayOfMonth)
            tempEndCalendar.timeInMillis = tempStartCalendar.timeInMillis + 3600000
            btnSave.text = "保存"
            spinnerRemind.setSelection(0)
        }

        fun updateTimeTexts() {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvStartDate.text = dateFormat.format(tempStartCalendar.time)
            tvStartTime.text = timeFormat.format(tempStartCalendar.time)
            tvEndDate.text = dateFormat.format(tempEndCalendar.time)
            tvEndTime.text = timeFormat.format(tempEndCalendar.time)
        }
        updateTimeTexts()

        tvStartDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                tempStartCalendar.set(y, m, d)
                updateTimeTexts()
            }, tempStartCalendar.get(Calendar.YEAR), tempStartCalendar.get(Calendar.MONTH), tempStartCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        tvStartTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                tempStartCalendar.set(Calendar.HOUR_OF_DAY, h)
                tempStartCalendar.set(Calendar.MINUTE, m)
                updateTimeTexts()
            }, tempStartCalendar.get(Calendar.HOUR_OF_DAY), tempStartCalendar.get(Calendar.MINUTE), true).show()
        }
        tvEndDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                tempEndCalendar.set(y, m, d)
                updateTimeTexts()
            }, tempEndCalendar.get(Calendar.YEAR), tempEndCalendar.get(Calendar.MONTH), tempEndCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        tvEndTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                tempEndCalendar.set(Calendar.HOUR_OF_DAY, h)
                tempEndCalendar.set(Calendar.MINUTE, m)
                updateTimeTexts()
            }, tempEndCalendar.get(Calendar.HOUR_OF_DAY), tempEndCalendar.get(Calendar.MINUTE), true).show()
        }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString()
            if (title.isBlank()) {
                Toast.makeText(this, "请输入标题", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedRemindIndex = spinnerRemind.selectedItemPosition
            val remindMinutes = remindOptions.getOrElse(selectedRemindIndex) { -1 }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    var savedEvent: CalendarEvent? = null

                    if (isEditMode) {
                        val updatedEvent = eventToEdit!!.copy(
                            title = title,
                            location = etLocation.text.toString(),
                            description = etDesc.text.toString(),
                            startTime = tempStartCalendar.timeInMillis,
                            endTime = tempEndCalendar.timeInMillis,
                            remindMinutes = remindMinutes
                        )
                        database.eventDao().updateEvent(updatedEvent)
                        savedEvent = updatedEvent
                        ReminderManager.cancelReminder(this@MainActivity, eventToEdit)
                    } else {
                        val newEvent = CalendarEvent(
                            title = title,
                            location = etLocation.text.toString(),
                            description = etDesc.text.toString(),
                            startTime = tempStartCalendar.timeInMillis,
                            endTime = tempEndCalendar.timeInMillis,
                            remindMinutes = remindMinutes
                        )
                        val newId = database.eventDao().insertEvent(newEvent)
                        savedEvent = newEvent.copy(id = newId)
                    }

                    if (savedEvent != null) {
                        ReminderManager.setReminder(this@MainActivity, savedEvent)
                    }

                    withContext(Dispatchers.Main) {
                        val msg = if (isEditMode) "更新成功" else "保存成功"
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        refreshData()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
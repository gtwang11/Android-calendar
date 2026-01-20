package com.example.calendar_vol1.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calendar_vol1.data.CalendarEvent
import com.example.calendar_vol1.databinding.EventItemLayoutBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 这是一个标准的 ListAdapter，专门用来高效显示列表
class EventAdapter(
    private val onEventClick: (CalendarEvent) -> Unit,
    private val onDeleteClick: (CalendarEvent) -> Unit
) : ListAdapter<CalendarEvent, EventAdapter.EventViewHolder>(EventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = EventItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EventViewHolder(private val binding: EventItemLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: CalendarEvent) {
            binding.itemTitle.text = event.title


            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startStr = timeFormat.format(Date(event.startTime))
            val endStr = timeFormat.format(Date(event.endTime))
            binding.itemTime.text = "$startStr - $endStr"

            binding.root.setOnClickListener {
                onEventClick(event)
            }


            binding.itemDeleteButton.setOnClickListener {
                onDeleteClick(event)
            }
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<CalendarEvent>() {
        override fun areItemsTheSame(oldItem: CalendarEvent, newItem: CalendarEvent) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CalendarEvent, newItem: CalendarEvent) = oldItem == newItem
    }
}
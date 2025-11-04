package com.example.llmapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.llmapp.R
import com.example.llmapp.data.ChatSession
import com.example.llmapp.databinding.ItemChatHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying chat history list in the navigation drawer
 */
class ChatHistoryListAdapter(
    private var sessions: List<ChatSession>,
    private val onSessionClick: (ChatSession) -> Unit,
    private val onSessionDelete: (ChatSession) -> Unit
) : RecyclerView.Adapter<ChatHistoryListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemChatHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.binding.textViewTitle.text = session.title
        holder.binding.textViewSubtitle.text = formatTimestamp(session.lastUpdated)
        
        holder.itemView.setOnClickListener {
            onSessionClick(session)
        }
        
        holder.binding.buttonDelete.setOnClickListener {
            onSessionDelete(session)
        }
    }

    override fun getItemCount(): Int = sessions.size

    fun updateSessions(newSessions: List<ChatSession>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}


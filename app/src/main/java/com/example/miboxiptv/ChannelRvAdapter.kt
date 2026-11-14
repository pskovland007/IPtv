package com.example.miboxiptv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class ChannelRvAdapter(
    private val getTheme: () -> AppThemeStyle,
    private val getUiScaleFactor: () -> Float,
    private val isTopUrl: (String) -> Boolean,
    private val isFavUrl: (String) -> Boolean,
    private val getPlayingUrl: () -> String?,
    private val onChannelFocused: (Int) -> Unit,
    private val onChannelClicked: (Int, Channel) -> Unit,
    private val onChannelLongClicked: (Int, Channel) -> Unit,
    private val onLeftPressedToGroups: () -> Unit,
    private val onRightPressedCloseMenu: () -> Unit
) : RecyclerView.Adapter<ChannelRvAdapter.ChannelVH>() {

    private val items = ArrayList<Channel>()
    var focusedPos: Int = 0
        private set

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items[position].url.hashCode().toLong()
    }

    fun submitChannels(newList: List<Channel>, initialFocusPos: Int) {
        val oldList = ArrayList(items)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                oldList[oldPos].url == newList[newPos].url
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                oldList[oldPos] == newList[newPos]
        })
        items.clear()
        items.addAll(newList)
        focusedPos = initialFocusPos.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        diff.dispatchUpdatesTo(this)
    }

    fun updateFocusedPosition(newPos: Int) {
        if (items.isEmpty()) return
        val bounded = newPos.coerceIn(0, items.lastIndex)
        val prev = focusedPos
        focusedPos = bounded
        if (prev in items.indices && prev != bounded) notifyItemChanged(prev)
        notifyItemChanged(bounded)
        onChannelFocused(bounded)
    }

    private fun extractMonogram(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size >= 2) return (words[0].take(1) + words[1].take(1)).uppercase()
        return name.take(2).uppercase()
    }

    private fun monogramColorFor(name: String): Int {
        val palette = listOf("#1E88E5", "#00897B", "#8E24AA", "#D81B60", "#F4511E", "#3949AB", "#43A047")
        return Color.parseColor(palette[abs(name.hashCode()) % palette.size])
    }

    class ChannelVH(val row: ChannelRowView) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelVH {
        val row = ChannelRowView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 3, 0, 3) }
        }
        return ChannelVH(row)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ChannelVH, position: Int) {
        val ch = items[position]
        val t = getTheme()
        val scale = getUiScaleFactor()
        val highlighted = (position == focusedPos)

        val topBadge = if (isTopUrl(ch.url)) "▲ " else ""
        val starBadge = if (isFavUrl(ch.url)) "★ " else ""
        holder.row.titleView.text = "${position + 1}. $topBadge$starBadge${ch.name}"
        holder.row.titleView.typeface = if (highlighted) t.boldFont else t.normalFont
        holder.row.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        holder.row.titleView.forceMarqueeActive = highlighted

        holder.row.badgeView.text = extractMonogram(ch.name)
        holder.row.badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f * scale)
        holder.row.badgeView.setBackgroundDrawable(GradientDrawable().apply {
            setColor(monogramColorFor(ch.name))
            cornerRadius = 12f
        })

        if (getPlayingUrl() == ch.url) {
            holder.row.eqView.barColor = if (highlighted) t.textOnAccent else Color.parseColor("#00E676")
            holder.row.eqView.visibility = View.VISIBLE
            holder.row.eqView.invalidate()
        } else {
            holder.row.eqView.visibility = View.GONE
        }

        if (highlighted) {
            holder.row.titleView.setTextColor(t.textOnAccent)
            holder.row.setBackgroundDrawable(GradientDrawable().apply {
                setColor(t.accentColor)
                cornerRadius = 20f
                setStroke(2, Color.WHITE)
            })
        } else {
            holder.row.titleView.setTextColor(t.textPrimaryColor)
            holder.row.setBackgroundDrawable(null)
        }

        holder.row.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val adapterPos = holder.bindingAdapterPosition
                if (adapterPos != RecyclerView.NO_POSITION && adapterPos != focusedPos) {
                    val old = focusedPos
                    focusedPos = adapterPos
                    holder.row.post {
                        if (old in items.indices) notifyItemChanged(old)
                        notifyItemChanged(adapterPos)
                    }
                    onChannelFocused(adapterPos)
                }
            }
        }

        holder.row.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION && adapterPos in items.indices) {
                updateFocusedPosition(adapterPos)
                onChannelClicked(adapterPos, items[adapterPos])
            }
        }

        holder.row.setOnLongClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION && adapterPos in items.indices) {
                updateFocusedPosition(adapterPos)
                onChannelLongClicked(adapterPos, items[adapterPos])
            }
            true
        }

        holder.row.setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val adapterPos = holder.bindingAdapterPosition
                if (adapterPos == RecyclerView.NO_POSITION) return@setOnKeyListener false
                val rv = v.parent as? RecyclerView
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    onLeftPressedToGroups()
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    onRightPressedCloseMenu()
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && adapterPos == 0 && items.size > 1) {
                    val lastIdx = items.lastIndex
                    updateFocusedPosition(lastIdx)
                    rv?.scrollToPosition(lastIdx)
                    rv?.post { rv.findViewHolderForAdapterPosition(lastIdx)?.itemView?.requestFocus() }
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && adapterPos == items.lastIndex && items.size > 1) {
                    updateFocusedPosition(0)
                    rv?.scrollToPosition(0)
                    rv?.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                    return@setOnKeyListener true
                }
            }
            false
        }
    }
}

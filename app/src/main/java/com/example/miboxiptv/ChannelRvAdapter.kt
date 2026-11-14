package com.example.miboxiptv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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
) : RecyclerView.Adapter<ChannelRvAdapter.VH>() {

    private val items = ArrayList<Channel>()
    var highlightedPos: Int = 0

    class VH(val row: ChannelRowView) : RecyclerView.ViewHolder(row)

    fun submitChannels(newList: List<Channel>, focusPos: Int) {
        items.clear()
        items.addAll(newList)
        highlightedPos = focusPos.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val row = ChannelRowView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 2) }
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
        }
        return VH(row)
    }

    private fun styleRow(row: ChannelRowView, ch: Channel?, isSelected: Boolean, isPlaying: Boolean) {
        val t = getTheme()
        val scale = getUiScaleFactor()
        val padV = (11 * scale).toInt()
        row.setPadding(6, padV, 6, padV)

        row.titleView.setSingleLine(true)
        row.titleView.maxLines = 1
        row.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f * scale)
        row.titleView.typeface = if (isSelected || isPlaying) t.boldFont else t.normalFont
        row.titleView.forceMarqueeActive = isSelected

        if (isSelected) {
            row.titleView.setTextColor(t.textOnAccent)
            row.setBackgroundDrawable(GradientDrawable().apply {
                setColor(t.accentColor)
                cornerRadius = 20f
                setStroke(2, Color.WHITE)
            })
        } else if (isPlaying) {
            row.titleView.setTextColor(t.accentColor)
            row.setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.argb(65, Color.red(t.accentColor), Color.green(t.accentColor), Color.blue(t.accentColor)))
                cornerRadius = 18f
                setStroke(1, t.accentColor)
            })
        } else {
            row.titleView.setTextColor(t.textPrimaryColor)
            row.setBackgroundDrawable(null)
        }

        if (ch != null) {
            val isTop = isTopUrl(ch.url)
            val isFav = isFavUrl(ch.url)
            val prefix = (if (isTop) "▲ " else "") + (if (isFav) "★ " else "")
            row.titleView.text = prefix + ch.name

            val letters = ch.name.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "TV" }
            row.badgeView.text = letters
            val hue = (abs(ch.name.hashCode()) % 360).toFloat()
            val badgeBgColor = Color.HSVToColor(floatArrayOf(hue, 0.65f, 0.78f))
            row.badgeView.setBackgroundDrawable(GradientDrawable().apply {
                setColor(badgeBgColor)
                cornerRadius = 13f
                setStroke(1, Color.argb(110, 255, 255, 255))
            })
        }

        if (isPlaying) {
            row.eqView.visibility = View.VISIBLE
            row.eqView.barColor = if (isSelected) t.textOnAccent else t.accentColor
        } else {
            row.eqView.visibility = View.GONE
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = items.getOrNull(position) ?: return
        val isPlaying = (ch.url == getPlayingUrl())
        val isSelected = (holder.itemView.hasFocus() || position == highlightedPos)

        styleRow(holder.row, ch, isSelected, isPlaying)

        holder.row.setOnFocusChangeListener { _, hasFocus ->
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener
            val curCh = items.getOrNull(adapterPos)
            if (hasFocus) {
                val prev = highlightedPos
                highlightedPos = adapterPos
                onChannelFocused(adapterPos)
                if (prev != adapterPos && prev in 0 until items.size) {
                    notifyItemChanged(prev)
                }
            }
            styleRow(holder.row, curCh, hasFocus, curCh?.url == getPlayingUrl())
        }

        holder.row.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos in items.indices) {
                highlightedPos = adapterPos
                onChannelClicked(adapterPos, items[adapterPos])
            }
        }

        holder.row.setOnLongClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos in items.indices) {
                highlightedPos = adapterPos
                onChannelLongClicked(adapterPos, items[adapterPos])
            }
            true
        }

        holder.row.setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val adapterPos = holder.bindingAdapterPosition
                val rv = v.parent as? RecyclerView
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onLeftPressedToGroups()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onRightPressedCloseMenu()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> if (adapterPos == 0 && items.size > 1) {
                        val lastIdx = items.size - 1
                        highlightedPos = lastIdx
                        rv?.scrollToPosition(lastIdx)
                        rv?.post { rv.findViewHolderForAdapterPosition(lastIdx)?.itemView?.requestFocus() }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (adapterPos == items.size - 1 && items.size > 1) {
                        highlightedPos = 0
                        rv?.scrollToPosition(0)
                        rv?.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }
}

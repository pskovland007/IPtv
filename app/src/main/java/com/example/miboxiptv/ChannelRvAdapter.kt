package com.example.miboxiptv

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

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
    var highlightedPos: Int = 0

    private val badgePalette = intArrayOf(
        Color.parseColor("#E53935"),
        Color.parseColor("#8E24AA"),
        Color.parseColor("#3949AB"),
        Color.parseColor("#039BE5"),
        Color.parseColor("#00897B"),
        Color.parseColor("#43A047"),
        Color.parseColor("#FB8C00"),
        Color.parseColor("#D81B60"),
        Color.parseColor("#5E35B1"),
        Color.parseColor("#00ACC1")
    )

    fun getExactRowHeightPx(): Int {
        val scale = getUiScaleFactor()
        return (54f * scale).toInt()
    }

    fun submitChannels(newList: List<Channel>, initialFocusPos: Int) {
        items.clear()
        items.addAll(newList)
        highlightedPos = initialFocusPos.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        notifyDataSetChanged()
    }

    fun getItemOrNull(pos: Int): Channel? = items.getOrNull(pos)

    private fun extractMonogram(name: String): String {
        val clean = name.replace(Regex("[^a-zA-Zа-яА-Я0-9 ]"), " ").trim()
        val parts = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val c1 = parts[0].first()
            val c2 = parts[1].first()
            return "$c1$c2".uppercase()
        }
        val single = parts.firstOrNull() ?: "TV"
        return single.take(2).uppercase()
    }

    private fun badgeColorForName(name: String): Int {
        val hash = (name.hashCode() and 0x7FFFFFFF)
        return badgePalette[hash % badgePalette.size]
    }

    inner class ChannelVH(val rowView: ChannelRowView) : RecyclerView.ViewHolder(rowView) {
        val selectorBg = GradientDrawable().apply { cornerRadius = 18f }
        val badgeBg = GradientDrawable().apply { cornerRadius = 10f }

        init {
            rowView.isFocusable = true
            rowView.isFocusableInTouchMode = false
            rowView.isClickable = true
            rowView.isLongClickable = true
            rowView.badgeView.includeFontPadding = false
            rowView.badgeView.setPadding(2, 0, 2, 0)
            rowView.titleView.includeFontPadding = false

            rowView.setOnFocusChangeListener { _, hasFocus ->
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener
                if (hasFocus) {
                    val prev = highlightedPos
                    highlightedPos = pos
                    onChannelFocused(pos)
                    if (prev != pos && prev in 0 until items.size) {
                        notifyItemChanged(prev)
                    }
                    bindStyle(items[pos], isSelectedRow = true)
                } else {
                    bindStyle(items[pos], isSelectedRow = false)
                }
            }

            rowView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos in 0 until items.size) {
                    onChannelClicked(pos, items[pos])
                }
            }

            rowView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos in 0 until items.size) {
                    onChannelLongClicked(pos, items[pos])
                    true
                } else false
            }

            rowView.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnKeyListener false
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
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (pos == 0 && items.size > 1) {
                                val lastIdx = items.size - 1
                                highlightedPos = lastIdx
                                rv?.scrollToPosition(lastIdx)
                                rv?.post { rv.findViewHolderForAdapterPosition(lastIdx)?.itemView?.requestFocus() }
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (pos == items.size - 1 && items.size > 1) {
                                highlightedPos = 0
                                rv?.scrollToPosition(0)
                                rv?.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                                return@setOnKeyListener true
                            }
                        }
                    }
                }
                false
            }
        }

        fun bindStyle(ch: Channel, isSelectedRow: Boolean) {
            val t = getTheme()
            val scale = getUiScaleFactor()
            val isPlaying = (ch.url == getPlayingUrl())

            val rowH = getExactRowHeightPx()
            rowView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                rowH
            )

            val badgeW = (44f * scale).toInt()
            val badgeH = (34f * scale).toInt()
            rowView.badgeView.layoutParams = FrameLayout.LayoutParams(
                badgeW,
                badgeH,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply { leftMargin = (12f * scale).toInt() }

            val monogram = extractMonogram(ch.name)
            rowView.badgeView.text = monogram
            val fontSp = if (monogram.length >= 2) 9.4f else 10.5f
            rowView.badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp * scale)
            badgeBg.cornerRadius = 9f * scale
            badgeBg.setColor(badgeColorForName(ch.name))
            badgeBg.setStroke(if (isSelectedRow) 2 else 1, if (isSelectedRow) Color.WHITE else Color.parseColor("#55FFFFFF"))
            rowView.badgeView.background = badgeBg

            val prefix = (if (isTopUrl(ch.url)) "▲ " else "") + (if (isFavUrl(ch.url)) "★ " else "")
            rowView.titleView.text = prefix + ch.name
            rowView.titleView.typeface = if (isSelectedRow || isPlaying) t.boldFont else t.normalFont
            rowView.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f * scale)
            rowView.titleView.forceMarqueeActive = isSelectedRow

            val leftTitleMargin = (12f * scale).toInt() + badgeW + (12f * scale).toInt()
            val rightTitleMargin = if (isPlaying) (44f * scale).toInt() else (14f * scale).toInt()
            rowView.titleView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            ).apply {
                leftMargin = leftTitleMargin
                rightMargin = rightTitleMargin
            }

            if (isSelectedRow) {
                rowView.titleView.setTextColor(t.textOnAccent)
                selectorBg.cornerRadius = 16f * scale
                selectorBg.setColor(t.accentColor)
                selectorBg.setStroke(2, Color.WHITE)
                rowView.background = selectorBg
            } else {
                rowView.titleView.setTextColor(if (isPlaying) t.accentColor else t.textPrimaryColor)
                rowView.background = null
            }

            if (isPlaying) {
                rowView.eqView.barColor = if (isSelectedRow) t.textOnAccent else t.accentColor
                rowView.eqView.visibility = View.VISIBLE
            } else {
                rowView.eqView.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelVH {
        val rowView = ChannelRowView(parent.context)
        return ChannelVH(rowView)
    }

    override fun onBindViewHolder(holder: ChannelVH, position: Int) {
        val ch = items[position]
        val isSel = holder.rowView.hasFocus() || (position == highlightedPos)
        holder.bindStyle(ch, isSel)
    }

    override fun getItemCount(): Int = items.size
}

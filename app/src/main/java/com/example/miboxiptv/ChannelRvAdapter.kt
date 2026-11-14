package com.example.miboxiptv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
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
    var focusedPos: Int = 0
    private var lastKeyStepTimeMs: Long = 0L

    class ChannelVH(val row: ChannelRowView) : RecyclerView.ViewHolder(row)

    fun submitChannels(newList: List<Channel>, focusIndex: Int = 0) {
        items.clear()
        items.addAll(newList)
        focusedPos = if (items.isEmpty()) 0 else focusIndex.coerceIn(0, items.lastIndex)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelVH {
        val row = ChannelRowView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            isLongClickable = true
        }
        return ChannelVH(row)
    }

    override fun onBindViewHolder(holder: ChannelVH, position: Int) {
        val ch = items.getOrNull(position) ?: return
        val row = holder.row

        bindRowVisuals(row, ch, position, row.hasFocus() || position == focusedPos)

        row.setOnFocusChangeListener { _, hasFocus ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos >= items.size) return@setOnFocusChangeListener
            if (hasFocus) {
                focusedPos = pos
                onChannelFocused(pos)
            }
            bindRowVisuals(row, items[pos], pos, hasFocus)
        }

        row.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos in items.indices) {
                focusedPos = pos
                onChannelClicked(pos, items[pos])
            }
        }

        row.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos in items.indices) {
                focusedPos = pos
                onChannelLongClicked(pos, items[pos])
            }
            true
        }

        row.setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || items.isEmpty()) return@setOnKeyListener false

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onLeftPressedToGroups()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onRightPressedCloseMenu()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Защита от зависания и краша при зажатой кнопке вниз (троттлинг повторов)
                        val now = System.currentTimeMillis()
                        if (event.repeatCount > 0 && now - lastKeyStepTimeMs < 42L) {
                            return@setOnKeyListener true
                        }
                        lastKeyStepTimeMs = now

                        if (pos >= items.size - 1) {
                            val rv = v.parent as? RecyclerView
                            focusedPos = 0
                            rv?.post {
                                rv.scrollToPosition(0)
                                rv.postDelayed({
                                    rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                }, 25L)
                            }
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val now = System.currentTimeMillis()
                        if (event.repeatCount > 0 && now - lastKeyStepTimeMs < 42L) {
                            return@setOnKeyListener true
                        }
                        lastKeyStepTimeMs = now

                        if (pos <= 0) {
                            val lastIdx = items.size - 1
                            val rv = v.parent as? RecyclerView
                            focusedPos = lastIdx
                            rv?.post {
                                rv.scrollToPosition(lastIdx)
                                rv.postDelayed({
                                    rv.findViewHolderForAdapterPosition(lastIdx)?.itemView?.requestFocus()
                                }, 25L)
                            }
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }
    }

    private fun bindRowVisuals(row: ChannelRowView, ch: Channel, position: Int, isHighlighted: Boolean) {
        val t = getTheme()
        val scale = getUiScaleFactor()
        val isTop = isTopUrl(ch.url)
        val isFav = isFavUrl(ch.url)
        val isPlayingNow = (getPlayingUrl() == ch.url)

        val padV = (12 * scale).toInt()
        row.setPadding(6, padV, 6, padV)

        val prefix = (if (isTop) "▲ " else "") + (if (isFav) "★ " else "")
        row.badgeView.text = "${position + 1}"
        row.badgeView.typeface = t.boldFont
        row.badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f * scale)
        row.badgeView.setBackgroundDrawable(GradientDrawable().apply {
            setColor(if (isHighlighted) Color.parseColor("#26000000") else t.cardBgColor)
            cornerRadius = 10f
            setStroke(1, if (isHighlighted) t.textOnAccent else t.strokeColor)
        })
        row.badgeView.setTextColor(if (isHighlighted) t.textOnAccent else t.accentColor)

        row.titleView.typeface = if (isHighlighted) t.boldFont else t.normalFont
        row.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        row.titleView.setSingleLine(true)
        row.titleView.maxLines = 1
        row.titleView.forceMarqueeActive = isHighlighted
        row.titleView.text = prefix + ch.name

        if (isPlayingNow) {
            row.eqView.barColor = if (isHighlighted) t.textOnAccent else t.accentColor
            row.eqView.visibility = android.view.View.VISIBLE
        } else {
            row.eqView.visibility = android.view.View.GONE
        }

        if (isHighlighted) {
            row.titleView.setTextColor(t.textOnAccent)
            row.setBackgroundDrawable(GradientDrawable().apply {
                setColor(t.accentColor)
                cornerRadius = 20f
                setStroke(2, Color.WHITE)
            })
        } else {
            row.titleView.setTextColor(t.textPrimaryColor)
            row.setBackgroundDrawable(null)
        }
    }
}

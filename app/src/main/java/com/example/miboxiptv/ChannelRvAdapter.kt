package com.example.miboxiptv

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

    var highlightedPos: Int = 0
    fun getExactRowHeightPx(): Int = (50f * getUiScaleFactor()).toInt()
    fun submitChannels(newList: List<Channel>, initialFocusPos: Int) {
        highlightedPos = initialFocusPos
    }

    inner class ChannelVH(val rowView: ChannelRowView) : RecyclerView.ViewHolder(rowView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelVH =
        ChannelVH(ChannelRowView(parent.context))

    override fun onBindViewHolder(holder: ChannelVH, position: Int) {}

    override fun getItemCount(): Int = 0
}

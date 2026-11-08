package com.example.miboxiptv

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Channel(val name: String, val url: String)

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var channelListView: ListView
    private lateinit var sidePanel: LinearLayout
    private lateinit var currentChannelBanner: TextView

    private val channels = mutableListOf<Channel>()
    private lateinit var adapter: ArrayAdapter<String>
    private var currentIndex = 0
    private val defaultPlaylistUrl = "https://iptv-org.github.io/iptv/index.m3u"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            useController = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(playerView)

        sidePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC121212"))
            layoutParams = FrameLayout.LayoutParams(
                750,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.START
            )
            setPadding(24, 24, 24, 24)
        }

        val btnSettings = Button(this).apply {
            text = "Изменить URL плейлиста (.m3u)"
            setOnClickListener { showUrlDialog() }
        }
        sidePanel.addView(btnSettings)

        channelListView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        sidePanel.addView(channelListView)
        root.addView(sidePanel)

        currentChannelBanner = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(32, 16, 32, 16)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { setMargins(0, 40, 40, 0) }
        }
        root.addView(currentChannelBanner)

        setContentView(root)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        channelListView.adapter = adapter

        channelListView.setOnItemClickListener { _, _, position, _ ->
            playChannel(position)
            hideSidePanel()
        }

        val prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("playlist_url", defaultPlaylistUrl) ?: defaultPlaylistUrl
        loadPlaylist(savedUrl)
    }

    private fun loadPlaylist(urlStr: String) {
        Toast.makeText(this, "Загрузка плейлиста...", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val lines = conn.inputStream.bufferedReader().readLines()
                val parsed = mutableListOf<Channel>()
                var currentName = "Канал"

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXTINF:")) {
                        currentName = trimmed.substringAfterLast(",").trim()
                    } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        parsed.add(Channel(currentName, trimmed))
                    }
                }

                runOnUiThread {
                    if (parsed.isNotEmpty()) {
                        channels.clear()
                        channels.addAll(parsed)
                        adapter.clear()
                        adapter.addAll(channels.mapIndexed { i, ch -> "${i + 1}. ${ch.name}" })
                        adapter.notifyDataSetChanged()
                        playChannel(0)
                        channelListView.requestFocus()
                    } else {
                        Toast.makeText(this, "Каналы не найдены в плейлисте", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun playChannel(index: Int) {
        if (channels.isEmpty() || index !in channels.indices) return
        currentIndex = index
        val channel = channels[index]

        player.setMediaItem(MediaItem.fromUri(channel.url))
        player.prepare()
        player.play()

        showBanner("${index + 1}. ${channel.name}")
    }

    private fun showBanner(text: String) {
        currentChannelBanner.text = text
        currentChannelBanner.visibility = View.VISIBLE
        currentChannelBanner.removeCallbacks(hideBannerRunnable)
        currentChannelBanner.postDelayed(hideBannerRunnable, 3500)
    }

    private val hideBannerRunnable = Runnable {
        currentChannelBanner.visibility = View.GONE
    }

    private fun showSidePanel() {
        sidePanel.visibility = View.VISIBLE
        channelListView.setSelection(currentIndex)
        channelListView.requestFocus()
    }

    private fun hideSidePanel() {
        sidePanel.visibility = View.GONE
    }

    private fun showUrlDialog() {
        val prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        val input = EditText(this).apply {
            setText(prefs.getString("playlist_url", defaultPlaylistUrl))
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Ссылка на плейлист M3U")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    prefs.edit().putString("playlist_url", newUrl).apply()
                    loadPlaylist(newUrl)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (sidePanel.visibility == View.GONE) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    showSidePanel()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (channels.isNotEmpty()) {
                        val next = if (currentIndex - 1 < 0) channels.size - 1 else currentIndex - 1
                        playChannel(next)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (channels.isNotEmpty()) {
                        val next = (currentIndex + 1) % channels.size
                        playChannel(next)
                    }
                    return true
                }
            }
        } else {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                hideSidePanel()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

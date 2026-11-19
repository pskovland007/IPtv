package com.example.miboxiptv

object BuiltinCatalog {
    const val FALLBACK_KIDS_CDN_1 = "https://hls.tv.3angels.ru/stream.m3u8"
    const val FALLBACK_MUSIC_CDN_1 = "https://1hdru-hls-otcnet.cdnvideo.ru/onehdmusic/tracks-v1a1/mono.m3u8"
    const val FALLBACK_MOVIES_CDN_1 = "https://vgtrkregion-reg.cdnvideo.ru/vgtrk/0/russia1-hd/index.m3u8"

    // 1. НОВОСТИ И ОБЩИЕ (Прямые открытые CDN без ключей)
    val newsChannels = listOf(
        Channel("Первый канал (Прямой CDN)", "https://edge1.1internet.tv/dash-live11/streams/1tv/1tvdash.mpd", "Новости и общие", ""),
        Channel("Россия 1 (Прямой CDN)", "https://vgtrkregion-reg.cdnvideo.ru/vgtrk/0/russia1-hd/index.m3u8", "Новости и общие", ""),
        Channel("НТВ (Прямой CDN)", "https://cdn2.ntv.ru/ntv0_hd/tracks-v1a1/mono.m3u8", "Новости и общие", ""),
        Channel("360° HD", "https://cdn-evacoder-tv.facecast.io/evacoder_hls_hi/CkxfR1xNUAJwTgtXTBZTAJli/index.m3u8", "Новости и общие", ""),
        Channel("Астрахань 24 HD", "https://streaming.astrakhan.ru/astrakhan24/playlist.m3u8", "Новости и общие", ""),
        Channel("Сургут 86 (1080p)", "https://sitv.ru/hls/s86.m3u8", "Новости и общие", ""),
        Channel("БСТ (Башкортостан)", "https://bsttv.bonus-tv.ru/cdn/bst/playlist.m3u8", "Новости и общие", "")
    )

    // 2. МУЗЫКА (Открытые русские и европейские 720p/1080p CDN без ключей)
    val musicChannels = listOf(
        Channel("1HD Music Television", "https://1hdru-hls-otcnet.cdnvideo.ru/onehdmusic/tracks-v1a1/mono.m3u8", "Музыка", ""),
        Channel("Восток ТВ", "https://cdn-01.bonus-tv.ru/vostoktv/index.m3u8", "Музыка", ""),
        Channel("МузСоюз", "https://hls-tvsoyuz.cdnvideo.ru/tvsoyuz2/muzsoyuz.6fw0-58xp-acts-esy0/playlist.m3u8", "Музыка", ""),
        Channel("Музыка 1", "https://kino-1.catcast.tv/content/37745/index.m3u8", "Музыка", ""),
        Channel("Музыка Кино", "https://kino-1.catcast.tv/content/37739/index.m3u8", "Музыка", ""),
        Channel("NOW 90s00s (1080p)", "https://amg01076-amg01076c19-rakuten-gb-8653.playouts.now.amagi.tv/playlist/amg01076-lightning-now90s00s-rakutengb/playlist.m3u8", "Музыка", ""),
        Channel("NOW Rock (1080p)", "https://lightning-now90s-samsungnz.amagi.tv/playlist.m3u8", "Музыка", ""),
        Channel("Qello Concerts (1080p)", "https://lotus.stingray.com/manifest/qello-qello001-montreal/samsungtvplus/master.m3u8", "Музыка", ""),
        Channel("Power Dance (1080p)", "https://livetv.powerapp.com.tr/dance/dance.smil/playlist.m3u8", "Музыка", ""),
        Channel("Music Box Classic", "http://88.212.15.50/live/mb_classic/index.m3u8", "Музыка", ""),
        Channel("Music Box Dance", "http://88.212.15.50/live/mb_dance/index.m3u8", "Музыка", ""),
        Channel("Music Box Hits", "http://88.212.15.50/live/mb_hits/index.m3u8", "Музыка", "")
    )

    // 3. СПОРТ (Открытые 720p/1080p CDN без ключей)
    val sportsChannels = listOf(
        Channel("Беларусь 5 (1080p)", "https://ngtrk.dc.beltelecom.by/ngtrk/smil:belarus5.smil/playlist.m3u8", "Спорт", ""),
        Channel("Беларусь 5 Интернет (1080p)", "https://edge59.dc.beltelecom.by/ngtrk/smil:belarus5int.smil/playlist.m3u8", "Спорт", ""),
        Channel("Astrahan.Ru Sport (720p)", "https://streaming.astrakhan.ru/astrakhanrusporthd/playlist.m3u8", "Спорт", "")
    )

    // 4. ОСТАЛЬНЫЕ КАТЕГОРИИ (Наполняются динамически из открытых бесплатных плейлистов openRusSources)
    val kidsChannels = listOf<Channel>()
    val moviesChannels = listOf<Channel>()
    val docChannels = listOf<Channel>()
    val entertainmentChannels = listOf<Channel>()
    val uhd2kChannels = listOf<Channel>()

    // Резервные потоки заполняются автоматически из открытых источников без платных ключей
    val origBackupPairs: List<Pair<String, String>> = emptyList()

    // Сводный базовый стартовый каталог (строго без платных ключей)
    val extraOpenStreams: List<Channel> = buildList {
        addAll(newsChannels)
        addAll(musicChannels)
        addAll(sportsChannels)
        addAll(kidsChannels)
        addAll(moviesChannels)
        addAll(docChannels)
        addAll(entertainmentChannels)
        addAll(uhd2kChannels)
    }
}

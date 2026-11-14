package com.example.miboxiptv

object BuiltinCatalog {
    // Надёжные открытые российские CDN-потоки для резервного подхвата
    const val FALLBACK_KIDS_CDN_1 = "https://cdn-01.bonus-tv.ru/karusel/index.m3u8"
    const val FALLBACK_MUSIC_CDN_1 = "https://1hdru-hls-otcnet.cdnvideo.ru/onehdmusic/tracks-v1a1/mono.m3u8"
    const val FALLBACK_MOVIES_CDN_1 = "https://cdn-01.bonus-tv.ru/domkino/index.m3u8"

    val extraOpenStreams = listOf(
        // Новости и общие (Официальные прямые российские CDN)
        Channel("Первый канал HD", "https://edge1.1internet.tv/dash-live11/streams/1tv/1tvdash.mpd", "Новости и общие", ""),
        Channel("Россия 1 HD", "https://vgtrkregion-reg.cdnvideo.ru/vgtrk/0/russia1-hd/index.m3u8", "Новости и общие", ""),
        Channel("Россия 24", "https://vgtrkregion-reg.cdnvideo.ru/vgtrk/0/russia24-sd/index.m3u8", "Новости и общие", ""),
        Channel("НТВ HD", "https://cdn2.ntv.ru/ntv0_hd/tracks-v1a1/mono.m3u8", "Новости и общие", ""),
        Channel("Мир 24 HD", "https://hls-mirtv.cdnvideo.ru/mirtv-parampublish/mir24_2500/playlist.m3u8", "Новости и общие", ""),
        Channel("Мир HD", "https://hls-mirtv.cdnvideo.ru/mirtv-parampublish/mir_2500/playlist.m3u8", "Новости и общие", ""),
        Channel("РБК ТВ HD", "https://online-video.rbc.ru/online/rbctv_1080p/index.m3u8", "Новости и общие", ""),
        Channel("Москва 24", "https://vgtrkregion-reg.cdnvideo.ru/vgtrk/0/moskva24-sd/index.m3u8", "Новости и общие", ""),
        Channel("360 Новости", "https://cdn-01.bonus-tv.ru/360news/tracks-v1a1/mono.m3u8", "Новости и общие", ""),
        Channel("Вместе-РФ", "https://cdn-01.bonus-tv.ru/vmesterf/index.m3u8", "Новости и общие", ""),

        // Музыка (Прямые рабочие российские CDN)
        Channel("1HD Music Television", "https://1hdru-hls-otcnet.cdnvideo.ru/onehdmusic/tracks-v1a1/mono.m3u8", "Музыка", ""),
        Channel("Восток ТВ (Музыка)", "https://cdn-01.bonus-tv.ru/vostoktv/index.m3u8", "Музыка", ""),
        Channel("Курай ТВ (Музыкальный)", "https://cdn-01.bonus-tv.ru/kuraytv/index.m3u8", "Музыка", ""),
        Channel("Туган Тел HD (Музыкальный)", "https://cdn-01.bonus-tv.ru/tugantel/index.m3u8", "Музыка", ""),
        Channel("Майдан ТВ (Музыкальный)", "https://cdn-01.bonus-tv.ru/maidan/index.m3u8", "Музыка", "")
    )
}

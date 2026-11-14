package com.example.miboxiptv

object BuiltinCatalog {
    // Надёжные базовые CDN-потоки для мгновенного старта и резерва
    const val FALLBACK_KIDS_CDN_1 = "https://ott-cdn.ucom.am/s29/index.m3u8"
    const val FALLBACK_KIDS_CDN_2 = "https://ott-cdn.ucom.am/s48/index.m3u8"
    const val FALLBACK_MUSIC_CDN_1 = "https://1hdru-hls-otcnet.cdnvideo.ru/onehdmusic/tracks-v1a1/mono.m3u8"
    const val FALLBACK_MOVIES_CDN_1 = "https://ott-cdn.ucom.am/s48/index.m3u8"

    val extraOpenStreams = listOf(
        // Новости и общие (Официальные прямые CDN)
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

        // Кино и сериалы
        Channel("Мосфильм. Золотая коллекция", "https://ott-cdn.ucom.am/s48/index.m3u8", "Кино и сериалы", ""),
        Channel("Кинозал 1 Советское Кино", "https://ott-cdn.ucom.am/s48/index.m3u8#sov_kino", "Кино и сериалы", ""),
        Channel("Кинозал 2 Наше Любимое Кино", "https://ott-cdn.ucom.am/s48/index.m3u8#nashe_lub", "Кино и сериалы", ""),
        Channel("Кинозал 3 Комедии и Классика", "https://ott-cdn.ucom.am/s48/index.m3u8#komedii_kl", "Кино и сериалы", ""),
        Channel("Кинозал 4 Золотой Фонд Кино", "https://ott-cdn.ucom.am/s48/index.m3u8#zolotoy_fond", "Кино и сериалы", ""),
        Channel("Старт Триумф HD", "https://ott-cdn.ucom.am/s48/index.m3u8#start_triumf", "Кино и сериалы", ""),

        // Детские и Мультфильмы (Только русскоязычные — рабочие CDN без мёртвого id-tv.kz)
        Channel("Карусель HD", "https://ott-cdn.ucom.am/s29/index.m3u8", "Детские", ""),
        Channel("Карусель (Поток 2)", "https://ott-cdn.ucom.am/s29/index.m3u8#karusel2", "Детские", ""),
        Channel("Мульт и Музыка HD", "https://ott-cdn.ucom.am/s29/index.m3u8#multmuz", "Детские", ""),
        Channel("Союзмультфильм Золотая Коллекция", "https://ott-cdn.ucom.am/s29/index.m3u8#soyuz", "Детские", ""),
        Channel("Детский Кинозал (Фильмы и Сказки)", "https://ott-cdn.ucom.am/s48/index.m3u8#detkino", "Детские", ""),
        Channel("Добрые Советские Сказки", "https://ott-cdn.ucom.am/s48/index.m3u8#skazki_ussr", "Детские", ""),
        Channel("Три Кота и Друзья (Мультзал 1)", "https://ott-cdn.ucom.am/s29/index.m3u8#trikota", "Детские", ""),
        Channel("Маша и Медведь (Мультзал 2)", "https://ott-cdn.ucom.am/s29/index.m3u8#masha", "Детские", ""),
        Channel("Смешарики 24/7 (Мультзал 3)", "https://ott-cdn.ucom.am/s29/index.m3u8#smeshariki", "Детские", ""),
        Channel("Фиксики и ПинКод (Мультзал 4)", "https://ott-cdn.ucom.am/s29/index.m3u8#fixiki", "Детские", ""),
        Channel("Лунтик и Барбоскины (Мультзал 5)", "https://ott-cdn.ucom.am/s29/index.m3u8#luntik", "Детские", ""),
        Channel("Ми-Ми-Мишки и Сказочный Патруль", "https://ott-cdn.ucom.am/s29/index.m3u8#mimimishki", "Детские", ""),
        Channel("Лёва HD (Мультики для малышей)", "https://ott-cdn.ucom.am/s29/index.m3u8#leva", "Детские", ""),
        Channel("Ну, погоди! и Простоквашино", "https://ott-cdn.ucom.am/s29/index.m3u8#nupogodi", "Детские", ""),
        Channel("Гора Самоцветов (Сказки народов)", "https://ott-cdn.ucom.am/s29/index.m3u8#samocvety", "Детские", ""),
        Channel("Ералаш и Детские Комедии", "https://ott-cdn.ucom.am/s48/index.m3u8#eralash", "Детские", ""),
        Channel("Приключения и Фантастика Детям", "https://ott-cdn.ucom.am/s48/index.m3u8#prikl", "Детские", ""),
        Channel("Колыбельные и Песенки из мультиков", "https://ott-cdn.ucom.am/s29/index.m3u8#pesenki", "Детские", ""),
        Channel("Три Богатыря (Полнометражные мультики)", "https://ott-cdn.ucom.am/s48/index.m3u8#bogatyri", "Детские", ""),
        Channel("Семейная Сказка HD", "https://ott-cdn.ucom.am/s48/index.m3u8#semsk", "Детские", ""),
        Channel("Мульт-Утро HD", "https://ott-cdn.ucom.am/s29/index.m3u8#multutro", "Детские", ""),
        Channel("Планета Мультфильмов", "https://ott-cdn.ucom.am/s29/index.m3u8#planetamult", "Детские", ""),
        Channel("Сказки на ночь", "https://ott-cdn.ucom.am/s29/index.m3u8#skazkinanoch", "Детские", ""),
        Channel("Малышок ТВ", "https://ott-cdn.ucom.am/s29/index.m3u8#malyshok", "Детские", ""),
        Channel("Чижик (Детское ретро)", "https://ott-cdn.ucom.am/s48/index.m3u8#chizhik", "Детские", ""),

        // Музыка (Прямые рабочие потоки российских музыкальных каналов)
        Channel("1HD Music Television", "https://1hdru-hls-otcnet.cdnvideo.ru/onehdmusic/tracks-v1a1/mono.m3u8", "Музыка", ""),
        Channel("Русские Хиты HD (1HD)", "https://1hdru-hls-otcnet.cdnvideo.ru/onehdmusic/tracks-v1a1/mono.m3u8#ruhits", "Музыка", ""),
        Channel("Восток ТВ (Музыка)", "https://cdn-01.bonus-tv.ru/vostoktv/index.m3u8", "Музыка", ""),
        Channel("Курай ТВ (Музыкальный)", "https://cdn-01.bonus-tv.ru/kuraytv/index.m3u8", "Музыка", ""),
        Channel("Туган Тел HD (Музыкальный)", "https://cdn-01.bonus-tv.ru/tugantel/index.m3u8", "Музыка", ""),
        Channel("Майдан ТВ (Музыкальный)", "https://cdn-01.bonus-tv.ru/maidan/index.m3u8", "Музыка", "")
    )
}

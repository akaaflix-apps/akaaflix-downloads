package com.akaaflix.prime.data.settings

/** Persisted user preferences (DataStore). Defaults are applied when a key is missing. */
data class AppSettings(
    // ---- General ----
    val language: AppLanguage = AppLanguage.ARABIC,
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: AccentColor = AccentColor.BLUE,
    val timeZone: String = "system",
    val notificationsEnabled: Boolean = true,
    val autoRefreshHours: Int = 12,
    val interfaceMode: InterfaceMode = InterfaceMode.AUTO,
    val hideLiveSection: Boolean = false,
    val hideMoviesSection: Boolean = false,
    val hideSeriesSection: Boolean = false,

    // ---- Player ----
    val defaultPlayer: PlayerChoice = PlayerChoice.BUILT_IN,
    val decoder: DecoderMode = DecoderMode.HARDWARE,
    val audioOutput: AudioOutputMode = AudioOutputMode.AUTO,
    val defaultAspect: AspectMode = AspectMode.FIT,
    val autoPlay: Boolean = true,
    val autoNextEpisode: Boolean = true,
    val resumePlayback: Boolean = true,
    val bufferProfile: BufferProfile = BufferProfile.NORMAL,
    val dataSaver: Boolean = false,
    val preferredAudioLanguage: String = "",
    val rememberLastChannel: Boolean = true,
    val livePreview: Boolean = true,

    // ---- Live stream format ----
    val liveFormat: LiveFormat = LiveFormat.AUTO,

    // ---- Subtitles ----
    val subtitlesEnabled: Boolean = true,
    val subtitleLanguage: String = "ar",
    val subtitleSource: SubtitleSource = SubtitleSource.EMBEDDED_AND_EXTERNAL,
    val subtitleEncoding: String = "UTF-8",
    val subtitleScale: Int = 100,
    val subtitleTextColor: Long = 0xFFFFFFFF,
    val subtitleBackgroundColor: Long = 0xFF000000,
    val subtitleBackgroundOpacity: Int = 50,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.BOTTOM,
    val subtitleEdge: SubtitleEdge = SubtitleEdge.OUTLINE,
    val subtitleDelayMs: Long = 0,

    // ---- EPG ----
    val epgEnabled: Boolean = true,
    val epgTimeShiftHours: Int = 0,
    val epgCacheHours: Int = 6,

    // ---- Parental controls ----
    val parentalEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val hideAdultContent: Boolean = true,
    val maxAgeRating: Int = 0,

    // ---- Search ----
    val saveSearchHistory: Boolean = true,
) {
    val hasPin: Boolean get() = pinHash.isNotBlank() && pinSalt.isNotBlank()

    /** Adult categories/streams are hidden unless parental control is off or hideAdult is disabled. */
    val shouldHideAdult: Boolean get() = parentalEnabled && hideAdultContent
}

enum class AppLanguage(val tag: String) { ARABIC("ar"), ENGLISH("en") }
enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class AccentColor(val argb: Long) {
    BLUE(0xFF0878FF), PURPLE(0xFF9B19FF), CYAN(0xFF00C2E0), GREEN(0xFF1DB954), ORANGE(0xFFFF8A1F), RED(0xFFF01835)
}
enum class InterfaceMode { AUTO, MOBILE, TV }
enum class PlayerChoice { BUILT_IN, VLC, MX_PLAYER, SYSTEM }
enum class DecoderMode { HARDWARE, SOFTWARE }
enum class AudioOutputMode { AUTO, PCM_STEREO }
enum class AspectMode { FIT, FILL, ZOOM, RATIO_16_9, RATIO_4_3 }
enum class BufferProfile(val minMs: Int, val maxMs: Int, val startMs: Int, val rebufferMs: Int) {
    LOW(8_000, 20_000, 1_000, 2_000),
    NORMAL(15_000, 50_000, 2_500, 5_000),
    HIGH(30_000, 120_000, 5_000, 10_000),
}
enum class LiveFormat(val extension: String?) {
    AUTO(null), HLS("m3u8"), MPEG_TS("ts"), DASH("mpd"), RTMP(null), PROGRESSIVE_MP4("mp4")
}
enum class SubtitleSource { EMBEDDED_AND_EXTERNAL, EMBEDDED_ONLY, EXTERNAL_ONLY }
enum class SubtitlePosition { BOTTOM, MIDDLE, TOP }
enum class SubtitleEdge { NONE, OUTLINE, DROP_SHADOW, RAISED, DEPRESSED }

object SubtitleOptions {
    val languages = listOf("ar", "en", "fr", "es", "de", "tr", "it", "pt", "ru", "fa", "ur", "hi", "nl", "el")
    val encodings = listOf("UTF-8", "windows-1256", "ISO-8859-6", "windows-1252", "ISO-8859-1", "UTF-16", "windows-1250", "windows-1251", "windows-1253", "windows-1254")
    val colors = listOf(0xFFFFFFFF, 0xFFFFE600, 0xFF00E5FF, 0xFF00E676, 0xFFFF5252, 0xFF000000, 0xFF9E9E9E)
}

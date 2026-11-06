@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.akaaflix.prime.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.akaaflix.prime.data.settings.AppSettings
import com.akaaflix.prime.data.settings.AudioOutputMode
import com.akaaflix.prime.data.settings.DecoderMode
import com.akaaflix.prime.data.settings.SubtitleSource
import com.akaaflix.prime.network.NetworkModule
import okhttp3.OkHttpClient

/**
 * Builds a Media3 ExoPlayer configured from the user's player settings: buffering profile,
 * hardware/software decoder preference, audio output (passthrough vs PCM), preferred
 * languages and data saver. HLS, DASH, progressive (MP4/MKV/TS) and RTMP are supported.
 */
class PlayerFactory(private val context: Context, private val okHttpClient: OkHttpClient) {

    fun create(settings: AppSettings): ExoPlayer {
        val httpFactory = OkHttpDataSource.Factory(okHttpClient).setUserAgent(NetworkModule.USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractors)

        val buffer = settings.bufferProfile
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(buffer.minMs, buffer.maxMs, buffer.startMs, buffer.rebufferMs)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredTextLanguage(settings.subtitleLanguage.ifBlank { null })
                .setPreferredAudioLanguage(settings.preferredAudioLanguage.ifBlank { null })
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.subtitlesEnabled || settings.subtitleSource == SubtitleSource.EXTERNAL_ONLY)
                .apply { if (settings.dataSaver) setMaxVideoSize(1280, 720).setMaxVideoBitrate(DATA_SAVER_BITRATE) }
                .build()
        }

        val renderersFactory = AkaaRenderersFactory(context, settings.audioOutput)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(codecSelector(settings.decoder))

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(SEEK_MS)
            .setSeekForwardIncrementMs(SEEK_MS)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .build()
    }

    /** Hardware decoders first (default) or software decoders first. */
    private fun codecSelector(mode: DecoderMode): MediaCodecSelector =
        if (mode == DecoderMode.HARDWARE) MediaCodecSelector.DEFAULT
        else MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val infos = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            infos.sortedByDescending { it.softwareOnly }
        }

    private class AkaaRenderersFactory(context: Context, private val audioOutput: AudioOutputMode) : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? {
            if (audioOutput != AudioOutputMode.PCM_STEREO) return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
            // Decode everything to PCM (no AC-3/E-AC-3/DTS passthrough to an AV receiver).
            @Suppress("DEPRECATION")
            return DefaultAudioSink.Builder(context)
                .setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
    }

    companion object {
        const val SEEK_MS = 10_000L
        const val DATA_SAVER_BITRATE = 2_500_000
    }
}

package com.akaaflix.prime.network

import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.domain.model.AccountInfo
import com.akaaflix.prime.domain.model.AccountStatus
import com.akaaflix.prime.domain.model.XtreamCredentials
import com.akaaflix.prime.network.dto.AuthResponseDto
import com.akaaflix.prime.network.dto.CategoryDto
import com.akaaflix.prime.network.dto.LiveStreamDto
import com.akaaflix.prime.network.dto.SeriesDto
import com.akaaflix.prime.network.dto.SeriesInfoResponseDto
import com.akaaflix.prime.network.dto.ShortEpgResponseDto
import com.akaaflix.prime.network.dto.VodInfoResponseDto
import com.akaaflix.prime.network.dto.VodStreamDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Talks to the Xtream Codes `player_api.php` endpoint and maps transport/protocol
 * failures to [AppError]s. The authenticated server response is the only source of
 * account information — nothing is invented locally.
 */
class XtreamDataSource(
    private val apiFactory: XtreamApiFactory,
    private val json: Json = XtreamJson,
    private val isOnline: () -> Boolean = { true },
) {

    /** Authenticates and returns account information. */
    suspend fun authenticate(creds: XtreamCredentials, fastConnect: Boolean = false): AppResult<AccountInfo> =
        call(creds, fastConnect = fastConnect, action = null, deserializer = AuthResponseDto.serializer()) { dto ->
            val user = dto.userInfo ?: return@call AppResult.Failure(AppError.NotXtreamServer)
            if (user.auth != 1L) return@call AppResult.Failure(AppError.InvalidCredentials)
            val status = AccountStatus.parse(user.status)
            when (status) {
                AccountStatus.EXPIRED -> return@call AppResult.Failure(AppError.AccountExpired)
                AccountStatus.BANNED -> return@call AppResult.Failure(AppError.AccountBanned)
                AccountStatus.DISABLED -> return@call AppResult.Failure(AppError.AccountDisabled)
                else -> Unit
            }
            val server = dto.serverInfo
            AppResult.Success(
                AccountInfo(
                    status = status,
                    rawStatus = user.status,
                    expiresAtEpochSec = user.expDate?.takeIf { it > 0 },
                    createdAtEpochSec = user.createdAt?.takeIf { it > 0 },
                    isTrial = user.isTrial == 1L,
                    activeConnections = user.activeConnections?.toInt(),
                    maxConnections = user.maxConnections?.toInt(),
                    allowedOutputFormats = user.allowedOutputFormats.map { it.lowercase() },
                    message = user.message,
                    serverTimezone = server?.timezone,
                    serverProtocol = server?.serverProtocol,
                    rtmpPort = server?.rtmpPort,
                    httpsPort = server?.httpsPort,
                    resolvedBaseUrl = creds.baseUrl,
                ),
            )
        }

    suspend fun liveCategories(creds: XtreamCredentials) = list(creds, XtreamActions.LIVE_CATEGORIES, CategoryDto.serializer())
    suspend fun vodCategories(creds: XtreamCredentials) = list(creds, XtreamActions.VOD_CATEGORIES, CategoryDto.serializer())
    suspend fun seriesCategories(creds: XtreamCredentials) = list(creds, XtreamActions.SERIES_CATEGORIES, CategoryDto.serializer())
    suspend fun liveStreams(creds: XtreamCredentials) = list(creds, XtreamActions.LIVE_STREAMS, LiveStreamDto.serializer())
    suspend fun vodStreams(creds: XtreamCredentials) = list(creds, XtreamActions.VOD_STREAMS, VodStreamDto.serializer())
    suspend fun series(creds: XtreamCredentials) = list(creds, XtreamActions.SERIES, SeriesDto.serializer())

    suspend fun vodInfo(creds: XtreamCredentials, vodId: Long): AppResult<VodInfoResponseDto> =
        call(creds, action = XtreamActions.VOD_INFO, extra = mapOf("vod_id" to vodId.toString()), deserializer = VodInfoResponseDto.serializer()) {
            AppResult.Success(it)
        }

    suspend fun seriesInfo(creds: XtreamCredentials, seriesId: Long): AppResult<SeriesInfoResponseDto> =
        call(creds, action = XtreamActions.SERIES_INFO, extra = mapOf("series_id" to seriesId.toString()), deserializer = SeriesInfoResponseDto.serializer()) {
            AppResult.Success(it)
        }

    suspend fun shortEpg(creds: XtreamCredentials, streamId: Long, limit: Int = 6): AppResult<ShortEpgResponseDto> =
        call(
            creds,
            action = XtreamActions.SHORT_EPG,
            extra = mapOf("stream_id" to streamId.toString(), "limit" to limit.toString()),
            deserializer = ShortEpgResponseDto.serializer(),
        ) { AppResult.Success(it) }

    /** Lists: some panels answer `{}` / `null` / `false` for empty lists — treat those as empty. */
    private suspend fun <T> list(
        creds: XtreamCredentials,
        action: String,
        itemSerializer: kotlinx.serialization.KSerializer<T>,
    ): AppResult<List<T>> = withContext(Dispatchers.IO) {
        try {
            val body = apiFactory.create(creds.baseUrl).playerApi(creds.username, creds.password, action)
            body.use { b ->
                if (firstSignificantByte(b) != '['.code) {
                    // Not an array: object, null, false or empty body => empty list.
                    return@withContext AppResult.Success(emptyList())
                }
                AppResult.Success(json.decodeFromStream(ListSerializer(itemSerializer), b.byteStream()))
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppResult.Failure(mapError(t))
        }
    }

    /** Peeks (without consuming) the first non-whitespace, non-BOM byte of the body. */
    private fun firstSignificantByte(body: ResponseBody): Int {
        val source = body.source()
        while (source.request(1)) {
            val b = source.buffer[0].toInt() and 0xff
            if (b == 0x20 || b == 0x0a || b == 0x0d || b == 0x09 || b == 0xef || b == 0xbb || b == 0xbf) {
                source.skip(1)
                continue
            }
            return b
        }
        return -1
    }

    private suspend fun <T, R> call(
        creds: XtreamCredentials,
        fastConnect: Boolean = false,
        action: String?,
        extra: Map<String, String> = emptyMap(),
        deserializer: DeserializationStrategy<T>,
        mapper: (T) -> AppResult<R>,
    ): AppResult<R> = withContext(Dispatchers.IO) {
        try {
            val body = apiFactory.create(creds.baseUrl, fastConnect).playerApi(creds.username, creds.password, action, extra)
            val element: JsonElement = body.use { json.decodeFromStream(JsonElement.serializer(), it.byteStream()) }
            if (element !is JsonObject) {
                // Empty arrays / false are returned by some panels for "not found" or bad auth.
                return@withContext if (action == null) AppResult.Failure(AppError.InvalidCredentials)
                else if (element is JsonArray && element.isEmpty()) AppResult.Failure(AppError.NotFound)
                else AppResult.Failure(AppError.InvalidResponse)
            }
            mapper(json.decodeFromJsonElement(deserializer, element))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppResult.Failure(mapError(t))
        }
    }

    fun mapError(t: Throwable): AppError = when (t) {
        is HttpException -> when (t.code()) {
            401, 403 -> AppError.InvalidCredentials
            404 -> AppError.NotXtreamServer
            in 500..599 -> AppError.Server(t.code())
            else -> AppError.Server(t.code())
        }
        is SocketTimeoutException -> AppError.Timeout
        is UnknownHostException, is NoRouteToHostException -> if (!isOnline()) AppError.Offline else AppError.ServerUnreachable
        is ConnectException -> if (!isOnline()) AppError.Offline else AppError.ServerUnreachable
        is SSLException -> AppError.Tls
        is InterruptedIOException -> AppError.Timeout
        is SerializationException, is IllegalArgumentException -> AppError.InvalidResponse
        is IOException -> if (!isOnline()) AppError.Offline else AppError.ServerUnreachable
        else -> AppError.Unknown
    }
}

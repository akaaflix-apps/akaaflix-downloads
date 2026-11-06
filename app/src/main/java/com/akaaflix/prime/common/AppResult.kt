package com.akaaflix.prime.common

import androidx.annotation.StringRes
import com.akaaflix.prime.R

/** Result wrapper used across the data and domain layers. */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data
fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error

/**
 * Typed application errors. Each error maps to a localized (Arabic/English) message.
 * Messages never include credentials or full server URLs.
 */
sealed class AppError(@StringRes val messageRes: Int) {
    data object Offline : AppError(R.string.error_offline)
    data object ServerUnreachable : AppError(R.string.error_server_unreachable)
    data object Timeout : AppError(R.string.error_timeout)
    data object Tls : AppError(R.string.error_tls)
    data object InvalidCredentials : AppError(R.string.error_invalid_credentials)
    data object AccountExpired : AppError(R.string.error_account_expired)
    data object AccountDisabled : AppError(R.string.error_account_disabled)
    data object AccountBanned : AppError(R.string.error_account_banned)
    data object NotXtreamServer : AppError(R.string.error_not_xtream)
    data class Server(val code: Int) : AppError(R.string.error_server)
    data object InvalidResponse : AppError(R.string.error_invalid_response)
    data object InvalidUrl : AppError(R.string.error_invalid_url)
    data object NoActivePlaylist : AppError(R.string.error_no_active_playlist)
    data object NotFound : AppError(R.string.error_not_found)
    data object Duplicate : AppError(R.string.error_duplicate_playlist)
    data object Storage : AppError(R.string.error_storage)
    data object Unknown : AppError(R.string.error_unknown)
}

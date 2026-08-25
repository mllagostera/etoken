package com.etoken.ui.common

import com.etoken.R
import java.io.IOException
import retrofit2.HttpException

/** The failure modes worth telling the user apart. */
enum class LoadError(val messageRes: Int) {
    /** Moxfield has no such user, or the user has no public decks. */
    NotFound(R.string.error_user_not_found),

    /** Moxfield's Cloudflare layer turned the request away. */
    Blocked(R.string.error_blocked),

    /** No connectivity, DNS failure, timeout. */
    Network(R.string.error_network),

    Unknown(R.string.error_unknown),
    ;

    companion object {
        fun from(throwable: Throwable): LoadError = when {
            throwable is HttpException && throwable.code() == 404 -> NotFound
            // 403 is what Cloudflare answers when it decides the client is a
            // bot; it is not the same problem as a missing user.
            throwable is HttpException && throwable.code() == 403 -> Blocked
            throwable is IOException -> Network
            else -> Unknown
        }
    }
}

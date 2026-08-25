package com.etoken.data

import com.etoken.data.moxfield.MoxfieldApi
import com.etoken.data.scryfall.ScryfallApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit

object Network {

    /**
     * Moxfield sits behind Cloudflare and rejects clients that don't look like
     * a browser; the User-Agent and Referer below are the pair
     * commander-companion's Go client uses in production.
     */
    private const val MOXFIELD_USER_AGENT =
        "Mozilla/5.0 (compatible; etoken/0.1; +https://github.com/mllagostera/etoken)"
    private const val MOXFIELD_REFERER = "https://www.moxfield.com/"

    /**
     * Scryfall asks every client to identify itself and to leave 50-100 ms
     * between requests (https://scryfall.com/docs/api). Both are enforced
     * below rather than left to callers.
     */
    private const val SCRYFALL_USER_AGENT = "etoken/0.1 (+https://github.com/mllagostera/etoken)"
    private const val SCRYFALL_MIN_INTERVAL_MS = 100L

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // Scryfall's /cards/collection rejects an identifier object that
        // carries more than one key, so null fields must not be serialized.
        explicitNulls = false
    }

    fun moxfieldApi(): MoxfieldApi = retrofit(
        baseUrl = MoxfieldApi.BASE_URL,
        client = baseClient()
            .addInterceptor(
                HeaderInterceptor(
                    "User-Agent" to MOXFIELD_USER_AGENT,
                    "Accept" to "application/json",
                    "Referer" to MOXFIELD_REFERER,
                ),
            )
            .addInterceptor(RetryInterceptor())
            .build(),
    ).create(MoxfieldApi::class.java)

    fun scryfallApi(): ScryfallApi = retrofit(
        baseUrl = ScryfallApi.BASE_URL,
        client = baseClient()
            .addInterceptor(
                HeaderInterceptor(
                    "User-Agent" to SCRYFALL_USER_AGENT,
                    "Accept" to "application/json",
                ),
            )
            .addInterceptor(RateLimitInterceptor(SCRYFALL_MIN_INTERVAL_MS))
            .addInterceptor(RetryInterceptor())
            .build(),
    ).create(ScryfallApi::class.java)

    private fun baseClient(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}

private class HeaderInterceptor(private vararg val headers: Pair<String, String>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        return chain.proceed(request)
    }
}

/** Serializes requests so that consecutive calls are at least [minIntervalMs] apart. */
private class RateLimitInterceptor(private val minIntervalMs: Long) : Interceptor {
    private val lock = Any()
    private var lastRequestAt = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(lock) {
            val wait = minIntervalMs - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) {
                try {
                    Thread.sleep(wait)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("interrupted while rate limiting", e)
                }
            }
            lastRequestAt = System.currentTimeMillis()
        }
        return chain.proceed(chain.request())
    }
}

/**
 * Retries transient failures — network errors, 5xx and 429 — with exponential
 * backoff, honouring Retry-After when the server sends one. A 404 is never
 * retried: a deck that doesn't exist won't start existing.
 *
 * Mirrors the retry policy of commander-companion's Moxfield client.
 */
private class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val initialDelayMs: Long = 200L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var delay = initialDelayMs
        var lastError: IOException? = null

        for (attempt in 1..maxAttempts) {
            val response = try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                lastError = e
                if (attempt == maxAttempts) throw e
                sleep(delay)
                delay *= 2
                continue
            }

            if (!isTransient(response.code) || attempt == maxAttempts) return response

            val wait = retryAfterMs(response) ?: delay
            response.close()
            sleep(wait)
            delay *= 2
        }

        throw lastError ?: IOException("request failed after $maxAttempts attempts")
    }

    private fun isTransient(code: Int) = code == 429 || code >= 500

    /** Only the seconds form; that is the one these APIs use in practice. */
    private fun retryAfterMs(response: Response): Long? =
        response.header("Retry-After")?.toLongOrNull()?.takeIf { it >= 0 }?.times(1_000)

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted while retrying", e)
        }
    }
}

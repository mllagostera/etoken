package com.etoken.data.scryfall

import retrofit2.http.Body
import retrofit2.http.POST

interface ScryfallApi {

    /** Resolves up to [MAX_IDENTIFIERS] cards in one round trip. */
    @POST("cards/collection")
    suspend fun collection(@Body body: CollectionRequest): CollectionResponse

    companion object {
        const val BASE_URL = "https://api.scryfall.com/"

        /** Hard limit imposed by the API. */
        const val MAX_IDENTIFIERS = 75
    }
}

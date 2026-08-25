package com.etoken.data.moxfield

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MoxfieldApi {

    /**
     * Moxfield has no "decks by user" endpoint; the deck-search endpoint
     * filtered to a single author is how the site itself does it. Paginated —
     * see [SearchResponse.totalPages].
     */
    @GET("v2/decks/search-sfw")
    suspend fun searchDecks(
        @Query("authorUserNames") username: String,
        @Query("pageNumber") pageNumber: Int,
        @Query("pageSize") pageSize: Int,
        @Query("sortType") sortType: String = "Updated",
        @Query("sortDirection") sortDirection: String = "Descending",
        @Query("includePinned") includePinned: Boolean = true,
        @Query("showIllegal") showIllegal: Boolean = true,
    ): SearchResponse

    @GET("v3/decks/all/{publicId}")
    suspend fun deck(@Path("publicId") publicId: String): DeckResponse

    companion object {
        const val BASE_URL = "https://api2.moxfield.com/"
        const val PAGE_SIZE = 100
    }
}

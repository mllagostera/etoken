package com.etoken.data.scryfall

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the parts of Scryfall's API this app touches.
 *
 * Unlike Moxfield, Scryfall is documented and stable (https://scryfall.com/docs/api).
 * What matters here is `all_parts`: for every card that can create a token,
 * Scryfall lists the token's own card object as a related part, which is what
 * lets us go from "the 99 in this deck" to "every token this deck can make"
 * without maintaining a card-text parser.
 */

/** Request body for `POST /cards/collection`. Scryfall caps this at 75 identifiers. */
@Serializable
data class CollectionRequest(val identifiers: List<Identifier>)

/**
 * Scryfall accepts several identifier shapes; we use the Scryfall UUID when
 * Moxfield gave us one and fall back to the exact card name when it didn't.
 * Nulls are dropped by the Json instance (explicitNulls = false) so each
 * object carries exactly one key, as the API requires.
 */
@Serializable
data class Identifier(
    val id: String? = null,
    val name: String? = null,
) {
    companion object {
        fun byId(id: String) = Identifier(id = id)
        fun byName(name: String) = Identifier(name = name)
    }
}

@Serializable
data class CollectionResponse(
    val data: List<ScryfallCard> = emptyList(),
)

@Serializable
data class ScryfallCard(
    val id: String = "",
    val name: String = "",
    val layout: String? = null,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    /**
     * Scryfall's structured keyword list: "Flying", "Haste", "Trample".
     *
     * Printed haste is read off this rather than out of [oracleText], which is
     * the same rule the tokens themselves follow — they come from `all_parts`,
     * never from parsing a card's text. A token that merely *grants* haste to
     * other creatures does not appear here, and telling those two apart in
     * prose is exactly the bug a parser would keep producing.
     */
    val keywords: List<String> = emptyList(),
    /** Strings, not numbers: Magic prints `*`, `1+*` and `X` as power/toughness. */
    val power: String? = null,
    val toughness: String? = null,
    @SerialName("image_uris") val imageUris: ImageUris? = null,
    /** Double-faced cards carry their art per face rather than at the top level. */
    @SerialName("card_faces") val cardFaces: List<CardFace> = emptyList(),
    /** Tokens, emblems, meld results and combo pieces related to this card. */
    @SerialName("all_parts") val allParts: List<RelatedCard> = emptyList(),
) {
    /** The image to show for this card, tolerating the double-faced layout. */
    fun imageUrl(): String? =
        imageUris?.best() ?: cardFaces.firstNotNullOfOrNull { it.imageUris?.best() }
}

@Serializable
data class CardFace(
    val name: String = "",
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("image_uris") val imageUris: ImageUris? = null,
)

@Serializable
data class ImageUris(
    val small: String? = null,
    val normal: String? = null,
    val large: String? = null,
    @SerialName("art_crop") val artCrop: String? = null,
    @SerialName("border_crop") val borderCrop: String? = null,
    val png: String? = null,
) {
    /** `normal` is the sweet spot for a phone grid; the rest are fallbacks. */
    fun best(): String? = normal ?: large ?: small ?: png ?: borderCrop
}

/**
 * An entry in [ScryfallCard.allParts]. It is only a stub — it carries the
 * related card's id and name but no artwork, so the token ids gathered here
 * have to be resolved through a second `/cards/collection` call.
 */
@Serializable
data class RelatedCard(
    val id: String = "",
    /** "token", "meld_part", "meld_result" or "combo_piece". */
    val component: String = "",
    val name: String = "",
    @SerialName("type_line") val typeLine: String = "",
    val uri: String = "",
)

package com.etoken.data.moxfield

/**
 * Builds art-crop URLs on Moxfield's asset CDN — the same image Moxfield uses
 * as a deck's og:image.
 *
 * Ported from commander-companion's `mainImageURL`, including the trap it
 * documents: for a two-faced card the crop only exists under the *face* id
 * with the `card-face-` prefix. Requesting `card-{faceId}` also answers 200,
 * but the face-id and card-id namespaces are distinct, so that URL silently
 * serves a completely different card's art.
 */
object MoxfieldImages {

    private const val CARD_TEMPLATE = "https://assets.moxfield.net/cards/card-%s-art_crop.jpg"
    private const val FACE_TEMPLATE = "https://assets.moxfield.net/cards/card-face-%s-art_crop.jpg"

    fun artCrop(card: MoxfieldCard?): String? {
        if (card == null) return null

        val frontFaceId = card.cardFaces.firstOrNull()?.id
        if (!frontFaceId.isNullOrEmpty()) return FACE_TEMPLATE.format(frontFaceId)

        return card.id.takeIf { it.isNotEmpty() }?.let { CARD_TEMPLATE.format(it) }
    }
}

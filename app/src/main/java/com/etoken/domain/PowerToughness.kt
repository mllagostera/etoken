package com.etoken.domain

/**
 * Renders a token's power/toughness with its +1/+1 counters applied.
 *
 * Magic's printed values are not always numbers — `*`, `1+*` and `X` all
 * appear on tokens — so the arithmetic is attempted and the expression is
 * shown unevaluated when it can't be done.
 */
object PowerToughness {

    fun display(power: String?, toughness: String?, counters: Int): String? {
        if (power.isNullOrBlank() || toughness.isNullOrBlank()) return null
        if (counters == 0) return "$power/$toughness"

        val boostedPower = power.toIntOrNull()?.plus(counters)
        val boostedToughness = toughness.toIntOrNull()?.plus(counters)

        return if (boostedPower != null && boostedToughness != null) {
            "$boostedPower/$boostedToughness"
        } else {
            // e.g. */* with two counters reads as "*/* +2/+2".
            "$power/$toughness +$counters/+$counters"
        }
    }
}

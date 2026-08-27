package com.etoken

import com.etoken.domain.model.TokenCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only creatures can be summoning sick. `isCreature` is what
 * `BoardViewModel.add` gates `entersSick` on -- see [TappedTokenTest] and
 * `BoardRulesTest` for the board side of the rule.
 */
class CreatureTokenTest {

    private fun token(typeLine: String) = TokenCard(
        id = "id",
        name = "Whatever",
        typeLine = typeLine,
        imageUrl = null,
        oracleText = null,
        createdBy = emptyList(),
    )

    @Test
    fun `a token creature is a creature`() {
        assertTrue(token("Token Creature — Goblin").isCreature)
        assertTrue(token("Legendary Creature — Zombie").isCreature)
    }

    @Test
    fun `an artifact token is not a creature`() {
        assertFalse(token("Token Artifact — Treasure").isCreature)
        assertFalse(token("Token Artifact — Clue").isCreature)
    }

    @Test
    fun `an emblem is not a creature`() {
        assertFalse(token("Emblem").isCreature)
    }
}

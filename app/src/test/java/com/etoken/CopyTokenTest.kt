package com.etoken

import com.etoken.domain.model.TokenCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyTokenTest {

    private fun token(name: String) = TokenCard(
        id = "id",
        name = name,
        typeLine = "Token",
        imageUrl = null,
        oracleText = null,
        createdBy = emptyList(),
    )

    @Test
    fun `Scryfall's copy token is recognised`() {
        assertTrue(token("Copy").isCopy)
    }

    @Test
    fun `the check is not thrown by casing or stray spacing`() {
        assertTrue(token("copy").isCopy)
        assertTrue(token("  Copy  ").isCopy)
    }

    @Test
    fun `tokens that merely mention copying are not copy tokens`() {
        assertFalse(token("Goblin").isCopy)
        assertFalse(token("Copycat").isCopy)
        assertFalse(token("Treasure").isCopy)
    }
}

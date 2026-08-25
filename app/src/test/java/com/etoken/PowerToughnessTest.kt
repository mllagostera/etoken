package com.etoken

import com.etoken.domain.PowerToughness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerToughnessTest {

    @Test
    fun `a token with no printed size has none to show`() {
        // Treasures and emblems are not creatures.
        assertNull(PowerToughness.display(null, null, counters = 0))
        assertNull(PowerToughness.display("1", null, counters = 0))
        assertNull(PowerToughness.display("", "1", counters = 2))
    }

    @Test
    fun `without counters it is just the printed size`() {
        assertEquals("1/1", PowerToughness.display("1", "1", counters = 0))
    }

    @Test
    fun `counters are added to both halves`() {
        assertEquals("3/3", PowerToughness.display("1", "1", counters = 2))
        assertEquals("7/8", PowerToughness.display("6", "7", counters = 1))
    }

    @Test
    fun `a variable size is shown unevaluated rather than guessed at`() {
        assertEquals("*/* +2/+2", PowerToughness.display("*", "*", counters = 2))
        assertEquals("1+*/1+* +1/+1", PowerToughness.display("1+*", "1+*", counters = 1))
    }
}

package com.etoken

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A token printed with haste enters able to attack, and the screen has to say
 * so without the player clearing summoning sickness by hand.
 *
 * `HasteTokenTest` covers both halves of the rule off the device. This is the
 * screen reading it: the deck's Hellion is printed with haste and its Goblin is
 * not, so the same gesture has to end in two different states.
 */
@RunWith(AndroidJUnit4::class)
class HasteBoardTest {

    @get:Rule
    val compose = createComposeRule()

    private val robot by lazy { BoardRobot(compose) }

    @Test
    fun a_hasty_token_arrives_able_to_attack() {
        robot.show()

        // The Hellion is the third cell in the picker, which is a row below the
        // fold on CI's 320x640dp emulator: it has to be scrolled to.
        robot.add(Fakes.HASTE_TOKEN_NAME, quantity = 2, scroll = true)

        robot.awaitText("×2")
        // The badge is the whole claim: nothing on this table is waiting.
        compose.onAllNodesWithText(robot.str(R.string.entry_sick)).assertCountEquals(0)
    }

    @Test
    fun the_deck_s_other_creature_still_arrives_sick() {
        robot.show()

        robot.add(Fakes.TOKEN_NAME, quantity = 2)

        robot.awaitText(robot.str(R.string.entry_sick))
        compose.onNodeWithText(robot.str(R.string.board_begin_turn)).assertIsDisplayed()
    }

    @Test
    fun a_hasty_entry_can_still_be_made_sick_by_hand() {
        // Printed haste can be turned off at the table — a Torpor Orb effect,
        // or simply a miscount — so automating it must not take the control away.
        robot.show()
        robot.add(Fakes.HASTE_TOKEN_NAME, scroll = true)
        robot.awaitText(Fakes.HASTE_TOKEN_NAME)

        robot.openEntry(Fakes.HASTE_TOKEN_NAME)
        compose.onNodeWithText(robot.str(R.string.entry_ready)).performClick()

        robot.awaitText(robot.str(R.string.entry_sick))
    }
}

package com.etoken

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two rules that share a screen: only creatures can be summoning sick, and any
 * token can be told to enter tapped. `CreatureTokenTest` covers the first as a
 * pure function; this drives the real screen asking that question of a real
 * (fake) token instead of assuming every token is a creature.
 */
@RunWith(AndroidJUnit4::class)
class CreatureSicknessAndTappedBoardTest {

    @get:Rule
    val compose = createComposeRule()

    private val robot by lazy { BoardRobot(compose) }

    @Test
    fun a_treasure_never_shows_summoning_sickness() {
        robot.show()

        robot.add(Fakes.TREASURE_TOKEN_NAME, quantity = 2)

        robot.awaitText("×2")
        compose.onAllNodesWithText(robot.str(R.string.entry_sick)).assertCountEquals(0)
    }

    @Test
    fun a_creature_still_arrives_summoning_sick() {
        robot.show()

        robot.add(Fakes.TOKEN_NAME, quantity = 2)

        robot.awaitText(robot.str(R.string.entry_sick))
        compose.onNodeWithText("×2").assertIsDisplayed()
    }

    @Test
    fun turning_on_enter_tapped_makes_the_new_entry_arrive_tapped() {
        robot.show()

        robot.add(Fakes.TOKEN_NAME, quantity = 2, tapped = true)

        robot.awaitText(robot.str(R.string.entry_tapped))
    }

    @Test
    fun tokens_arrive_untapped_by_default() {
        robot.show()

        robot.add(Fakes.TOKEN_NAME, quantity = 2)

        robot.awaitText("×2")
        compose.onAllNodesWithText(robot.str(R.string.entry_tapped)).assertCountEquals(0)
    }

    @Test
    fun a_treasure_can_still_be_tapped_by_hand() {
        // Non-creature and never sick, but a Treasure is spent by tapping it,
        // so the gesture has to work on one.
        robot.show()
        robot.add(Fakes.TREASURE_TOKEN_NAME)
        robot.awaitText(Fakes.TREASURE_TOKEN_NAME)

        robot.tapEntry(Fakes.TREASURE_TOKEN_NAME)

        robot.awaitText(robot.str(R.string.entry_tapped))
    }
}

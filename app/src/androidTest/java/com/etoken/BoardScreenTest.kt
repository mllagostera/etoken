package com.etoken

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The battlefield, driven the way a player drives it: through the "+", the
 * picker and the cells.
 *
 * The rules underneath are unit-tested; what these confirm is that the screen
 * is wired to them — that a press of "add" makes one entry and not a merge,
 * that a tap turns a card, and that the buttons in the bar reach the right
 * edits.
 */
@RunWith(AndroidJUnit4::class)
class BoardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val robot by lazy { BoardRobot(compose) }

    @Test
    fun a_deck_opens_onto_an_empty_table_that_says_how_to_fill_it() {
        robot.show()

        compose.onNodeWithText(robot.str(R.string.board_empty)).assertIsDisplayed()
        compose.onNodeWithText(robot.str(R.string.board_empty_detail)).assertIsDisplayed()
        compose.onNodeWithContentDescription(robot.str(R.string.board_add_token))
            .assertIsDisplayed()
    }

    @Test
    fun the_picker_offers_what_the_deck_can_create() {
        robot.show()

        robot.openPicker()

        // The first row of the picker's grid: the deck's other token is a row
        // down, and lazy grids do not compose what is below the fold.
        compose.onNodeWithText(Fakes.TREASURE_TOKEN_NAME).assertIsDisplayed()
        compose.onNodeWithText(Fakes.TOKEN_NAME).assertIsDisplayed()
    }

    @Test
    fun adding_puts_an_entry_on_the_table_summoning_sick() {
        robot.show()

        robot.add(Fakes.TOKEN_NAME, quantity = 2)

        robot.awaitText("×2")
        compose.onNodeWithText(robot.plural(R.plurals.board_in_play_count, 2)).assertIsDisplayed()
        compose.onNodeWithText(robot.str(R.string.entry_sick)).assertIsDisplayed()
    }

    @Test
    fun each_press_of_add_is_its_own_entry() {
        robot.show()

        robot.add(Fakes.TOKEN_NAME, quantity = 2)
        robot.awaitText("×2")
        robot.add(Fakes.TOKEN_NAME, quantity = 2)

        // Four Goblins in two cells, not one cell of four: this is the whole
        // point of the redesign, and the old board would have merged them.
        robot.awaitText(robot.plural(R.plurals.board_in_play_count, 4))
        robot.awaitCount("×2", 2)
        compose.onAllNodesWithText(Fakes.TOKEN_NAME).assertCountEquals(2)
    }

    @Test
    fun a_tap_turns_an_entry_and_another_turns_it_back() {
        robot.show()
        robot.add(Fakes.TOKEN_NAME)
        robot.awaitText(robot.str(R.string.entry_sick))

        robot.tapEntry(Fakes.TOKEN_NAME)

        robot.awaitText(robot.str(R.string.entry_tapped))

        robot.tapEntry(Fakes.TOKEN_NAME)

        robot.awaitGone(robot.str(R.string.entry_tapped))
    }

    @Test
    fun the_untap_step_clears_sickness_and_untaps_the_table() {
        robot.show()
        robot.add(Fakes.TOKEN_NAME, quantity = 3)
        robot.awaitText(robot.str(R.string.entry_sick))
        robot.tapEntry(Fakes.TOKEN_NAME)
        robot.answerAll(3)
        robot.awaitText(robot.str(R.string.entry_tapped))

        compose.onNodeWithText(robot.str(R.string.board_begin_turn)).performClick()

        robot.awaitGone(robot.str(R.string.entry_sick))
        compose.onAllNodesWithText(robot.str(R.string.entry_tapped)).assertCountEquals(0)
        // Nothing left the table on the way: the three are still one entry.
        compose.onNodeWithText("×3").assertIsDisplayed()
    }

    @Test
    fun tapping_some_of_an_entry_leaves_the_rest_able_to_attack() {
        robot.show()
        robot.add(Fakes.TOKEN_NAME, quantity = 6)
        robot.awaitText("×6")

        // Six Goblins, two tapped for mana: the table now holds two things.
        robot.tapEntry(Fakes.TOKEN_NAME)
        robot.answerSome(2)

        robot.awaitCount(Fakes.TOKEN_NAME, 2)
        compose.onNodeWithText("×4").assertIsDisplayed()
        compose.onNodeWithText("×2").assertIsDisplayed()
        compose.onAllNodesWithText(robot.str(R.string.entry_tapped)).assertCountEquals(1)
    }

    @Test
    fun the_untap_step_joins_the_table_back_up() {
        // The case the redesign has to get right both ways round: two tapped,
        // two ready and two summoning sick are three cells while those states
        // differ, and one cell of six the moment a turn resets them.
        robot.show()
        robot.add(Fakes.TOKEN_NAME, quantity = 2, tapped = true)
        robot.awaitText(robot.str(R.string.entry_tapped))
        robot.add(Fakes.HASTE_TOKEN_NAME, quantity = 2, scroll = true)
        robot.add(Fakes.TOKEN_NAME, quantity = 2)
        robot.awaitText(robot.plural(R.plurals.board_in_play_count, 6))

        compose.onNodeWithText(robot.str(R.string.board_begin_turn)).performClick()

        // The Goblins join up; the Hellions are a different token and stay
        // their own cell.
        robot.awaitCount(Fakes.TOKEN_NAME, 1)
        compose.onNodeWithText("×4").assertIsDisplayed()
        compose.onNodeWithText(Fakes.HASTE_TOKEN_NAME).assertIsDisplayed()
        compose.onAllNodesWithText(robot.str(R.string.entry_tapped)).assertCountEquals(0)
        compose.onAllNodesWithText(robot.str(R.string.entry_sick)).assertCountEquals(0)
    }

    @Test
    fun a_counter_on_everything_grows_every_entry() {
        robot.show()
        robot.add(Fakes.TOKEN_NAME, quantity = 2)
        robot.awaitText("×2")

        compose.onNodeWithText(robot.str(R.string.board_counter_all)).performClick()

        // Base 1/1, one +1/+1 counter, so the cell says 2/2 under the art.
        robot.awaitText("2/2")
        compose.onNodeWithText(robot.str(R.string.entry_counters_chip, 1)).assertIsDisplayed()
    }

    @Test
    fun a_long_press_opens_the_entry_and_it_can_be_taken_off_the_table() {
        robot.show()
        robot.add(Fakes.TOKEN_NAME, quantity = 2)
        robot.awaitText("×2")

        robot.openEntry(Fakes.TOKEN_NAME)
        compose.onNodeWithText(robot.str(R.string.entry_counters)).assertIsDisplayed()

        compose.onNodeWithText(robot.str(R.string.entry_remove)).performClick()

        robot.awaitText(robot.str(R.string.board_empty))
    }

    @Test
    fun the_sickness_chip_asks_the_same_question_the_cell_does() {
        robot.show()
        robot.add(Fakes.TOKEN_NAME, quantity = 4)
        robot.awaitText("×4")

        robot.openEntry(Fakes.TOKEN_NAME)
        // The chip in the sheet, not the badge on the cell behind it: both
        // draw the same word, which is the point of them sharing a string.
        robot.inSheet(robot.str(R.string.entry_sick)).performClick()
        // All four are waiting, so the question is about the other direction.
        robot.awaitText(robot.str(R.string.entry_ready_how_many))
        robot.answerSome(1)

        // One can attack, three are still waiting, and they are two entries.
        // Read off the board rather than the grid: the detail sheet is still
        // up and covering the cells it just split.
        robot.awaitEntries(2)
        val entries = robot.boards.board.value.entries
        assertEquals(listOf(3, 1), entries.map { it.quantity })
        assertEquals(1, entries.count { !it.summoningSick })
    }

    @Test
    fun undo_brings_back_a_table_that_a_new_game_emptied() {
        robot.show()
        robot.add(Fakes.TOKEN_NAME, quantity = 5)
        robot.awaitText("×5")

        compose.onNodeWithContentDescription(robot.str(R.string.new_game)).performClick()
        robot.awaitText(robot.str(R.string.new_game_title))
        compose.onNodeWithText(robot.str(R.string.action_start)).performClick()
        robot.awaitText(robot.str(R.string.board_empty))

        compose.onNodeWithContentDescription(robot.str(R.string.action_undo)).performClick()

        // The five are back, still one entry and still summoning sick.
        robot.awaitText("×5")
        compose.onNodeWithText(robot.str(R.string.entry_sick)).assertIsDisplayed()
    }
}

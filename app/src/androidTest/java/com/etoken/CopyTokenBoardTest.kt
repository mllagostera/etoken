package com.etoken

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A token named "Copy" says nothing on its own, so the dialog that adds one
 * also asks what it is a copy of, and will not close until it is told.
 *
 * The answer belongs to the entry, which is what these guard: a copy of Krenko
 * and a copy of Atraxa are two things on the battlefield, and so — now — are
 * two copies of Krenko made at different moments.
 */
@RunWith(AndroidJUnit4::class)
class CopyTokenBoardTest {

    @get:Rule
    val compose = createComposeRule()

    private val robot by lazy { BoardRobot(compose) }

    private fun show() = robot.show(Fakes.OTHER_DECK_ID, Fakes.OTHER_DECK_NAME)

    @Test
    fun a_copy_with_nothing_named_cannot_be_added() {
        show()

        robot.openPicker()
        compose.onNodeWithText(Fakes.COPY_TOKEN_NAME).assertIsDisplayed()
        robot.pick(Fakes.COPY_TOKEN_NAME)

        robot.inDialog(robot.str(R.string.board_add)).assertIsNotEnabled()
    }

    @Test
    fun the_name_given_is_the_one_the_cell_shows() {
        show()

        robot.add(Fakes.COPY_TOKEN_NAME, quantity = 2, copying = "Krenko, Mob Boss")

        robot.awaitText("Krenko, Mob Boss")
        compose.onNodeWithText("×2").assertIsDisplayed()
    }

    @Test
    fun copies_of_different_creatures_are_different_entries() {
        show()

        robot.add(Fakes.COPY_TOKEN_NAME, quantity = 2, copying = "Krenko, Mob Boss")
        robot.awaitText("Krenko, Mob Boss")
        robot.add(Fakes.COPY_TOKEN_NAME, copying = "Atraxa")

        // The count rather than the name: the deck is called "Atraxa
        // Superfriends", so waiting on "Atraxa" is satisfied by the title bar.
        robot.awaitText(robot.plural(R.plurals.board_in_play_count, 3))
        compose.onNodeWithText("Krenko, Mob Boss").assertIsDisplayed()
        compose.onNodeWithText("Atraxa").assertIsDisplayed()
    }

    @Test
    fun two_copies_of_the_same_creature_are_still_two_entries() {
        show()

        robot.add(Fakes.COPY_TOKEN_NAME, quantity = 2, copying = "Krenko, Mob Boss")
        robot.awaitText("Krenko, Mob Boss")
        robot.add(Fakes.COPY_TOKEN_NAME, copying = "Krenko, Mob Boss")

        // Three tokens across two cells. The old board merged these, which is
        // exactly what it was asked to stop doing.
        robot.awaitText(robot.plural(R.plurals.board_in_play_count, 3))
        compose.onAllNodesWithText("Krenko, Mob Boss").assertCountEquals(2)
    }
}

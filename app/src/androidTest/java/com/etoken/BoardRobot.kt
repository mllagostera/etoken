package com.etoken

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.platform.app.InstrumentationRegistry
import com.etoken.data.GameBoardStore
import com.etoken.ui.board.BoardScreen
import com.etoken.ui.board.BoardViewModel
import com.etoken.ui.theme.EtokenTheme

/**
 * The gestures the battlefield screen is driven by, in one place.
 *
 * Adding a token is four taps across a sheet and a dialog now, and every board
 * test starts with it; spelling that out in each of them would bury what each
 * test is actually about. The screen and the view model are the real ones —
 * only the two APIs are faked — so what these steps exercise is the app.
 */
class BoardRobot(private val compose: ComposeContentTestRule) {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Shared with the screen, so a test can also read or seed the table directly. */
    val boards = GameBoardStore()

    fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    fun plural(id: Int, count: Int): String =
        context.resources.getQuantityString(id, count, count)

    fun show(publicId: String = Fakes.DECK_ID, deckName: String = Fakes.DECK_NAME) {
        val viewModel = BoardViewModel(
            repository = Fakes.repository(),
            boards = boards,
            publicId = publicId,
            deckName = deckName,
        )
        compose.setContent {
            EtokenTheme { BoardScreen(onBack = {}, viewModel = viewModel) }
        }
        // The button that opens the picker only exists once the deck's tokens
        // have arrived, so it is the screen saying it is ready.
        awaitText(str(R.string.board_add_token))
    }

    fun openPicker() {
        compose.onNodeWithText(str(R.string.board_add_token)).performClick()
        awaitText(str(R.string.board_picker_title))
    }

    /**
     * Adds one entry of [quantity] copies of [tokenName], through the picker
     * and the dialog, exactly as a player would.
     *
     * [copying] is only for a Copy token, which will not let the dialog close
     * until it is told what it is a copy of.
     */
    fun add(
        tokenName: String,
        quantity: Int = 1,
        tapped: Boolean = false,
        copying: String? = null,
        scroll: Boolean = false,
    ) {
        openPicker()
        pick(tokenName, scroll)

        // The field rather than the chips: it takes any amount, and the chips
        // are only a shortcut to the same value.
        amountField().performTextReplacement(quantity.toString())
        if (copying != null) copyField().performTextReplacement(copying)
        if (tapped) inDialog(str(R.string.board_enter_tapped)).performClick()

        inDialog(str(R.string.board_add)).performClick()
        // The dialog leaving is the entry having been made; the sheet goes with it.
        awaitGone(str(R.string.dialog_how_many))
    }

    /** Opens the add dialog for one of the picker's tokens. The picker must be up. */
    fun pick(tokenName: String, scroll: Boolean = false) {
        if (scroll) scrollPickerTo(tokenName)
        pickerCell(tokenName).performClick()
        awaitText(str(R.string.dialog_how_many))
    }

    /** Turns the first entry of that token, which is what a tap on its cell does. */
    fun tapEntry(tokenName: String) {
        entryCell(tokenName).performClick()
    }

    /** Opens that entry's detail sheet, which is what a long press does. */
    fun openEntry(tokenName: String) {
        entryCell(tokenName).performTouchInput { longClick() }
        awaitText(str(R.string.entry_quantity))
    }

    /**
     * A cell in the picker rather than one on the table behind it.
     *
     * Both draw the token's name, so with the sheet up there can be two nodes
     * carrying it. The sheet is composed after the table, so the picker's is
     * the last of them.
     */
    private fun pickerCell(tokenName: String): SemanticsNodeInteraction {
        val matches = compose.onAllNodesWithText(tokenName).fetchSemanticsNodes().size
        return compose.onAllNodesWithText(tokenName)[matches - 1]
    }

    /** The first cell on the table for that token; the sheet is shut by then. */
    private fun entryCell(tokenName: String) = compose.onAllNodesWithText(tokenName)[0]

    fun inDialog(text: String) =
        compose.onAllNodesWithText(text).filterToOne(hasAnyAncestor(isDialog()))

    fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    fun awaitGone(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty()
    }

    fun awaitCount(text: String, count: Int) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size == count
    }

    /**
     * Brings a token's cell into view, and into existence.
     *
     * The picker's grid is lazy, and CI's emulator is the default AVD rather
     * than a phone: 320x640dp, which `GridCells.Adaptive(150.dp)` answers with
     * two columns. The first row is on screen; anything below it is not merely
     * off screen, it is never composed, so it is absent from the semantics tree
     * and even `assertExists` fails on it.
     *
     * The picker is the last scrollable in the tree while its sheet is up: the
     * table underneath is composed first, and the sheet after it.
     */
    private fun scrollPickerTo(tokenName: String) {
        val scrollables = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        compose.onAllNodes(hasScrollAction())[scrollables - 1]
            .performScrollToNode(hasText(tokenName))
    }

    /** The dialog's first text field: how many copies to make. */
    private fun amountField() =
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[0]

    /** The second one, which only a Copy token has: what it is a copy of. */
    private fun copyField() =
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[1]

    private companion object {
        const val TIMEOUT = 5_000L
    }
}

package app.rive

import android.view.KeyEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * Creates a mocked key event.
 *
 * @param keyCode The key.
 * @param action The key action.
 * @param shift Whether Shift is held.
 */
private fun keyEvent(
    keyCode: Int = KeyEvent.KEYCODE_TAB,
    action: Int = KeyEvent.ACTION_DOWN,
    shift: Boolean = false,
): KeyEvent = mockk<KeyEvent>().also { event ->
    every { event.keyCode } returns keyCode
    every { event.action } returns action
    every { event.isShiftPressed } returns shift
}

class RiveFocusDirectionUnitTest : FunSpec({
    context("traversalDirection") {
        test("Tab resolves to Next") {
            keyEvent().traversalDirection() shouldBe RiveFocusDirection.Next
        }

        test("Shift+Tab resolves to Previous") {
            keyEvent(shift = true).traversalDirection() shouldBe RiveFocusDirection.Previous
        }

        test("A Tab release is not a traversal") {
            keyEvent(action = KeyEvent.ACTION_UP).traversalDirection() shouldBe null
        }

        test("Other keys are not a traversal") {
            keyEvent(keyCode = KeyEvent.KEYCODE_ENTER).traversalDirection() shouldBe null
        }
    }
})

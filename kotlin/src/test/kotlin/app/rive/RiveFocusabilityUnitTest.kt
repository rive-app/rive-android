package app.rive

import android.view.View
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder

/**
 * Creates a mocked view.
 *
 * @param focused Whether the view holds focus.
 * @param inTouchMode Whether the view's window is in touch mode.
 */
private fun view(focused: Boolean = false, inTouchMode: Boolean = false): View =
    mockk<View>(relaxed = true).also { view ->
        every { view.isFocused } returns focused
        every { view.isInTouchMode } returns inTouchMode
    }

class RiveFocusabilityUnitTest : FunSpec({
    context("applyTo") {
        test("Sets focusable in touch mode before focusable") {
            RiveFocusability.entries.forAll { focusability ->
                val view = view()

                focusability.applyTo(view)

                // Setting focusable first would offer focus before touch mode allows it.
                verifyOrder {
                    view.isFocusableInTouchMode = focusability.isFocusableInTouchMode
                    view.isFocusable = focusability.isFocusable
                }
            }
        }

        test("Clears focus held in touch mode once the view can't receive it there") {
            val view = view(focused = true, inTouchMode = true)

            RiveFocusability.Keyboard.applyTo(view)

            verify { view.clearFocus() }
        }

        test("Keeps focus held in keyboard input mode") {
            val view = view(focused = true, inTouchMode = false)

            RiveFocusability.Keyboard.applyTo(view)

            verify(exactly = 0) { view.clearFocus() }
        }

        test("Keeps focus held in touch mode while the view can still receive it there") {
            val view = view(focused = true, inTouchMode = true)

            RiveFocusability.KeyboardAndTouch.applyTo(view)

            verify(exactly = 0) { view.clearFocus() }
        }
    }
})

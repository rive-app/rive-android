package app.rive

import android.view.KeyEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * Creates a mocked key event.
 *
 * @param keyCode The Android key code.
 * @param action The key action.
 * @param repeatCount How many times the key has repeated.
 * @param shift Whether Shift is held.
 * @param ctrl Whether Ctrl is held.
 * @param alt Whether Alt is held.
 * @param meta Whether Meta is held.
 * @param numLock Whether NumLock is on. Like other lock keys, it doesn't count as a modifier.
 */
private fun keyEvent(
    keyCode: Int,
    action: Int = KeyEvent.ACTION_DOWN,
    repeatCount: Int = 0,
    shift: Boolean = false,
    ctrl: Boolean = false,
    alt: Boolean = false,
    meta: Boolean = false,
    numLock: Boolean = true,
): KeyEvent = mockk<KeyEvent>().also { event ->
    val metaState = (if (shift) KeyEvent.META_SHIFT_ON else 0) or
        (if (ctrl) KeyEvent.META_CTRL_ON else 0) or
        (if (alt) KeyEvent.META_ALT_ON else 0) or
        (if (meta) KeyEvent.META_META_ON else 0)
    every { event.keyCode } returns keyCode
    every { event.action } returns action
    every { event.repeatCount } returns repeatCount
    every { event.isShiftPressed } returns shift
    every { event.isCtrlPressed } returns ctrl
    every { event.isAltPressed } returns alt
    every { event.isMetaPressed } returns meta
    every { event.isNumLockOn } returns numLock
    every { event.hasNoModifiers() } returns (metaState == 0)
    every { event.hasModifiers(any()) } answers { firstArg<Int>() == metaState }
}

class RiveKeyPressUnitTest : FunSpec({
    context("from") {
        test("Maps each end of the letter, digit, function, and numpad key ranges") {
            listOf(
                KeyEvent.KEYCODE_A to 65,
                KeyEvent.KEYCODE_Z to 90,
                KeyEvent.KEYCODE_0 to 48,
                KeyEvent.KEYCODE_9 to 57,
                KeyEvent.KEYCODE_F1 to 290,
                KeyEvent.KEYCODE_F12 to 301,
                KeyEvent.KEYCODE_NUMPAD_0 to 320,
                KeyEvent.KEYCODE_NUMPAD_9 to 329,
            ).forAll { (keyCode, key) ->
                RiveKeyPress.from(keyEvent(keyCode))?.key shouldBe key
            }
        }

        test("Maps the D-pad center button to Enter, as TV remotes select with it") {
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_DPAD_CENTER)).shouldNotBeNull().key shouldBe
                RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_ENTER)).shouldNotBeNull().key
        }

        test("Returns null for keys core has no code for") {
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_BACK)).shouldBeNull()
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_VOLUME_UP)).shouldBeNull()
        }

        test("Leaves numpad digit and decimal point presses to Android while NumLock is off") {
            // With NumLock off, Android turns them into arrow and editing keys, but only when the
            // app doesn't consume them.
            listOf(
                KeyEvent.KEYCODE_NUMPAD_0,
                KeyEvent.KEYCODE_NUMPAD_9,
                KeyEvent.KEYCODE_NUMPAD_DOT,
            ).forAll { keyCode ->
                RiveKeyPress.from(keyEvent(keyCode)).shouldNotBeNull()
                RiveKeyPress.from(keyEvent(keyCode, numLock = false)).shouldBeNull()
            }
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_NUMPAD_ENTER, numLock = false))
                .shouldNotBeNull()
        }

        test("Maps a numpad release whatever the NumLock state, so it can follow its press") {
            // NumLock can change while the key is held.
            RiveKeyPress.from(
                keyEvent(KeyEvent.KEYCODE_NUMPAD_6, action = KeyEvent.ACTION_UP, numLock = false)
            ).shouldNotBeNull()
        }

        test("Reports press, release, and repeat") {
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A))?.isPressed shouldBe true
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A, KeyEvent.ACTION_UP))?.isPressed shouldBe
                false
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A))?.isRepeat shouldBe false
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A, repeatCount = 2))?.isRepeat shouldBe true
        }

        test("Packs modifiers as core KeyModifiers bits") {
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A, shift = true))?.modifiers shouldBe 1
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A, ctrl = true))?.modifiers shouldBe 2
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A, alt = true))?.modifiers shouldBe 4
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A, meta = true))?.modifiers shouldBe 8
        }
    }

    context("navigation") {
        test("Plain Tab and Shift+Tab traverse") {
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_TAB))?.navigation shouldBe
                RiveFocusDirection.Next
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_TAB, shift = true))?.navigation shouldBe
                RiveFocusDirection.Previous
        }

        test("Unmodified arrows move spatially") {
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_DPAD_LEFT))?.navigation shouldBe
                RiveFocusDirection.Left
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_DPAD_RIGHT))?.navigation shouldBe
                RiveFocusDirection.Right
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_DPAD_UP))?.navigation shouldBe
                RiveFocusDirection.Up
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_DPAD_DOWN))?.navigation shouldBe
                RiveFocusDirection.Down
        }

        test("Other modifiers turn navigation keys into ordinary keys") {
            // Not null first: an ordinary key still reaches Rive, it just doesn't navigate.
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_TAB, ctrl = true))
                .shouldNotBeNull().navigation.shouldBeNull()
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_TAB, alt = true))
                .shouldNotBeNull().navigation.shouldBeNull()
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_TAB, shift = true, ctrl = true))
                .shouldNotBeNull().navigation.shouldBeNull()
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_DPAD_LEFT, shift = true))
                .shouldNotBeNull().navigation.shouldBeNull()
        }
    }

    context("isChord") {
        test("Ctrl and Meta make a chord, Shift and Alt don't") {
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A, ctrl = true))?.isChord shouldBe true
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A, meta = true))?.isChord shouldBe true
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A, shift = true))?.isChord shouldBe false
            RiveKeyPress.from(keyEvent(KeyEvent.KEYCODE_A, alt = true))?.isChord shouldBe false
        }
    }
})

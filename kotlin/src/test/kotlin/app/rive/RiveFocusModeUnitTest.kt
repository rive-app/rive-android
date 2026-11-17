package app.rive

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

class RiveFocusModeUnitTest : FunSpec({
    test("Off never enables focus") {
        RiveFocusMode.Off.isEnabled(keyboardActive = false) shouldBe false
        RiveFocusMode.Off.isEnabled(keyboardActive = true) shouldBe false
    }

    test("On always enables focus") {
        RiveFocusMode.On.isEnabled(keyboardActive = false) shouldBe true
        RiveFocusMode.On.isEnabled(keyboardActive = true) shouldBe true
    }

    test("Automatic follows the keyboard input mode") {
        RiveFocusMode.Automatic.isEnabled(keyboardActive = false) shouldBe false
        RiveFocusMode.Automatic.isEnabled(keyboardActive = true) shouldBe true
    }

    test("Each mode resolves to its focusability") {
        RiveFocusMode.Off.focusability(hasFocusNodes = true) shouldBe RiveFocusability.None
        RiveFocusMode.On.focusability(hasFocusNodes = true) shouldBe
            RiveFocusability.KeyboardAndTouch
        RiveFocusMode.Automatic.focusability(hasFocusNodes = true) shouldBe
            RiveFocusability.Keyboard
    }

    test("An unanswered focus node query counts as focusable content") {
        RiveFocusMode.On.focusability(hasFocusNodes = null) shouldBe
            RiveFocusability.KeyboardAndTouch
    }

    test("No focusable content resolves to None in every mode") {
        RiveFocusMode.entries.forAll { mode ->
            mode.focusability(hasFocusNodes = false) shouldBe RiveFocusability.None
        }
    }
})

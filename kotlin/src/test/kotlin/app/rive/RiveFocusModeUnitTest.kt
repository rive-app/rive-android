package app.rive

import io.kotest.core.spec.style.FunSpec
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
})

package app.rive.semantics

import app.rive.RiveSemanticsMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class RiveSemanticsModeControllerUnitTest : FunSpec({
    test("Explicit modes resolve without observing Android accessibility") {
        val provider = FakeAccessibilityStateProvider(initialEnabled = true)
        val resolutions = mutableListOf<Boolean>()
        val controller = RiveSemanticsModeController(
            initialMode = RiveSemanticsMode.Off,
            provider = provider,
            onEnabledChanged = resolutions::add,
        )

        provider.listenerCount shouldBe 0
        resolutions.shouldContainExactly(false)

        controller.mode = RiveSemanticsMode.On
        provider.listenerCount shouldBe 0
        resolutions.shouldContainExactly(false, true)

        controller.mode = RiveSemanticsMode.On
        resolutions.shouldContainExactly(false, true)
    }

    test("Automatic mode observes state and stops when an explicit mode is selected") {
        val provider = FakeAccessibilityStateProvider(initialEnabled = false)
        val resolutions = mutableListOf<Boolean>()
        val controller = RiveSemanticsModeController(
            initialMode = RiveSemanticsMode.Automatic,
            provider = provider,
            onEnabledChanged = resolutions::add,
        )

        provider.listenerCount shouldBe 1
        resolutions.shouldContainExactly(false)

        provider.setEnabled(true)
        resolutions.shouldContainExactly(false, true)

        controller.mode = RiveSemanticsMode.Off
        provider.listenerCount shouldBe 0
        resolutions.shouldContainExactly(false, true, false)
    }

    test("Closing automatic mode removes its accessibility listener") {
        val provider = FakeAccessibilityStateProvider(initialEnabled = true)
        val controller = RiveSemanticsModeController(
            initialMode = RiveSemanticsMode.Automatic,
            provider = provider,
            onEnabledChanged = {},
        )

        provider.listenerCount shouldBe 1
        controller.close()
        provider.listenerCount shouldBe 0
    }
})

/** Mutable accessibility state used to test mode resolution without Android system services. */
private class FakeAccessibilityStateProvider(initialEnabled: Boolean) : AccessibilityStateProvider {
    private val listeners = mutableSetOf<AccessibilityEnabledListener>()
    private var enabled = initialEnabled

    /** Number of currently registered listeners. */
    val listenerCount: Int
        get() = listeners.size

    override val isEnabled: Boolean
        get() = enabled

    override fun addAccessibilityStateChangeListener(listener: AccessibilityEnabledListener) {
        listeners.add(listener)
    }

    override fun removeAccessibilityStateChangeListener(listener: AccessibilityEnabledListener) {
        listeners.remove(listener)
    }

    /** Updates accessibility state and notifies current listeners. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        listeners.toList().forEach { listener ->
            listener.onAccessibilityEnabledChanged(enabled)
        }
    }
}

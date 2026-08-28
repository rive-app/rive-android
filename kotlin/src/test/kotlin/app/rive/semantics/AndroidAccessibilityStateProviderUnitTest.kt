package app.rive.semantics

import android.content.Context
import android.view.accessibility.AccessibilityManager
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class AndroidAccessibilityStateProviderUnitTest : FunSpec({
    test("Adding the same listener twice registers one platform adapter") {
        val context = mockk<Context>()
        val accessibilityManager = mockk<AccessibilityManager>()
        val registeredAdapter =
            slot<AccessibilityManager.AccessibilityStateChangeListener>()
        every { context.getSystemService(Context.ACCESSIBILITY_SERVICE) } returns
            accessibilityManager
        every {
            accessibilityManager.addAccessibilityStateChangeListener(capture(registeredAdapter))
        } returns true
        every {
            accessibilityManager.removeAccessibilityStateChangeListener(any())
        } returns true

        val provider = AndroidAccessibilityStateProvider(context)
        val listener = AccessibilityEnabledListener {}

        provider.addAccessibilityStateChangeListener(listener)
        provider.addAccessibilityStateChangeListener(listener)
        provider.removeAccessibilityStateChangeListener(listener)

        verify(exactly = 1) {
            accessibilityManager.addAccessibilityStateChangeListener(any())
        }
        verify(exactly = 1) {
            accessibilityManager.removeAccessibilityStateChangeListener(registeredAdapter.captured)
        }
    }
})

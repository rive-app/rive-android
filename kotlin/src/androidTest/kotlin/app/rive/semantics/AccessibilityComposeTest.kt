package app.rive.semantics

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveSemanticsMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AccessibilityComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun automaticMode_readsInitialProviderState() {
        val provider = FakeAccessibilityStateProvider(initialEnabled = true)
        var observed = false

        composeRule.setContent {
            observed = rememberRiveSemanticsEnabled(RiveSemanticsMode.Automatic, provider)
        }

        composeRule.runOnIdle {
            assertTrue(observed)
            assertEquals(1, provider.listenerCount)
        }
    }

    @Test
    fun automaticMode_updatesWhenProviderChanges() {
        val provider = FakeAccessibilityStateProvider(initialEnabled = false)
        var observed = true

        composeRule.setContent {
            observed = rememberRiveSemanticsEnabled(RiveSemanticsMode.Automatic, provider)
        }

        composeRule.runOnIdle {
            assertFalse(observed)
            provider.setEnabled(true)
        }

        composeRule.runOnIdle {
            assertTrue(observed)
        }
    }

    @Test
    fun automaticMode_removesListenerWhenDisposed() {
        val provider = FakeAccessibilityStateProvider(initialEnabled = false)
        val showObserver = mutableStateOf(true)

        composeRule.setContent {
            if (showObserver.value) {
                rememberRiveSemanticsEnabled(RiveSemanticsMode.Automatic, provider)
            }
        }

        composeRule.runOnIdle {
            assertEquals(1, provider.listenerCount)
            showObserver.value = false
        }

        composeRule.runOnIdle {
            assertEquals(0, provider.listenerCount)
        }
    }

    @Test
    fun explicitModes_doNotObserveProvider() {
        val provider = FakeAccessibilityStateProvider(initialEnabled = true)
        val mode = mutableStateOf(RiveSemanticsMode.Off)
        var observed = true

        composeRule.setContent {
            observed = rememberRiveSemanticsEnabled(mode.value, provider)
        }

        composeRule.runOnIdle {
            assertFalse(observed)
            assertEquals(0, provider.listenerCount)
            mode.value = RiveSemanticsMode.On
        }

        composeRule.runOnIdle {
            assertTrue(observed)
            assertEquals(0, provider.listenerCount)
        }
    }

    @Test
    fun changingFromAutomatic_removesListenerAndAppliesExplicitMode() {
        val provider = FakeAccessibilityStateProvider(initialEnabled = true)
        val mode = mutableStateOf(RiveSemanticsMode.Automatic)
        var observed = false

        composeRule.setContent {
            observed = rememberRiveSemanticsEnabled(mode.value, provider)
        }

        composeRule.runOnIdle {
            assertTrue(observed)
            assertEquals(1, provider.listenerCount)
            mode.value = RiveSemanticsMode.Off
        }

        composeRule.runOnIdle {
            assertFalse(observed)
            assertEquals(0, provider.listenerCount)
        }
    }
}

private class FakeAccessibilityStateProvider(
    initialEnabled: Boolean
) : AccessibilityStateProvider {
    private val listeners = mutableSetOf<AccessibilityEnabledListener>()
    private var enabled = initialEnabled

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

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        listeners.forEach { listener ->
            listener.onAccessibilityEnabledChanged(enabled)
        }
    }
}

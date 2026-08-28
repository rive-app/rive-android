package app.rive.semantics

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.rive.RiveSemanticsMode

/**
 * Resolves whether Rive-authored accessibility semantics should currently be exposed.
 *
 * @param mode The public semantics policy to resolve.
 * @return true when semantics should currently be exposed, false otherwise.
 */
@Composable
internal fun rememberRiveSemanticsEnabled(mode: RiveSemanticsMode): Boolean = when (mode) {
    RiveSemanticsMode.Off -> false
    RiveSemanticsMode.On -> true
    RiveSemanticsMode.Automatic -> rememberAccessibilityEnabled()
}

/**
 * Resolves whether Rive-authored accessibility semantics should currently be exposed using an
 * injected accessibility-state provider.
 *
 * @param mode The semantics policy to resolve.
 * @param provider Source of accessibility state used by [RiveSemanticsMode.Automatic].
 * @return true when semantics should currently be exposed, false otherwise.
 */
@Composable
internal fun rememberRiveSemanticsEnabled(
    mode: RiveSemanticsMode,
    provider: AccessibilityStateProvider,
): Boolean = when (mode) {
    RiveSemanticsMode.Off -> false
    RiveSemanticsMode.On -> true
    RiveSemanticsMode.Automatic -> rememberAccessibilityEnabled(provider)
}

/**
 * Remembers whether Android accessibility is currently enabled.
 *
 * This observes [AccessibilityManager.isEnabled], which indicates that at least one accessibility
 * service is enabled for the device.
 *
 * @return true when Android accessibility is enabled, false otherwise.
 */
@Composable
private fun rememberAccessibilityEnabled(): Boolean {
    val context = LocalContext.current.applicationContext
    val provider = remember(context) {
        AndroidAccessibilityStateProvider(context)
    }
    return rememberAccessibilityEnabled(provider)
}

/**
 * Remember accessibility state from an injected provider.
 *
 * This exists so tests can validate listener registration and state updates without depending on
 * the device's real accessibility settings.
 *
 * @param provider Source of accessibility state and state-change callbacks.
 * @return true when the provider reports accessibility is enabled, false otherwise.
 */
@Composable
internal fun rememberAccessibilityEnabled(provider: AccessibilityStateProvider): Boolean {
    var enabled by remember(provider) { mutableStateOf(provider.isEnabled) }

    DisposableEffect(provider) {
        val listener = AccessibilityEnabledListener { nextEnabled ->
            enabled = nextEnabled
        }
        provider.addAccessibilityStateChangeListener(listener)
        // Refresh after listener registration in case the state changed between composition and
        // effect installation.
        enabled = provider.isEnabled

        onDispose {
            provider.removeAccessibilityStateChangeListener(listener)
        }
    }

    return enabled
}

/**
 * Listener notified when the platform accessibility enabled state changes.
 */
internal fun interface AccessibilityEnabledListener {
    /**
     * Called when accessibility enabled state changes.
     *
     * @param enabled true when accessibility is enabled, false otherwise.
     */
    fun onAccessibilityEnabledChanged(enabled: Boolean)
}

/**
 * Abstraction over Android accessibility state.
 *
 * Separating this from [AccessibilityManager] keeps semantics-mode resolution testable without
 * changing device accessibility settings.
 */
internal interface AccessibilityStateProvider {
    /**
     * Current accessibility enabled state.
     */
    val isEnabled: Boolean

    /**
     * Register a listener for accessibility state changes.
     *
     * @param listener Listener to notify when [isEnabled] changes.
     */
    fun addAccessibilityStateChangeListener(listener: AccessibilityEnabledListener)

    /**
     * Unregister a previously registered listener.
     *
     * @param listener Listener previously passed to [addAccessibilityStateChangeListener].
     */
    fun removeAccessibilityStateChangeListener(listener: AccessibilityEnabledListener)
}

/**
 * [AccessibilityStateProvider] backed by Android's [AccessibilityManager].
 *
 * @param context Context used to resolve the system accessibility service.
 */
internal class AndroidAccessibilityStateProvider(context: Context) : AccessibilityStateProvider {
    private val accessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val listenerAdapters =
        mutableMapOf<AccessibilityEnabledListener, AccessibilityManager.AccessibilityStateChangeListener>()

    override val isEnabled: Boolean
        get() = accessibilityManager.isEnabled

    override fun addAccessibilityStateChangeListener(listener: AccessibilityEnabledListener) {
        if (listenerAdapters.containsKey(listener)) {
            return
        }
        val adapter = AccessibilityManager.AccessibilityStateChangeListener { enabled ->
            listener.onAccessibilityEnabledChanged(enabled)
        }
        listenerAdapters[listener] = adapter
        accessibilityManager.addAccessibilityStateChangeListener(adapter)
    }

    override fun removeAccessibilityStateChangeListener(listener: AccessibilityEnabledListener) {
        val adapter = listenerAdapters.remove(listener) ?: return
        accessibilityManager.removeAccessibilityStateChangeListener(adapter)
    }
}

/**
 * Resolves a mutable [RiveSemanticsMode] against Android accessibility state.
 *
 * The provider is observed only while [mode] is [RiveSemanticsMode.Automatic]. The callback is
 * invoked once with the initial resolution and thereafter only when that resolved value changes.
 *
 * @param initialMode Initial semantics mode.
 * @param provider Source used to resolve [RiveSemanticsMode.Automatic].
 * @param onEnabledChanged Called when the resolved enabled state changes.
 */
internal class RiveSemanticsModeController(
    initialMode: RiveSemanticsMode,
    private val provider: AccessibilityStateProvider,
    private val onEnabledChanged: (Boolean) -> Unit,
) : AutoCloseable {
    private val listener = AccessibilityEnabledListener(::publishEnabled)
    private var observing = false
    private var resolvedEnabled: Boolean? = null

    /** Current semantics mode. */
    var mode: RiveSemanticsMode = initialMode
        set(value) {
            if (field == value) {
                return
            }
            stopObserving()
            field = value
            applyMode()
        }

    init {
        applyMode()
    }

    /** Stops observing Android accessibility state. */
    override fun close() {
        stopObserving()
    }

    /** Resolves [mode], registering for platform state changes only when necessary. */
    private fun applyMode() {
        when (mode) {
            RiveSemanticsMode.Off -> publishEnabled(false)
            RiveSemanticsMode.On -> publishEnabled(true)
            RiveSemanticsMode.Automatic -> {
                provider.addAccessibilityStateChangeListener(listener)
                observing = true
                // Read after registration so a state change cannot be missed between the two.
                publishEnabled(provider.isEnabled)
            }
        }
    }

    /** Publishes [enabled] only when it differs from the last resolved state. */
    private fun publishEnabled(enabled: Boolean) {
        if (resolvedEnabled == enabled) {
            return
        }
        resolvedEnabled = enabled
        onEnabledChanged(enabled)
    }

    /** Removes the platform listener when [mode] was automatic. */
    private fun stopObserving() {
        if (!observing) {
            return
        }
        provider.removeAccessibilityStateChangeListener(listener)
        observing = false
    }
}

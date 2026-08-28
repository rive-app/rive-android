package app.rive

import android.content.Context
import android.view.MotionEvent
import android.view.TextureView
import androidx.annotation.MainThread
import androidx.core.view.ViewCompat
import app.rive.semantics.AndroidVirtualNodeIdAllocator
import app.rive.semantics.AndroidVirtualNodeIdRegistry
import app.rive.semantics.RiveExploreByTouchHelper
import app.rive.semantics.SemanticAccessibilityFocusTransition
import app.rive.semantics.SemanticActionType
import app.rive.semantics.SemanticTreeModel

/** Texture-backed rendering host with optional Rive virtual accessibility descendants. */
internal class RiveTextureView(context: Context) : TextureView(context) {
    private val semanticsIdAllocator = AndroidVirtualNodeIdAllocator()
    private var semanticsInstallation: SemanticsInstallation? = null
    private var semanticsHelper: RiveExploreByTouchHelper? = null

    /**
     * Installs a virtual accessibility hierarchy backed by [tree].
     *
     * @param tree Main-thread-confined semantic tree to expose.
     * @param onSemanticAction Receives actions accepted by active semantic nodes.
     * @param onAccessibilityFocusChanged Receives atomic accessibility-focus transitions.
     * @param onSemanticFocusRequested Receives focus requests for focus-capable Rive nodes.
     * @param onSemanticFocusCleared Receives requests to clear Rive semantic focus.
     */
    @MainThread
    fun installSemantics(
        tree: SemanticTreeModel,
        onSemanticAction: (Int, SemanticActionType) -> Unit,
        onAccessibilityFocusChanged: (SemanticAccessibilityFocusTransition) -> Unit,
        onSemanticFocusRequested: (Int) -> Unit = {},
        onSemanticFocusCleared: () -> Unit = {},
    ) {
        val installation = SemanticsInstallation(
            tree = tree,
            onSemanticAction = onSemanticAction,
            onAccessibilityFocusChanged = onAccessibilityFocusChanged,
            onSemanticFocusRequested = onSemanticFocusRequested,
            onSemanticFocusCleared = onSemanticFocusCleared,
        )
        semanticsInstallation = installation
        installSemanticsHelper(installation)
    }

    /** Installs a fresh helper generation for [installation]. */
    private fun installSemanticsHelper(installation: SemanticsInstallation) {
        disposeSemanticsHelper()
        val helper = RiveExploreByTouchHelper(
            host = this,
            tree = installation.tree,
            onSemanticAction = installation.onSemanticAction,
            onAccessibilityFocusChanged = installation.onAccessibilityFocusChanged,
            onSemanticFocusRequested = installation.onSemanticFocusRequested,
            onSemanticFocusCleared = installation.onSemanticFocusCleared,
            idRegistry = AndroidVirtualNodeIdRegistry(semanticsIdAllocator),
        )
        semanticsHelper = helper
        ViewCompat.setAccessibilityDelegate(this, helper)
        helper.onInstalled()
        helper.invalidateRoot()
    }

    /**
     * Synchronizes virtual accessibility nodes with the helper's semantic tree.
     *
     * Every changed version conservatively invalidates the complete virtual subtree. More precise
     * structural, semantic, and geometry-only event selection is deferred until its TalkBack
     * behavior is validated.
     *
     * @return `true` when a changed tree version was published, otherwise `false`.
     */
    @MainThread
    fun synchronizeSemantics(): Boolean {
        val helper = semanticsHelper ?: return false
        if (!helper.synchronizeWithTree()) {
            return false
        }
        helper.invalidateRoot()
        return true
    }

    /** Clears virtual accessibility focus and removes the installed semantics delegate. */
    @MainThread
    fun clearSemantics() {
        semanticsInstallation = null
        disposeSemanticsHelper()
    }

    /** Disposes only the active helper while preserving a reattachment configuration. */
    private fun disposeSemanticsHelper() {
        val helper = semanticsHelper ?: return
        helper.dispose()
        // Publish the now-empty registry before removing the delegate so Android retires cached
        // descendants instead of retaining stale nodes from the previous helper generation.
        helper.invalidateRoot()
        semanticsHelper = null
        ViewCompat.setAccessibilityDelegate(this, null)
    }

    /** Forwards a hover event to virtual semantics without enabling keyboard-focus traversal. */
    fun dispatchSemanticHoverEvent(event: MotionEvent): Boolean =
        semanticsHelper?.dispatchHoverEvent(event) == true

    /** Routes framework hover dispatch through the optional virtual semantics helper. */
    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        dispatchSemanticHoverEvent(event) || super.dispatchHoverEvent(event)

    /** Reinstalls configured virtual semantics when this host enters a view hierarchy again. */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (semanticsHelper == null) {
            semanticsInstallation?.let(::installSemanticsHelper)
        }
    }

    /** Disposes the active helper while retaining configuration for a possible reattachment. */
    override fun onDetachedFromWindow() {
        disposeSemanticsHelper()
        super.onDetachedFromWindow()
    }

    /** Callbacks and tree needed to construct a new helper after temporary host detachment. */
    private data class SemanticsInstallation(
        val tree: SemanticTreeModel,
        val onSemanticAction: (Int, SemanticActionType) -> Unit,
        val onAccessibilityFocusChanged: (SemanticAccessibilityFocusTransition) -> Unit,
        val onSemanticFocusRequested: (Int) -> Unit,
        val onSemanticFocusCleared: () -> Unit,
    )
}

package app.rive.semantics

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.MainThread
import androidx.core.view.accessibility.AccessibilityEventCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * Exposes one Rive semantic tree as Android virtual accessibility nodes.
 *
 * The [SemanticTreeModel], [ProjectedSemanticHierarchy], and [AndroidVirtualNodeIdRegistry] are
 * persistent main-thread state; Android accessibility nodes are not. For each provider query,
 * Android supplies a virtual ID, the active registry resolves it to a Rive node ID, and this helper
 * populates a new ephemeral [AccessibilityNodeInfoCompat] from the synchronized hierarchy and tree.
 *
 * A changed tree version rebuilds the hierarchy before the registry is reconciled. Surviving Rive
 * nodes keep their virtual IDs through reorder and reparent operations, while removed mappings are
 * retired. The host then invalidates the virtual root so Android queries the new projection. The
 * provider wrapper rejects retired IDs before they can reach `ExploreByTouchHelper`, including
 * requests retained by the framework from an older tree or helper generation.
 *
 * This helper intentionally does not forward keyboard events or model Android input focus. The
 * authored Rive input-focus graph is separate future work. Accessibility-focus changes are
 * reported through dedicated callbacks so the host can synchronize Rive semantic focus without
 * conflating the two focus systems.
 *
 * @param host View that will host the virtual accessibility descendants.
 * @param tree Main-thread-confined semantic tree to expose.
 * @param onSemanticAction Invoked for supported Rive actions accepted by an active virtual node.
 * @param onAccessibilityFocusChanged Invoked for atomic Android accessibility-focus transitions.
 * @param onSemanticFocusRequested Invoked when a focus-capable Rive node gains accessibility focus.
 * @param onSemanticFocusCleared Invoked when Android accessibility focus leaves Rive focus targets.
 * @param idRegistry Helper-generation registry backed by a host-lifetime monotonic allocator.
 */
@MainThread
internal class RiveExploreByTouchHelper(
    private val host: View,
    private val tree: SemanticTreeModel,
    private val onSemanticAction: (Int, SemanticActionType) -> Unit,
    private val onAccessibilityFocusChanged: (SemanticAccessibilityFocusTransition) -> Unit = {},
    private val onSemanticFocusRequested: (Int) -> Unit = {},
    private val onSemanticFocusCleared: () -> Unit = {},
    private val idRegistry: AndroidVirtualNodeIdRegistry = AndroidVirtualNodeIdRegistry(),
) : ExploreByTouchHelper(host) {
    private var hierarchy = ProjectedSemanticHierarchy.from(tree)
    private var projectedNodeSnapshots = snapshotProjectedNodes(hierarchy)
    private var projectedTreeVersion = tree.version
    private var filteringProvider: AccessibilityNodeProviderCompat? = null
    private var preModalAccessibilityFocusedNodeId: Int? = null
    private var activeModalPaneTitle = modalPaneTitle(hierarchy.activeModalNodeId)
    private var isInstalled = false
    private var isSynchronizingWithTree = false
    private var isPopulatingProjectedSnapshot = false
    private val accessibilityManager =
        host.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    init {
        idRegistry.reconcile(hierarchy)
    }

    /**
     * Activates accessibility behavior that requires this helper to be installed on [host].
     *
     * An initially active modal is announced and receives Android accessibility focus only after
     * the host delegates provider requests to this helper. Repeated calls are ignored.
     */
    fun onInstalled() {
        if (isInstalled) {
            return
        }
        isInstalled = true
        hierarchy.activeModalNodeId?.let {
            announceModalAppeared(it)
            requestAccessibilityFocus(modalFocusTarget(hierarchy))
        }
    }

    /**
     * Rebuilds the projected hierarchy and reconciles virtual IDs after [tree] changes.
     *
     * Modal transitions also move or restore Android accessibility focus and emit window-like pane
     * events. The host remains responsible for invalidating the virtual root after a successful
     * refresh so Android re-queries the complete projection.
     *
     * @return `true` when a new semantic tree version was projected, otherwise `false`.
     */
    fun synchronizeWithTree(): Boolean {
        if (isSynchronizingWithTree || projectedTreeVersion == tree.version) {
            return false
        }

        isSynchronizingWithTree = true
        return try {
            synchronizeChangedTree()
            true
        } finally {
            isSynchronizingWithTree = false
        }
    }

    /** Projects a known-changed tree while [isSynchronizingWithTree] prevents nested refreshes. */
    private fun synchronizeChangedTree() {
        val nextHierarchy = ProjectedSemanticHierarchy.from(tree)
        val nextNodeSnapshots = snapshotProjectedNodes(nextHierarchy)
        val previousModalNodeId = hierarchy.activeModalNodeId
        val nextModalNodeId = nextHierarchy.activeModalNodeId
        val nextModalPaneTitle = modalPaneTitle(nextModalNodeId)
        val focusedVirtualNodeId = getAccessibilityFocusedVirtualViewId()
        val focusedRiveNodeId = idRegistry.riveNodeIdForVirtualNode(focusedVirtualNodeId)
        val modalChanged = previousModalNodeId != nextModalNodeId

        if (previousModalNodeId == null && nextModalNodeId != null) {
            preModalAccessibilityFocusedNodeId = focusedRiveNodeId
        }

        // Move directly while both mappings are active when the modal target already existed in
        // the previous projection. Otherwise focus must be cleared before its ID is retired.
        val nextModalFocusTarget = nextModalNodeId?.let { modalFocusTarget(nextHierarchy) }
        val focusedModalBeforeReconciliation = if (modalChanged && nextModalFocusTarget != null) {
            withProjectedSnapshotPopulation {
                requestAccessibilityFocus(nextModalFocusTarget)
            }
        } else {
            false
        }
        if (
            focusedRiveNodeId != null &&
            !nextHierarchy.contains(focusedRiveNodeId) &&
            !focusedModalBeforeReconciliation
        ) {
            clearAccessibilityFocus()
        }
        if (modalChanged && previousModalNodeId != null) {
            announceModalDisappeared()
        }

        idRegistry.reconcile(nextHierarchy)
        hierarchy = nextHierarchy
        projectedNodeSnapshots = nextNodeSnapshots
        projectedTreeVersion = tree.version
        val paneTitleChanged = activeModalPaneTitle != nextModalPaneTitle
        activeModalPaneTitle = nextModalPaneTitle

        when {
            nextModalNodeId != null && modalChanged -> {
                if (!focusedModalBeforeReconciliation) {
                    requestAccessibilityFocus(nextModalFocusTarget)
                }
                announceModalAppeared(nextModalNodeId)
            }
            previousModalNodeId != null && nextModalNodeId == null -> {
                val nodeToRestore = preModalAccessibilityFocusedNodeId
                preModalAccessibilityFocusedNodeId = null
                if (nodeToRestore != null && hierarchy.contains(nodeToRestore)) {
                    requestAccessibilityFocus(nodeToRestore)
                }
            }
            nextModalNodeId != null && paneTitleChanged -> announceModalTitleChanged(nextModalNodeId)
        }
    }

    /** Refreshes and invalidates stale projected state before serving an accessibility operation. */
    private fun synchronizeBeforeAccessibilityOperation() {
        if (synchronizeWithTree()) {
            invalidateRoot()
        }
    }

    /**
     * Clears active Android accessibility focus and retires every virtual-node mapping.
     *
     * Clearing focus before retiring IDs lets the provider report exactly one transition from the
     * previously focused Rive node to outside the subtree. Subsequent queries through a retained
     * provider reference are rejected because the registry is empty.
     */
    fun dispose() {
        if (isInstalled && hierarchy.activeModalNodeId != null) {
            announceModalDisappeared()
        }
        isInstalled = false
        clearAccessibilityFocus()
        preModalAccessibilityFocusedNodeId = null
        idRegistry.clear()
    }

    /** Returns the first exported modal descendant, falling back to the modal container. */
    private fun modalFocusTarget(projectedHierarchy: ProjectedSemanticHierarchy): Int? {
        val modalNodeId = projectedHierarchy.activeModalNodeId ?: return null
        return projectedHierarchy.activeNodeIds.firstOrNull { it != modalNodeId } ?: modalNodeId
    }

    /** Requests Android accessibility focus for an active projected Rive node. */
    private fun requestAccessibilityFocus(riveNodeId: Int?): Boolean {
        val virtualNodeId = riveNodeId?.let(idRegistry::virtualIdForRiveNode) ?: return false
        return getAccessibilityNodeProvider(host).performAction(
            virtualNodeId,
            AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
            null,
        )
    }

    /** Returns the authored title used to identify an active modal pane to Android. */
    private fun modalPaneTitle(modalNodeId: Int?): String? {
        val modalNode = modalNodeId?.let(tree::nodeById) ?: return null
        return modalNode.label.takeIf(String::isNotEmpty)
            ?: modalNode.value.takeIf(String::isNotEmpty)
            ?: modalNode.hint.takeIf(String::isNotEmpty)
    }

    /** Announces that the active modal pane appeared after its projection became queryable. */
    private fun announceModalAppeared(modalNodeId: Int) {
        sendModalPaneEvent(
            modalNodeId = modalNodeId,
            contentChangeType = AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_APPEARED,
        )
    }

    /** Announces that the previous modal pane no longer owns the exported Rive subtree. */
    private fun announceModalDisappeared() {
        sendModalPaneEvent(
            modalNodeId = hierarchy.activeModalNodeId,
            contentChangeType = AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED,
        )
    }

    /** Announces that the active modal pane's authored title changed. */
    private fun announceModalTitleChanged(modalNodeId: Int) {
        sendModalPaneEvent(
            modalNodeId = modalNodeId,
            contentChangeType = AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_TITLE,
        )
    }

    /** Sends a window-state event identifying one Rive-local modal pane transition. */
    @Suppress("DEPRECATION") // AccessibilityEvent.obtain supports Android versions below API 30.
    private fun sendModalPaneEvent(modalNodeId: Int?, contentChangeType: Int) {
        if (!accessibilityManager.isEnabled) {
            return
        }
        val parent = host.parent ?: return
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            contentChangeTypes = contentChangeType
            className = host.javaClass.name
            packageName = host.context.packageName
            isEnabled = host.isEnabled
            activeModalPaneTitle?.let {
                text.add(it)
                contentDescription = it
            }
            val virtualNodeId = modalNodeId?.let(idRegistry::virtualIdForRiveNode)
            if (virtualNodeId == null) {
                setSource(host)
            } else {
                setSource(host, virtualNodeId)
            }
        }
        parent.requestSendAccessibilityEvent(host, event)
    }

    /** Clears the currently focused virtual node through the standard provider action. */
    private fun clearAccessibilityFocus(): Boolean {
        val focusedVirtualNodeId = getAccessibilityFocusedVirtualViewId()
        if (focusedVirtualNodeId == INVALID_ID) {
            return false
        }
        return withProjectedSnapshotPopulation {
            getAccessibilityNodeProvider(host).performAction(
                focusedVirtualNodeId,
                AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                null,
            )
        }
    }

    /** Captures immutable node references matching [projectedHierarchy]. */
    private fun snapshotProjectedNodes(
        projectedHierarchy: ProjectedSemanticHierarchy,
    ): Map<Int, SemanticNodeData> = buildMap {
        for (riveNodeId in projectedHierarchy.activeNodeIds) {
            tree.nodeById(riveNodeId)?.let { put(riveNodeId, it) }
        }
    }

    /**
     * Serves synchronous framework events from the projection whose IDs and hierarchy remain active.
     *
     * AndroidX populates focus events before the initiating focus action returns. Keeping this scope
     * explicit distinguishes intentional retirement population from an unrelated missing tree node.
     *
     * @param block Focus operation that may synchronously populate nodes from the current projection.
     * @return The result returned by [block].
     */
    private inline fun <T> withProjectedSnapshotPopulation(block: () -> T): T {
        val wasPopulatingProjectedSnapshot = isPopulatingProjectedSnapshot
        isPopulatingProjectedSnapshot = true
        return try {
            block()
        } finally {
            isPopulatingProjectedSnapshot = wasPopulatingProjectedSnapshot
        }
    }

    /** Resolves current node data, falling back to the retained projection during focus events. */
    private fun nodeDataForPopulation(riveNodeId: Int): SemanticNodeData? =
        tree.nodeById(riveNodeId)
            ?: if (isPopulatingProjectedSnapshot) projectedNodeSnapshots[riveNodeId] else null

    /** Returns the active virtual node at the supplied host-local coordinates. */
    override fun getVirtualViewAt(x: Float, y: Float): Int {
        synchronizeBeforeAccessibilityOperation()
        if (x < 0f || y < 0f || x >= host.width || y >= host.height) {
            return INVALID_ID
        }
        val riveNodeId = hierarchy.hitTest(tree, x, y) ?: return INVALID_ID
        return idRegistry.virtualIdForRiveNode(riveNodeId) ?: INVALID_ID
    }

    /** Publishes projected roots in authored traversal order. */
    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        for (riveNodeId in hierarchy.roots) {
            idRegistry.virtualIdForRiveNode(riveNodeId)?.let(virtualViewIds::add)
        }
    }

    /** Populates one active virtual node from the current semantic tree projection. */
    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat,
    ) {
        val riveNodeId = checkNotNull(idRegistry.riveNodeIdForVirtualNode(virtualViewId)) {
            "Cannot populate inactive virtual accessibility node $virtualViewId"
        }
        val nodeData = checkNotNull(nodeDataForPopulation(riveNodeId)) {
            "Cannot populate missing Rive semantic node $riveNodeId"
        }
        val role = SemanticRole.fromValue(nodeData.role)
        val state = mapSemanticNodeState(nodeData.traitFlags, nodeData.stateFlags)
        val content = mapSemanticNodeContent(
            role = role,
            label = nodeData.label,
            value = nodeData.value,
            hint = nodeData.hint,
            state = state,
        )

        check(
            node.applySemanticNodeHierarchy(
                host = host,
                riveNodeId = riveNodeId,
                hierarchy = hierarchy,
                registry = idRegistry,
            )
        ) {
            "Cannot populate inconsistent Rive semantic node $riveNodeId"
        }
        node.applySemanticNodeRole(role)
        node.applySemanticNodeHeading(nodeData.headingLevel)
        node.applySemanticNodeState(state)
        node.applySemanticNodeActions(mapSemanticNodeActions(role, state))
        node.applySemanticNodeContent(content)
        if (riveNodeId == hierarchy.activeModalNodeId) {
            node.paneTitle = activeModalPaneTitle
        }
        val parentData = hierarchy.parentOf(riveNodeId)?.let(::nodeDataForPopulation)
        node.applySemanticNodeBounds(nodeData.toAndroidAccessibilityBounds(parentData))

        // ExploreByTouchHelper requires text or contentDescription even for structural containers.
        if (node.text == null && node.contentDescription == null) {
            node.contentDescription = ""
        }
        // Input focus is intentionally not inferred from the accessibility hierarchy.
        node.isFocusable = false
    }

    /** Dispatches an advertised semantic action for an active virtual node. */
    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?,
    ): Boolean {
        val riveNodeId = idRegistry.riveNodeIdForVirtualNode(virtualViewId) ?: return false
        val nodeData = tree.nodeById(riveNodeId) ?: return false
        val role = SemanticRole.fromValue(nodeData.role)
        val state = mapSemanticNodeState(nodeData.traitFlags, nodeData.stateFlags)
        val semanticAction = mapSemanticNodeActions(role, state).semanticActions.firstOrNull {
            it.toAndroidAccessibilityActionId() == action
        } ?: return false

        onSemanticAction(riveNodeId, semanticAction)
        return true
    }

    /** Returns a provider that filters retired virtual IDs before delegating framework queries. */
    override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProviderCompat {
        filteringProvider?.let { return it }
        return ActiveSemanticNodeProvider(
            delegate = checkNotNull(super.getAccessibilityNodeProvider(host)),
            isActiveVirtualNode = { virtualNodeId ->
                idRegistry.riveNodeIdForVirtualNode(virtualNodeId) != null
            },
            accessibilityFocusedVirtualNodeId = ::getAccessibilityFocusedVirtualViewId,
            inputFocusedVirtualNodeId = ::getKeyboardFocusedVirtualViewId,
            synchronizeBeforeQuery = ::synchronizeBeforeAccessibilityOperation,
            onAccessibilityFocusChanged = { previousVirtualNodeId, currentVirtualNodeId ->
                handleAccessibilityFocusChanged(previousVirtualNodeId, currentVirtualNodeId)
            },
        ).also { filteringProvider = it }
    }

    /** Synchronizes one atomic Android accessibility-focus transition with Rive focus capability. */
    private fun handleAccessibilityFocusChanged(
        previousVirtualNodeId: Int,
        currentVirtualNodeId: Int,
    ) {
        val transition = SemanticAccessibilityFocusTransition(
            previousNodeId = previousVirtualNodeId.toActiveRiveNodeIdOrNull(),
            currentNodeId = currentVirtualNodeId.toActiveRiveNodeIdOrNull(),
        )
        val currentNodeId = transition.currentNodeId
        when {
            currentNodeId?.canRequestSemanticFocus() == true ->
                onSemanticFocusRequested(currentNodeId)
            currentNodeId == null -> onSemanticFocusCleared()
        }
        onAccessibilityFocusChanged(transition)
    }

    /** Returns whether the active projected node carries Rive's authored focus capability. */
    private fun Int.canRequestSemanticFocus(): Boolean {
        val nodeData = tree.nodeById(this) ?: return false
        val role = SemanticRole.fromValue(nodeData.role)
        val state = mapSemanticNodeState(nodeData.traitFlags, nodeData.stateFlags)
        return mapSemanticNodeActions(role, state).canRequestFocus
    }

    /** Maps a focused Android virtual ID to its active Rive node ID. */
    private fun Int.toActiveRiveNodeIdOrNull(): Int? =
        if (this == INVALID_ID) null else idRegistry.riveNodeIdForVirtualNode(this)
}

/** Rive semantic node IDs before and after one atomic Android accessibility-focus action. */
internal data class SemanticAccessibilityFocusTransition(
    /** Previously accessibility-focused Rive node ID, or `null` outside the Rive subtree. */
    val previousNodeId: Int?,
    /** Currently accessibility-focused Rive node ID, or `null` outside the Rive subtree. */
    val currentNodeId: Int?,
)

/**
 * Filters framework queries before delegating them to the provider owned by one helper generation.
 *
 * The delegate creates ephemeral node objects from its helper's current projection. This wrapper
 * therefore validates every requested virtual ID against the same generation's active registry;
 * it never forwards a stale ID and never caches populated node objects. Focus IDs are read from the
 * delegate only while their mappings remain active. [synchronizeBeforeQuery] closes the interval
 * between a source-tree update and its normal asynchronous publication before the registry is read.
 */
private class ActiveSemanticNodeProvider(
    private val delegate: AccessibilityNodeProviderCompat,
    private val isActiveVirtualNode: (Int) -> Boolean,
    private val accessibilityFocusedVirtualNodeId: () -> Int,
    private val inputFocusedVirtualNodeId: () -> Int,
    private val synchronizeBeforeQuery: () -> Unit,
    private val onAccessibilityFocusChanged: (Int, Int) -> Unit,
) : AccessibilityNodeProviderCompat() {
    /** Returns whether a provider operation may query [virtualViewId]. */
    private fun isQueryable(virtualViewId: Int): Boolean {
        synchronizeBeforeQuery()
        return virtualViewId == HOST_VIEW_ID || isActiveVirtualNode(virtualViewId)
    }

    /** Creates an ephemeral node only for the host or an active virtual ID. */
    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfoCompat? =
        if (isQueryable(virtualViewId)) {
            delegate.createAccessibilityNodeInfo(virtualViewId)
        } else {
            null
        }

    /** Delegates text search only when its requested root remains active. */
    override fun findAccessibilityNodeInfosByText(
        text: String,
        virtualViewId: Int,
    ): List<AccessibilityNodeInfoCompat>? = if (isQueryable(virtualViewId)) {
        delegate.findAccessibilityNodeInfosByText(text, virtualViewId)
    } else {
        null
    }

    /** Returns the focused node only while its virtual ID remains active. */
    override fun findFocus(focus: Int): AccessibilityNodeInfoCompat? {
        synchronizeBeforeQuery()
        val focusedVirtualNodeId = when (focus) {
            AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY ->
                accessibilityFocusedVirtualNodeId()
            AccessibilityNodeInfoCompat.FOCUS_INPUT -> inputFocusedVirtualNodeId()
            else -> return delegate.findFocus(focus)
        }
        if (
            focusedVirtualNodeId != ExploreByTouchHelper.INVALID_ID &&
            !isActiveVirtualNode(focusedVirtualNodeId)
        ) {
            return null
        }
        return delegate.findFocus(focus)
    }

    /**
     * Delegates actions and reports one atomic accessibility-focus transition when applicable.
     *
     * Comparing focus around the complete delegated action suppresses the helper's internal clear
     * while moving directly between two virtual descendants.
     */
    override fun performAction(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?,
    ): Boolean {
        if (!isQueryable(virtualViewId)) {
            return false
        }
        val previousVirtualNodeId = accessibilityFocusedVirtualNodeId()
        val handled = delegate.performAction(virtualViewId, action, arguments)
        val currentVirtualNodeId = accessibilityFocusedVirtualNodeId()
        if (
            handled &&
            previousVirtualNodeId != currentVirtualNodeId &&
            action.isAccessibilityFocusAction()
        ) {
            onAccessibilityFocusChanged(previousVirtualNodeId, currentVirtualNodeId)
        }
        return handled
    }

    /** Delegates extra-data requests only for the host or an active virtual ID. */
    override fun addExtraDataToAccessibilityNodeInfo(
        virtualViewId: Int,
        info: AccessibilityNodeInfoCompat,
        extraDataKey: String,
        arguments: Bundle?,
    ) {
        if (isQueryable(virtualViewId)) {
            delegate.addExtraDataToAccessibilityNodeInfo(
                virtualViewId,
                info,
                extraDataKey,
                arguments,
            )
        }
    }

    /** Returns whether this action changes Android accessibility focus. */
    private fun Int.isAccessibilityFocusAction(): Boolean =
        this == AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS ||
            this == AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS
}

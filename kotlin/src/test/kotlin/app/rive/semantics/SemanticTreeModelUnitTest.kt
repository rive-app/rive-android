package app.rive.semantics

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.test.assertFailsWith

private data class ObservedTreeState(
    val version: Int,
    val roots: List<Int>,
    val rootChildren: List<Int>?,
    val childLabel: String?,
    val childMaxX: Float?,
)

private fun node(
    id: Int,
    parentId: Int = -1,
    siblingIndex: Int = 0,
    role: Int = SemanticRole.None.value,
    label: String = "",
    value: String = "",
    hint: String = "",
    stateFlags: Int = 0,
    traitFlags: Int = 0,
    headingLevel: Int = 0,
    minX: Float = 0f,
    minY: Float = 0f,
    maxX: Float = 0f,
    maxY: Float = 0f,
): SemanticsDiffNode = SemanticsDiffNode(
    id = id,
    role = role,
    label = label,
    value = value,
    hint = hint,
    stateFlags = stateFlags,
    traitFlags = traitFlags,
    headingLevel = headingLevel,
    minX = minX,
    minY = minY,
    maxX = maxX,
    maxY = maxY,
    parentId = parentId,
    siblingIndex = siblingIndex
)

private fun diff(
    added: Array<SemanticsDiffNode> = emptyArray(),
    removed: IntArray = intArrayOf(),
    moved: Array<SemanticsDiffNode> = emptyArray(),
    updatedSemantic: Array<SemanticsDiffNode> = emptyArray(),
    updatedGeometry: Array<SemanticsBoundsUpdate> = emptyArray(),
    childrenUpdated: Array<SemanticsChildrenUpdate> = emptyArray(),
): SemanticsDiff = SemanticsDiff(
    treeVersion = 1L,
    frameNumber = 0L,
    rootId = 0,
    removed = removed,
    added = added,
    moved = moved,
    childrenUpdated = childrenUpdated,
    updatedSemantic = updatedSemantic,
    updatedGeometry = updatedGeometry
)

class SemanticTreeModelUnitTest : FunSpec({
    test("Check state field decodes authored and reserved values") {
        SemanticState.checkState(0) shouldBe SemanticCheckState.Unchecked
        SemanticState.checkState(1 shl 2) shouldBe SemanticCheckState.Checked
        SemanticState.checkState(2 shl 2) shouldBe SemanticCheckState.Mixed
        SemanticState.checkState(3 shl 2) shouldBe SemanticCheckState.Mixed

        SemanticState.effectiveChecked(1 shl 2) shouldBe true
        SemanticState.effectiveMixed(1 shl 2) shouldBe false
        SemanticState.effectiveChecked(2 shl 2) shouldBe false
        SemanticState.effectiveMixed(2 shl 2) shouldBe true
    }

    test("Empty diff is a no-op") {
        val model = SemanticTreeModel()
        model.applyDiff(SemanticsDiff.Empty)
        model.version shouldBe 0
        model.nodeCount shouldBe 0
    }

    test("First frame diff builds tree in order") {
        val model = SemanticTreeModel()
        model.applyDiff(
            diff(
                added = arrayOf(
                    node(1, parentId = -1, role = SemanticRole.Group.value),
                    node(2, parentId = 1, siblingIndex = 0, role = SemanticRole.Text.value),
                    node(3, parentId = 1, siblingIndex = 1, role = SemanticRole.Group.value),
                    node(4, parentId = 3, siblingIndex = 0, role = SemanticRole.Text.value)
                ),
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(parentId = -1, childIds = intArrayOf(1)),
                    SemanticsChildrenUpdate(parentId = 1, childIds = intArrayOf(2, 3)),
                    SemanticsChildrenUpdate(parentId = 3, childIds = intArrayOf(4))
                )
            )
        )

        model.nodeCount shouldBe 4
        model.roots shouldBe listOf(1)
        model.nodeById(1)?.children shouldBe listOf(2, 3)
        model.nodeById(3)?.children shouldBe listOf(4)
        model.version shouldBe 1
    }

    test("Published nodes and child collections are immutable snapshots") {
        val model = SemanticTreeModel()
        model.applyDiff(
            diff(
                added = arrayOf(
                    node(1, parentId = -1),
                    node(2, parentId = 1, label = "Before")
                ),
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(parentId = -1, childIds = intArrayOf(1)),
                    SemanticsChildrenUpdate(parentId = 1, childIds = intArrayOf(2))
                )
            )
        )
        val originalRoots = model.roots
        val originalParent = model.nodeById(1)!!
        val originalChild = model.nodeById(2)!!

        assertFailsWith<UnsupportedOperationException> {
            (originalRoots as MutableList<Int>).add(99)
        }
        assertFailsWith<UnsupportedOperationException> {
            (originalParent.children as MutableList<Int>).add(99)
        }

        model.applyDiff(
            diff(
                added = arrayOf(node(3, parentId = 1, siblingIndex = 1)),
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(parentId = 1, childIds = intArrayOf(2, 3))
                ),
                updatedSemantic = arrayOf(node(2, label = "After"))
            )
        )

        originalRoots shouldBe listOf(1)
        originalParent.children shouldBe listOf(2)
        originalChild.label shouldBe "Before"
        model.nodeById(1)?.children shouldBe listOf(2, 3)
        model.nodeById(2)?.label shouldBe "After"
    }

    test("Adding an existing ID updates fields without replacing its children") {
        val model = SemanticTreeModel()
        model.applyDiff(
            diff(
                added = arrayOf(
                    node(1, parentId = -1, label = "Before", maxX = 10f, maxY = 20f),
                    node(2, parentId = 1, label = "Child")
                ),
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(parentId = -1, childIds = intArrayOf(1)),
                    SemanticsChildrenUpdate(parentId = 1, childIds = intArrayOf(2))
                )
            )
        )

        model.applyDiff(
            diff(
                added = arrayOf(
                    node(
                        id = 1,
                        parentId = -1,
                        role = SemanticRole.Button.value,
                        label = "After",
                        value = "Updated",
                        maxX = 30f,
                        maxY = 40f,
                    )
                )
            )
        )

        val updated = model.nodeById(1)!!
        updated.role shouldBe SemanticRole.Button.value
        updated.label shouldBe "After"
        updated.value shouldBe "Updated"
        updated.maxX shouldBe 30f
        updated.maxY shouldBe 40f
        updated.children shouldBe listOf(2)
        model.nodeById(2)?.parentId shouldBe 1
    }

    test("Semantic updates preserve geometry") {
        val model = SemanticTreeModel()
        model.applyDiff(
            diff(
                added = arrayOf(
                    node(
                        id = 1,
                        role = SemanticRole.Text.value,
                        label = "Before",
                        minX = 10f,
                        minY = 20f,
                        maxX = 30f,
                        maxY = 40f,
                    )
                )
            )
        )

        model.applyDiff(
            diff(
                updatedSemantic = arrayOf(
                    node(
                        id = 1,
                        role = SemanticRole.Button.value,
                        label = "After",
                        value = "Value",
                        hint = "Hint",
                        stateFlags = SemanticState.Selected,
                        traitFlags = SemanticTrait.Selectable,
                        headingLevel = 2,
                        minX = 100f,
                        minY = 200f,
                        maxX = 300f,
                        maxY = 400f,
                    )
                )
            )
        )

        val updated = model.nodeById(1)!!
        updated.role shouldBe SemanticRole.Button.value
        updated.label shouldBe "After"
        updated.value shouldBe "Value"
        updated.hint shouldBe "Hint"
        updated.stateFlags shouldBe SemanticState.Selected
        updated.traitFlags shouldBe SemanticTrait.Selectable
        updated.headingLevel shouldBe 2
        updated.minX shouldBe 10f
        updated.minY shouldBe 20f
        updated.maxX shouldBe 30f
        updated.maxY shouldBe 40f
    }

    test("Geometry updates preserve semantic fields") {
        val model = SemanticTreeModel()
        model.applyDiff(
            diff(
                added = arrayOf(
                    node(
                        id = 1,
                        role = SemanticRole.Button.value,
                        label = "Label",
                        value = "Value",
                        hint = "Hint",
                        stateFlags = SemanticState.Selected,
                        traitFlags = SemanticTrait.Selectable,
                        headingLevel = 2,
                    )
                )
            )
        )

        model.applyDiff(
            diff(
                updatedGeometry = arrayOf(
                    SemanticsBoundsUpdate(
                        id = 1,
                        minX = 10f,
                        minY = 20f,
                        maxX = 30f,
                        maxY = 40f,
                    )
                )
            )
        )

        val updated = model.nodeById(1)!!
        updated.minX shouldBe 10f
        updated.minY shouldBe 20f
        updated.maxX shouldBe 30f
        updated.maxY shouldBe 40f
        updated.role shouldBe SemanticRole.Button.value
        updated.label shouldBe "Label"
        updated.value shouldBe "Value"
        updated.hint shouldBe "Hint"
        updated.stateFlags shouldBe SemanticState.Selected
        updated.traitFlags shouldBe SemanticTrait.Selectable
        updated.headingLevel shouldBe 2
    }

    test("Updates for unknown IDs are ignored") {
        val model = SemanticTreeModel()
        model.applyDiff(diff(added = arrayOf(node(1, label = "Known"))))
        val versionBeforeUpdates = model.version

        model.applyDiff(
            diff(
                updatedSemantic = arrayOf(node(99, label = "Unknown")),
                updatedGeometry = arrayOf(
                    SemanticsBoundsUpdate(
                        id = 100,
                        minX = 10f,
                        minY = 20f,
                        maxX = 30f,
                        maxY = 40f,
                    )
                )
            )
        )

        model.nodeCount shouldBe 1
        model.nodeById(1)?.label shouldBe "Known"
        model.nodeById(99) shouldBe null
        model.nodeById(100) shouldBe null
        model.version shouldBe versionBeforeUpdates
    }

    test("Version is emitted after the complete diff is applied") {
        lateinit var observed: ObservedTreeState

        coroutineScope {
            val model = SemanticTreeModel()
            launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                model.versionFlow.drop(1).first()
                observed = ObservedTreeState(
                    version = model.version,
                    roots = model.roots.toList(),
                    rootChildren = model.nodeById(1)?.children?.toList(),
                    childLabel = model.nodeById(2)?.label,
                    childMaxX = model.nodeById(2)?.maxX,
                )
            }

            model.applyDiff(
                diff(
                    added = arrayOf(
                        node(1, parentId = -1, role = SemanticRole.Group.value),
                        node(2, parentId = 1, label = "Before")
                    ),
                    childrenUpdated = arrayOf(
                        SemanticsChildrenUpdate(parentId = -1, childIds = intArrayOf(1)),
                        SemanticsChildrenUpdate(parentId = 1, childIds = intArrayOf(2))
                    ),
                    updatedSemantic = arrayOf(node(2, label = "After")),
                    updatedGeometry = arrayOf(
                        SemanticsBoundsUpdate(
                            id = 2,
                            minX = 10f,
                            minY = 20f,
                            maxX = 30f,
                            maxY = 40f
                        )
                    )
                )
            )
        }

        observed shouldBe ObservedTreeState(
            version = 1,
            roots = listOf(1),
            rootChildren = listOf(2),
            childLabel = "After",
            childMaxX = 30f,
        )
    }

    test("Remove removes subtree") {
        val model = SemanticTreeModel()
        model.applyDiff(
            diff(
                added = arrayOf(
                    node(1, -1),
                    node(2, 1, 0),
                    node(3, 1, 1),
                    node(4, 2, 0),
                    node(5, 2, 1)
                )
            )
        )
        model.nodeCount shouldBe 5

        model.applyDiff(diff(removed = intArrayOf(2)))

        model.nodeCount shouldBe 2
        model.nodeById(2) shouldBe null
        model.nodeById(4) shouldBe null
        model.nodeById(5) shouldBe null
        model.nodeById(1)?.children shouldBe listOf(3)
    }

    test("Moved plus childrenUpdated reparents and reorders") {
        val model = SemanticTreeModel()
        model.applyDiff(
            diff(
                added = arrayOf(
                    node(1, -1),
                    node(2, 1, 0),
                    node(3, 1, 1),
                    node(4, 2, 0)
                ),
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(-1, intArrayOf(1)),
                    SemanticsChildrenUpdate(1, intArrayOf(2, 3)),
                    SemanticsChildrenUpdate(2, intArrayOf(4))
                )
            )
        )

        model.applyDiff(
            diff(
                moved = arrayOf(node(4, parentId = 1, siblingIndex = 0, minX = 1f, maxX = 2f, maxY = 2f)),
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(1, intArrayOf(4, 2, 3)),
                    SemanticsChildrenUpdate(2, intArrayOf())
                )
            )
        )

        model.nodeById(4)?.parentId shouldBe 1
        model.nodeById(1)?.children shouldBe listOf(4, 2, 3)
        model.nodeById(2)?.children shouldBe emptyList()
    }

    test("Children updates authoritatively replace order and filter unknown IDs") {
        val model = SemanticTreeModel()
        model.applyDiff(
            diff(
                added = arrayOf(
                    node(1, parentId = -1),
                    node(2, parentId = 1, siblingIndex = 0),
                    node(3, parentId = 1, siblingIndex = 1),
                )
            )
        )

        model.applyDiff(
            diff(
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(
                        parentId = 1,
                        childIds = intArrayOf(3, 99, 2),
                    )
                )
            )
        )

        model.nodeById(1)?.children shouldBe listOf(3, 2)
        model.nodeById(3)?.parentId shouldBe 1
        model.nodeById(2)?.parentId shouldBe 1
        model.nodeById(99) shouldBe null
    }

    test("Root updates authoritatively replace authored order") {
        val model = SemanticTreeModel()
        model.applyDiff(
            diff(
                added = arrayOf(
                    node(1, parentId = -1, siblingIndex = 0),
                    node(2, parentId = -1, siblingIndex = 1),
                    node(3, parentId = -1, siblingIndex = 2),
                )
            )
        )

        model.applyDiff(
            diff(
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(
                        parentId = -1,
                        childIds = intArrayOf(3, 99, 1, 2),
                    )
                )
            )
        )

        model.roots shouldBe listOf(3, 1, 2)
        model.nodeById(3)?.parentId shouldBe -1
        model.nodeById(1)?.parentId shouldBe -1
        model.nodeById(2)?.parentId shouldBe -1
        model.nodeById(99) shouldBe null
    }

    test("Version increments exactly once for each changing diff") {
        val model = SemanticTreeModel()

        model.applyDiff(
            diff(
                added = arrayOf(
                    node(1, parentId = -1),
                    node(2, parentId = 1, label = "Before"),
                ),
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(parentId = -1, childIds = intArrayOf(1)),
                    SemanticsChildrenUpdate(parentId = 1, childIds = intArrayOf(2)),
                )
            )
        )
        model.version shouldBe 1

        model.applyDiff(
            diff(
                updatedSemantic = arrayOf(node(2, label = "After")),
                updatedGeometry = arrayOf(
                    SemanticsBoundsUpdate(
                        id = 2,
                        minX = 10f,
                        minY = 20f,
                        maxX = 30f,
                        maxY = 40f,
                    )
                )
            )
        )
        model.version shouldBe 2

        model.applyDiff(
            diff(
                updatedSemantic = arrayOf(node(2, label = "After")),
                updatedGeometry = arrayOf(
                    SemanticsBoundsUpdate(
                        id = 2,
                        minX = 10f,
                        minY = 20f,
                        maxX = 30f,
                        maxY = 40f,
                    )
                )
            )
        )
        model.version shouldBe 2
    }

    test("No-op semantic and geometry updates do not bump version") {
        val model = SemanticTreeModel()
        model.applyDiff(
            diff(
                added = arrayOf(node(1, label = "A", role = SemanticRole.Button.value))
            )
        )
        val versionAfterAdd = model.version

        model.applyDiff(
            diff(
                updatedSemantic = arrayOf(node(1, label = "A", role = SemanticRole.Button.value))
            )
        )
        model.version shouldBe versionAfterAdd

        model.applyDiff(
            diff(
                updatedGeometry = arrayOf(
                    SemanticsBoundsUpdate(id = 1, minX = 0f, minY = 0f, maxX = 0f, maxY = 0f)
                )
            )
        )
        model.version shouldBe versionAfterAdd
    }

    test("Full lifecycle remains coherent across add update move and remove") {
        val model = SemanticTreeModel()

        model.applyDiff(
            diff(
                added = arrayOf(
                    node(1, parentId = -1, role = SemanticRole.List.value),
                    node(
                        id = 2,
                        parentId = 1,
                        siblingIndex = 0,
                        role = SemanticRole.ListItem.value,
                        label = "A",
                    ),
                    node(
                        id = 3,
                        parentId = 1,
                        siblingIndex = 1,
                        role = SemanticRole.ListItem.value,
                        label = "B",
                    ),
                )
            )
        )
        model.nodeCount shouldBe 3

        model.applyDiff(
            diff(
                updatedSemantic = arrayOf(
                    node(
                        id = 2,
                        parentId = 1,
                        role = SemanticRole.ListItem.value,
                        label = "A2",
                    )
                )
            )
        )
        model.nodeById(2)?.label shouldBe "A2"

        model.applyDiff(
            diff(
                moved = arrayOf(
                    node(3, parentId = 1, siblingIndex = 0),
                    node(2, parentId = 1, siblingIndex = 1),
                ),
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(parentId = 1, childIds = intArrayOf(3, 2))
                )
            )
        )
        model.nodeById(1)?.children shouldBe listOf(3, 2)

        model.applyDiff(diff(removed = intArrayOf(2)))

        model.nodeCount shouldBe 2
        model.nodeById(2) shouldBe null
        model.nodeById(1)?.children shouldBe listOf(3)
        model.version shouldBe 4
    }
})

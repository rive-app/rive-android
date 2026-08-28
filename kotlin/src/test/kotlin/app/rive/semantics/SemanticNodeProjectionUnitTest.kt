package app.rive.semantics

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SemanticNodeProjectionUnitTest : FunSpec({
    val visibleState = mapSemanticNodeState(traitFlags = 0, stateFlags = 0)
    val emptyContent = semanticContent()

    test("Visible positive-area roles follow their topology") {
        SemanticRole.entries.forEach { role ->
            val expected = when (role.toSemanticNodeTopology()) {
                SemanticNodeTopology.Structural -> SemanticNodeProjection.PromoteChildren
                SemanticNodeTopology.ExplicitContainer -> SemanticNodeProjection.ExportContainer
                SemanticNodeTopology.AbsorbingLeaf -> SemanticNodeProjection.ExportLeaf
            }

            withClue(role) {
                classifyProjection(role, emptyContent, visibleState) shouldBe expected
            }
        }
    }

    test("Structural nodes with any accessible content become containers") {
        listOf(
            semanticContent(label = "Section"),
            semanticContent(value = "Current value"),
            semanticContent(hint = "Additional context")
        ).forEach { content ->
            classifyProjection(SemanticRole.None, content, visibleState) shouldBe
                SemanticNodeProjection.ExportContainer
        }
    }

    test("Hidden state prunes every role and takes precedence over geometry") {
        val hiddenState = mapSemanticNodeState(
            traitFlags = 0,
            stateFlags = SemanticState.Hidden
        )

        SemanticRole.entries.forEach { role ->
            withClue(role) {
                classifyProjection(
                    role = role,
                    content = semanticContent(label = "Accessible"),
                    state = hiddenState,
                    minX = Float.NaN,
                    minY = 0f,
                    maxX = 0f,
                    maxY = 0f
                ) shouldBe SemanticNodeProjection.PruneSubtree
            }
        }
    }

    test("Non-finite and zero-area bounds promote children for every role") {
        val unpublishedBounds = listOf(
            floatArrayOf(Float.NaN, 0f, 10f, 10f),
            floatArrayOf(0f, Float.NEGATIVE_INFINITY, 10f, 10f),
            floatArrayOf(0f, 0f, Float.POSITIVE_INFINITY, 10f),
            floatArrayOf(0f, 0f, 10f, Float.NaN),
            floatArrayOf(5f, 0f, 5f, 10f),
            floatArrayOf(0f, 5f, 10f, 5f)
        )

        SemanticRole.entries.forEach { role ->
            unpublishedBounds.forEach { bounds ->
                withClue("$role with ${bounds.contentToString()}") {
                    classifyProjection(
                        role = role,
                        content = semanticContent(label = "Accessible"),
                        state = visibleState,
                        minX = bounds[0],
                        minY = bounds[1],
                        maxX = bounds[2],
                        maxY = bounds[3]
                    ) shouldBe SemanticNodeProjection.PromoteChildren
                }
            }
        }
    }

    test("Reversed finite bounds retain positive area") {
        classifyProjection(
            role = SemanticRole.Button,
            content = emptyContent,
            state = visibleState,
            minX = 20f,
            minY = 30f,
            maxX = 10f,
            maxY = 5f
        ) shouldBe SemanticNodeProjection.ExportLeaf
    }
})

/** Creates classified general content for projection tests. */
private fun semanticContent(
    label: String = "",
    value: String = "",
    hint: String = "",
): SemanticNodeContent = mapSemanticNodeContent(
    role = SemanticRole.None,
    label = label,
    value = value,
    hint = hint,
    state = mapSemanticNodeState(traitFlags = 0, stateFlags = 0)
)

/** Classifies a node using finite positive-area default bounds. */
private fun classifyProjection(
    role: SemanticRole,
    content: SemanticNodeContent,
    state: SemanticNodeState,
    minX: Float = 0f,
    minY: Float = 0f,
    maxX: Float = 10f,
    maxY: Float = 10f,
): SemanticNodeProjection = classifySemanticNodeProjection(
    role = role,
    content = content,
    state = state,
    minX = minX,
    minY = minY,
    maxX = maxX,
    maxY = maxY
)

package app.rive

/**
 * Describes how size differences are handled between an authored Rive artboard and its containing
 * view.
 */
sealed class Fit {
    /**
     * Default alignment. Not relevant for all fit modes, but still passed when drawing or on
     * pointer events.
     */
    open val alignment: Alignment = Alignment.Center

    /**
     * Default scale factor. Only relevant with [Fit.Layout], but still passed when drawing or on
     * pointer events.
     */
    open val scaleFactor: Float = 1f

    /** The ordinal that maps to the core runtime enum. */
    internal abstract val nativeMapping: Byte

    /**
     * Invokes the Rive layout engine to apply responsive layout to the artboard. This assumes that
     * the artboard was designed with layouts in mind.
     *
     * @param scaleFactor A multiplier to apply to the size of the laid out contents to fine-tune
     *    their presentation. Defaults to 1f (no change).
     */
    data class Layout(override val scaleFactor: Float = 1f) : Fit() {
        override val nativeMapping: Byte = 7
    }

    /**
     * Preserve aspect ratio and scale the artboard so that its larger dimension matches the
     * corresponding dimension of the containing view.
     *
     * If aspect ratios are not identical, this will leave space on the larger axis.
     *
     * @param alignment The alignment of the artboard within the containing view. Defaults to
     *    [Alignment.Center].
     *
     * ℹ️ Only the axis opposite to the larger dimension will be relevant. E.g. when the artboard's
     * width (horizontal axis) is larger, only top, center, or bottom (vertical axis) alignments are
     * relevant.
     */
    data class Contain(override val alignment: Alignment = Alignment.Center) : Fit() {
        override val nativeMapping: Byte = 1
    }

    /**
     * Preserve aspect ratio and behave like [Fit.Contain] when the artboard is larger than the
     * containing view. Otherwise, use the artboard's original dimensions.
     *
     * @param alignment The alignment of the artboard within the containing view. Defaults to
     *    [Alignment.Center].
     *
     * ℹ️ When scaled down, see the note in [Fit.Contain] about the "free" axis.
     */
    data class ScaleDown(override val alignment: Alignment = Alignment.Center) : Fit() {
        override val nativeMapping: Byte = 6
    }

    /**
     * Preserve aspect ratio and scale the artboard so that its smaller dimension matches the
     * corresponding dimension of the containing view.
     *
     * If aspect ratios are not identical, this will clip the artboard on the larger dimension.
     *
     * @param alignment The alignment of the artboard within the containing view. Defaults to
     *    [Alignment.Center].
     *
     * ℹ️ Only the axis opposite to the smaller dimension will be relevant. E.g. when the artboard's
     * width (horizontal axis) is smaller, only top, center, or bottom (vertical axis) alignments
     * are relevant.
     */
    data class Cover(override val alignment: Alignment = Alignment.Center) : Fit() {
        override val nativeMapping: Byte = 2
    }

    /**
     * Preserve aspect ratio and scale the artboard width to match the containing view's width.
     *
     * If the aspect ratios between the artboard and containing view do not match, this will result
     * in either vertical clipping or space in the vertical axis.
     *
     * @param alignment The vertical alignment of the artboard within the containing view. Defaults
     *    to [Alignment.Center].
     */
    data class FitWidth(override val alignment: Alignment = Alignment.Center) : Fit() {
        override val nativeMapping: Byte = 3
    }

    /**
     * Preserve aspect ratio and scale the artboard height to match the containing view's height.
     *
     * If the aspect ratios between the artboard and containing view do not match, this will result
     * in either horizontal clipping or space in the horizontal axis.
     *
     * @param alignment The horizontal alignment of the artboard within the containing view.
     *    Defaults to [Alignment.Center].
     */
    data class FitHeight(override val alignment: Alignment = Alignment.Center) : Fit() {
        override val nativeMapping: Byte = 4

    }

    /** Do not preserve aspect ratio and stretch to the containing view's dimensions. */
    object Fill : Fit() {
        override val nativeMapping: Byte = 0
    }

    /**
     * Do not scale. Use the artboard's original dimensions.
     *
     * For either dimension, if the artboard's dimension is larger, it will be clipped. If it is
     * smaller, it will leave space.
     *
     * @param alignment The alignment of the artboard within the containing view. Defaults to
     *    [Alignment.Center].
     */
    data class None(override val alignment: Alignment = Alignment.Center) : Fit() {
        override val nativeMapping: Byte = 5
    }
}

/**
 * Alignment of the artboard within the containing view.
 *
 * @param nativeMapping An ordinal that maps to the core runtime constants. The core runtime does
 *    not use these values, so the mapping only exists in JNI.
 */
enum class Alignment(internal val nativeMapping: Byte) {
    TopLeft(0), TopCenter(1), TopRight(2),
    CenterLeft(3), Center(4), CenterRight(5),
    BottomLeft(6), BottomCenter(7), BottomRight(8)
}

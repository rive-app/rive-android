package app.rive.runtime.kotlin.core

/**
 * Representation of an axis-aligned bounding box (AABB).
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [nativePointer] keeps track of this relationship.
 *
 * The AABB helps us describe and keep track of shapes and artboards, by describing the
 * top left and bottom right vertices of a box.
 *
 */
class AABB {
    var nativePointer: Long

    private external fun constructor(width: Float, height: Float): Long
    private external fun nativeWidth(nativePointer: Long): Float
    private external fun nativeHeight(nativePointer: Long): Float

    constructor(_nativePointer: Long) : super() {
        nativePointer = _nativePointer
    }

    constructor(width: Float, height: Float) : super() {
        nativePointer = constructor(width, height)
    }

    /**
     * Return the width of the bounding box
     */
    val width: Float
        get() = nativeWidth(nativePointer)

    /**
     * Return the height of the bounding box
     */
    val height: Float
        get() = nativeHeight(nativePointer)
}

package app.rive.runtime.kotlin.core

/**
 * Representation of an axis-aligned bounding box (AABB).
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [cppPointer] keeps track of this relationship.
 *
 * The AABB helps us describe and keep track of shapes and artboards, by describing the
 * top left and bottom right vertices of a box.
 *
 */
class AABB {
    var cppPointer: Long

    private external fun constructor(width: Float, height: Float): Long
    private external fun cppWidth(cppPointer: Long): Float
    private external fun cppHeight(cppPointer: Long): Float

    constructor(_cppPointer: Long) : super() {
        cppPointer = _cppPointer
    }

    constructor(width: Float, height: Float) : super() {
        cppPointer = constructor(width, height)
    }

    /**
     * Return the width of the bounding box
     */
    val width: Float
        get() = cppWidth(cppPointer)

    /**
     * Return the height of the bounding box
     */
    val height: Float
        get() = cppHeight(cppPointer)
}

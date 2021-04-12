package app.rive.runtime.kotlin

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

    val width: Float
        get() = nativeWidth(nativePointer)
    val height: Float
        get() = nativeHeight(nativePointer)
}

package app.rive.runtime.kotlin

class AABB {
    var nativePointer: Long

    private external fun constructor(width: Float, height: Float): Long

    constructor(_nativePointer: Long) : super() {
        nativePointer = _nativePointer
    }

    constructor(width: Float, height: Float) : super() {
        nativePointer = constructor(width, height)
    }
}

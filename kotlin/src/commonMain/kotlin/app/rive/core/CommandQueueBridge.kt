package app.rive.core

import app.rive.RiveInitializationException

/**
 * Abstraction of calls to the native command queue.
 *
 * Allows for mocking in tests.
 */
interface CommandQueueBridge {
    @Throws(RiveInitializationException::class)
    fun cppConstructor(renderContextPointer: Long): Long
    fun cppDelete(pointer: Long)
    fun cppCreateListeners(pointer: Long, receiver: CommandQueue): NativeListeners

    fun cppPollMessages(pointer: Long)
    fun cppSetTracingEnabled(pointer: Long, enabled: Boolean)
    fun isCurrentThreadCommandServer(pointer: Long): Boolean

    fun cppLoadFile(pointer: Long, requestID: Long, bytes: ByteArray)
    fun cppDeleteFile(pointer: Long, requestID: Long, fileHandle: Long)

    fun cppGetArtboardNames(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )

    fun cppGetStateMachineNames(
        pointer: Long,
        requestID: Long,
        artboardHandle: Long
    )

    fun cppGetDefaultViewModelInfo(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        artboardHandle: Long
    )

    fun cppGetViewModelNames(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )

    fun cppGetViewModelInstanceNames(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    )

    fun cppGetViewModelProperties(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    )

    fun cppGetEnums(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )

    fun cppCreateDefaultArtboard(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    ): Long

    fun cppCreateArtboardByName(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        name: String
    ): Long

    fun cppDeleteArtboard(pointer: Long, requestID: Long, artboardHandle: Long)

    fun cppCreateDefaultStateMachine(
        pointer: Long,
        requestID: Long,
        artboardHandle: Long
    ): Long

    fun cppCreateStateMachineByName(
        pointer: Long,
        requestID: Long,
        artboardHandle: Long,
        name: String
    ): Long

    fun cppDeleteStateMachine(
        pointer: Long,
        requestID: Long,
        stateMachineHandle: Long
    )

    fun cppAdvanceStateMachine(
        pointer: Long,
        stateMachineHandle: Long,
        deltaTimeNs: Long
    )

    fun cppNamedVMCreateBlankVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    ): Long

    fun cppDefaultVMCreateBlankVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        artboardHandle: Long
    ): Long

    fun cppNamedVMCreateDefaultVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    ): Long

    fun cppDefaultVMCreateDefaultVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        artboardHandle: Long
    ): Long

    fun cppNamedVMCreateNamedVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String,
        instanceName: String
    ): Long

    fun cppDefaultVMCreateNamedVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        artboardHandle: Long,
        instanceName: String
    ): Long

    fun cppReferenceNestedVMI(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        path: String
    ): Long

    fun cppReferenceListItemVMI(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        path: String,
        index: Int
    ): Long

    fun cppGetViewModelInstanceViewModelName(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long
    )

    fun cppGetViewModelInstanceName(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long
    )

    fun cppDeleteViewModelInstance(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long
    )

    fun cppBindViewModelInstance(
        pointer: Long,
        requestID: Long,
        stateMachineHandle: Long,
        viewModelInstanceHandle: Long
    )

    fun cppSetNumberProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: Float
    )

    fun cppGetNumberProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    fun cppSetStringProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: String
    )

    fun cppGetStringProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    fun cppSetBooleanProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: Boolean
    )

    fun cppGetBooleanProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    fun cppSetEnumProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: String
    )

    fun cppGetEnumProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    fun cppSetColorProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: Int
    )

    fun cppGetColorProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    fun cppFireTriggerProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    fun cppSubscribeToProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        propertyType: Int
    )

    fun cppSetImageProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        imageHandle: Long
    )

    fun cppSetArtboardProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        artboardHandle: Long
    )

    fun cppSetViewModelInstanceProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        valueHandle: Long
    )

    fun cppGetListSize(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    fun cppInsertToListAtIndex(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        index: Int,
        itemHandle: Long
    )

    fun cppAppendToList(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        itemHandle: Long
    )

    fun cppRemoveFromListAtIndex(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        index: Int
    )

    fun cppRemoveFromList(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        itemHandle: Long
    )

    fun cppSwapListItems(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        indexA: Int,
        indexB: Int
    )

    fun cppDecodeImage(pointer: Long, requestID: Long, bytes: ByteArray)
    fun cppDeleteImage(pointer: Long, imageHandle: Long)
    fun cppRegisterImage(
        pointer: Long,
        name: String,
        imageHandle: Long
    )

    fun cppUnregisterImage(pointer: Long, name: String)
    fun cppDecodeAudio(pointer: Long, requestID: Long, bytes: ByteArray)
    fun cppDeleteAudio(pointer: Long, audioHandle: Long)
    fun cppRegisterAudio(
        pointer: Long,
        name: String,
        audioHandle: Long
    )

    fun cppUnregisterAudio(pointer: Long, name: String)
    fun cppDecodeFont(pointer: Long, requestID: Long, bytes: ByteArray)
    fun cppDeleteFont(pointer: Long, fontHandle: Long)
    fun cppRegisterFont(
        pointer: Long,
        name: String,
        fontHandle: Long
    )

    fun cppUnregisterFont(pointer: Long, name: String)
    fun cppPointerMove(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Byte,
        alignment: Byte,
        layoutScale: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    fun cppPointerDown(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Byte,
        alignment: Byte,
        layoutScale: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    fun cppPointerUp(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Byte,
        alignment: Byte,
        layoutScale: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    fun cppPointerExit(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Byte,
        alignment: Byte,
        layoutScale: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    fun cppResizeArtboard(
        pointer: Long,
        artboardHandle: Long,
        width: Int,
        height: Int,
        scaleFactor: Float
    )

    fun cppResetArtboardSize(
        pointer: Long,
        artboardHandle: Long
    )

    fun cppCreateDrawKey(pointer: Long): Long
    fun cppDraw(
        pointer: Long,
        renderContextPointer: Long,
        surfaceNativePointer: Long,
        drawKey: Long,
        artboardHandle: Long,
        stateMachineHandle: Long,
        width: Int,
        height: Int,
        fit: Byte,
        alignment: Byte,
        scaleFactor: Float,
        clearColor: Int
    )

    fun cppCancelDraw(pointer: Long, drawKey: Long)

    fun cppDrawToBuffer(
        pointer: Long,
        renderContextPointer: Long,
        surfaceNativePointer: Long,
        drawKey: Long,
        artboardHandle: Long,
        stateMachineHandle: Long,
        width: Int,
        height: Int,
        fit: Byte,
        alignment: Byte,
        scaleFactor: Float,
        clearColor: Int,
        buffer: ByteArray
    )

    fun cppRunOnCommandServer(pointer: Long, work: () -> Unit)

    /**
     * Deletes a native surface resource.
     *
     * Normal surface disposal calls this on the command server thread; creation-failure cleanup
     * may call it from the creating thread before the surface has ever been used for rendering.
     */
    fun cppDeleteSurface(surfacePointer: Long)

    /** Resizes a native surface render target. Runs on the command server thread. */
    fun cppResizeSurface(surfacePointer: Long, width: Int, height: Int)
}

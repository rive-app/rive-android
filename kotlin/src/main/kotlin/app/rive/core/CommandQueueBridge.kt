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
    fun cppCreateListeners(pointer: Long, receiver: CommandQueue): Listeners

    fun cppPollMessages(pointer: Long)

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

    fun cppCreateRiveRenderTarget(pointer: Long, width: Int, height: Int): Long
    fun cppCreateDrawKey(pointer: Long): Long
    fun cppDraw(
        pointer: Long,
        renderContextPointer: Long,
        surfaceNativePointer: Long,
        drawKey: Long,
        artboardHandle: Long,
        stateMachineHandle: Long,
        renderTargetPointer: Long,
        width: Int,
        height: Int,
        fit: Byte,
        alignment: Byte,
        scaleFactor: Float,
        clearColor: Int
    )

    fun cppDrawToBuffer(
        pointer: Long,
        renderContextPointer: Long,
        surfaceNativePointer: Long,
        drawKey: Long,
        artboardHandle: Long,
        stateMachineHandle: Long,
        renderTargetPointer: Long,
        width: Int,
        height: Int,
        fit: Byte,
        alignment: Byte,
        scaleFactor: Float,
        clearColor: Int,
        buffer: ByteArray
    )

    fun cppRunOnCommandServer(pointer: Long, work: () -> Unit)
}

/** Concrete JNI bridge implementation of [CommandQueueBridge]. */
internal class CommandQueueJNIBridge : CommandQueueBridge {
    external override fun cppConstructor(renderContextPointer: Long): Long
    external override fun cppDelete(pointer: Long)
    external override fun cppCreateListeners(pointer: Long, receiver: CommandQueue): Listeners

    external override fun cppPollMessages(pointer: Long)

    external override fun cppLoadFile(pointer: Long, requestID: Long, bytes: ByteArray)
    external override fun cppDeleteFile(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )

    external override fun cppGetArtboardNames(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )

    external override fun cppGetStateMachineNames(
        pointer: Long,
        requestID: Long,
        artboardHandle: Long
    )

    external override fun cppGetViewModelNames(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )

    external override fun cppGetViewModelInstanceNames(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    )

    external override fun cppGetViewModelProperties(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    )

    external override fun cppGetEnums(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )

    external override fun cppCreateDefaultArtboard(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    ): Long

    external override fun cppCreateArtboardByName(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        name: String
    ): Long

    external override fun cppDeleteArtboard(pointer: Long, requestID: Long, artboardHandle: Long)

    external override fun cppCreateDefaultStateMachine(
        pointer: Long,
        requestID: Long,
        artboardHandle: Long
    ): Long

    external override fun cppCreateStateMachineByName(
        pointer: Long,
        requestID: Long,
        artboardHandle: Long,
        name: String
    ): Long

    external override fun cppDeleteStateMachine(
        pointer: Long,
        requestID: Long,
        stateMachineHandle: Long
    )

    external override fun cppAdvanceStateMachine(
        pointer: Long,
        stateMachineHandle: Long,
        deltaTimeNs: Long
    )

    external override fun cppNamedVMCreateBlankVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    ): Long

    external override fun cppDefaultVMCreateBlankVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        artboardHandle: Long
    ): Long

    external override fun cppNamedVMCreateDefaultVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    ): Long

    external override fun cppDefaultVMCreateDefaultVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        artboardHandle: Long
    ): Long

    external override fun cppNamedVMCreateNamedVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String,
        instanceName: String
    ): Long

    external override fun cppDefaultVMCreateNamedVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        artboardHandle: Long,
        instanceName: String
    ): Long

    external override fun cppReferenceNestedVMI(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        path: String
    ): Long

    external override fun cppReferenceListItemVMI(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        path: String,
        index: Int
    ): Long

    external override fun cppDeleteViewModelInstance(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long
    )

    external override fun cppBindViewModelInstance(
        pointer: Long,
        requestID: Long,
        stateMachineHandle: Long,
        viewModelInstanceHandle: Long
    )

    external override fun cppSetNumberProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: Float
    )

    external override fun cppGetNumberProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    external override fun cppSetStringProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: String
    )

    external override fun cppGetStringProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    external override fun cppSetBooleanProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: Boolean
    )

    external override fun cppGetBooleanProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    external override fun cppSetEnumProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: String
    )

    external override fun cppGetEnumProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    external override fun cppSetColorProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: Int
    )

    external override fun cppGetColorProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    external override fun cppFireTriggerProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    external override fun cppSubscribeToProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        propertyType: Int
    )

    external override fun cppSetImageProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        imageHandle: Long
    )

    external override fun cppSetArtboardProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        artboardHandle: Long
    )

    external override fun cppGetListSize(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    external override fun cppInsertToListAtIndex(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        index: Int,
        itemHandle: Long
    )

    external override fun cppAppendToList(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        itemHandle: Long
    )

    external override fun cppRemoveFromListAtIndex(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        index: Int
    )

    external override fun cppRemoveFromList(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        itemHandle: Long
    )

    external override fun cppSwapListItems(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        indexA: Int,
        indexB: Int
    )

    external override fun cppDecodeImage(pointer: Long, requestID: Long, bytes: ByteArray)
    external override fun cppDeleteImage(pointer: Long, imageHandle: Long)
    external override fun cppRegisterImage(
        pointer: Long,
        name: String,
        imageHandle: Long
    )

    external override fun cppUnregisterImage(pointer: Long, name: String)
    external override fun cppDecodeAudio(pointer: Long, requestID: Long, bytes: ByteArray)
    external override fun cppDeleteAudio(pointer: Long, audioHandle: Long)
    external override fun cppRegisterAudio(
        pointer: Long,
        name: String,
        audioHandle: Long
    )

    external override fun cppUnregisterAudio(pointer: Long, name: String)
    external override fun cppDecodeFont(pointer: Long, requestID: Long, bytes: ByteArray)
    external override fun cppDeleteFont(pointer: Long, fontHandle: Long)
    external override fun cppRegisterFont(
        pointer: Long,
        name: String,
        fontHandle: Long
    )

    external override fun cppUnregisterFont(pointer: Long, name: String)
    external override fun cppPointerMove(
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

    external override fun cppPointerDown(
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

    external override fun cppPointerUp(
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

    external override fun cppPointerExit(
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

    external override fun cppResizeArtboard(
        pointer: Long,
        artboardHandle: Long,
        width: Int,
        height: Int,
        scaleFactor: Float
    )

    external override fun cppResetArtboardSize(
        pointer: Long,
        artboardHandle: Long
    )

    external override fun cppCreateRiveRenderTarget(pointer: Long, width: Int, height: Int): Long
    external override fun cppCreateDrawKey(pointer: Long): Long
    external override fun cppDraw(
        pointer: Long,
        renderContextPointer: Long,
        surfaceNativePointer: Long,
        drawKey: Long,
        artboardHandle: Long,
        stateMachineHandle: Long,
        renderTargetPointer: Long,
        width: Int,
        height: Int,
        fit: Byte,
        alignment: Byte,
        scaleFactor: Float,
        clearColor: Int
    )

    external override fun cppDrawToBuffer(
        pointer: Long,
        renderContextPointer: Long,
        surfaceNativePointer: Long,
        drawKey: Long,
        artboardHandle: Long,
        stateMachineHandle: Long,
        renderTargetPointer: Long,
        width: Int,
        height: Int,
        fit: Byte,
        alignment: Byte,
        scaleFactor: Float,
        clearColor: Int,
        buffer: ByteArray
    )

    external override fun cppRunOnCommandServer(pointer: Long, work: () -> Unit)
}

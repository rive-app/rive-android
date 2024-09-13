package app.rive.runtime.kotlin.core

import android.graphics.RectF
import app.rive.runtime.kotlin.core.errors.AnimationException
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.core.errors.StateMachineException
import app.rive.runtime.kotlin.core.errors.StateMachineInputException
import app.rive.runtime.kotlin.core.errors.TextValueRunException
import java.util.concurrent.locks.ReentrantLock

/**
 * [Artboard]s as designed in the Rive animation editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [unsafeCppPointer] keeps track of this relationship.
 *
 * [Artboard]s provide access to available [Animation]s, and some basic properties.
 * You can [draw] artboards using a [Renderer] that is tied to a canvas.
 *
 * The constructor uses a [unsafeCppPointer] to point to its c++ counterpart object.
 */
class Artboard(unsafeCppPointer: Long, private val lock: ReentrantLock) :
    NativeObject(unsafeCppPointer) {
    private external fun cppName(cppPointer: Long): String

    private external fun cppAnimationByIndex(cppPointer: Long, index: Int): Long
    private external fun cppAnimationByName(cppPointer: Long, name: String): Long
    private external fun cppAnimationCount(cppPointer: Long): Int
    private external fun cppAnimationNameByIndex(cppPointer: Long, index: Int): String

    private external fun cppStateMachineByIndex(cppPointer: Long, index: Int): Long
    private external fun cppStateMachineByName(cppPointer: Long, name: String): Long
    private external fun cppStateMachineCount(cppPointer: Long): Int
    private external fun cppStateMachineNameByIndex(cppPointer: Long, index: Int): String

    private external fun cppInputByNameAtPath(cppPointer: Long, name: String, path: String): Long

    private external fun cppGetVolume(cppPointer: Long): Float
    private external fun cppSetVolume(cppPointer: Long, volume: Float)

    private external fun cppAdvance(cppPointer: Long, elapsedTime: Float): Boolean
    private external fun cppFindTextValueRun(cppPointer: Long, name: String): Long
    private external fun cppFindValueOfTextValueRun(cppPointer: Long, name: String): String?
    private external fun cppSetValueOfTextValueRun(cppPointer: Long, name: String, newText: String): Boolean
    private external fun cppFindTextValueRunAtPath(cppPointer: Long, name: String, path: String): Long
    private external fun cppFindValueOfTextValueRunAtPath(cppPointer: Long, name: String, path: String): String?
    private external fun cppSetValueOfTextValueRunAtPath(cppPointer: Long, name: String, newText: String, path: String): Boolean


    private external fun cppDraw(cppPointer: Long, rendererPointer: Long)

    private external fun cppDrawAligned(
        cppPointer: Long, rendererPointer: Long,
        fit: Fit, alignment: Alignment,
    )

    private external fun cppBounds(cppPointer: Long): RectF

    external override fun cppDelete(pointer: Long)

    /**
     * Get the [name] of the Artboard.
     */
    val name: String
        get() = cppName(cppPointer)

    /**
     * Get the first [Animation] of the [Artboard].
     *
     * If you use more than one animation, it is preferred to use the [animation] functions.
     */
    val firstAnimation: LinearAnimationInstance
        @Throws(RiveException::class)
        get() {
            return animation(0)
        }

    /**
     * Get the animation at a given [index] in the [Artboard].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun animation(index: Int): LinearAnimationInstance {
        val animationPointer = cppAnimationByIndex(cppPointer, index)
        if (animationPointer == NULL_POINTER) {
            throw AnimationException("No Animation found at index $index.")
        }
        val lai = LinearAnimationInstance(animationPointer, lock)
        dependencies.add(lai)
        return lai
    }

    /**
     * Get the animation with a given [name] in the [Artboard].
     */
    @Throws(RiveException::class)
    fun animation(name: String): LinearAnimationInstance {
        val animationPointer = cppAnimationByName(cppPointer, name)
        if (animationPointer == NULL_POINTER) {
            throw AnimationException(
                "Animation \"$name\" not found. " +
                        "Available Animations: ${animationNames.map { "\"$it\"" }}\""
            )
        }
        val lai = LinearAnimationInstance(animationPointer, lock)
        dependencies.add(lai)
        return lai
    }

    /**
     * Get the first [StateMachine] of the [Artboard].
     *
     * If you use more than one animation, it is preferred to use the [stateMachine] functions.
     */
    val firstStateMachine: StateMachineInstance
        @Throws(RiveException::class)
        get() {
            return stateMachine(0)
        }


    /**
     * Get the animation at a given [index] in the [Artboard].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun stateMachine(index: Int): StateMachineInstance {
        val stateMachinePointer = cppStateMachineByIndex(cppPointer, index)
        if (stateMachinePointer == NULL_POINTER) {
            throw StateMachineException("No StateMachine found at index $index.")
        }
        val smi = StateMachineInstance(stateMachinePointer, lock)
        dependencies.add(smi)
        return smi
    }

    /**
     * Get the animation with a given [name] in the [Artboard].
     */
    @Throws(RiveException::class)
    fun stateMachine(name: String): StateMachineInstance {
        val stateMachinePointer = cppStateMachineByName(cppPointer, name)
        if (stateMachinePointer == NULL_POINTER) {
            throw StateMachineException("No StateMachine found with name $name.")
        }
        val smi = StateMachineInstance(stateMachinePointer, lock)
        dependencies.add(smi)
        return smi
    }

    /**
     * Get the input instance with a given [name] on the nested artboard represented at [path].
     */
    @Throws(RiveException::class)
    fun input(name: String, path: String): SMIInput {
        val stateMachineInputPointer = cppInputByNameAtPath(cppPointer, name, path)
        if (stateMachineInputPointer == NULL_POINTER) {
            throw StateMachineInputException("No StateMachineInput found with name $name in nested artboard $path.")
        }
        val input = SMIInput(stateMachineInputPointer)
        return convertInput(input)
    }

    /**
     * Get a [RiveTextValueRun] with a given [name] in the [Artboard].
     * @return The text value run.
     * @throws RiveException if the text run does not exist.
     */
    @Throws(RiveException::class)
    fun textRun(name: String): RiveTextValueRun {
        val textRunPointer = cppFindTextValueRun(cppPointer, name)
        if (textRunPointer == NULL_POINTER) {
            throw TextValueRunException("No Rive TextValueRun found with name \"$name.\"")
        }
        val run = RiveTextValueRun(textRunPointer)
        dependencies.add(run)
        return run
    }

    /**
     * Get the text value for a text run named [name].
     * @return The text value of the run, or null if the run is not found.
     */
    fun getTextRunValue(name: String): String? {
        return cppFindValueOfTextValueRun(cppPointer, name)
    }

    /**
     * Set the text value for a text run named [name] to [textValue].
     * @throws RiveException if the text run does not exist.
     */
    fun setTextRunValue(name: String, textValue: String) {
        val successCheck = cppSetValueOfTextValueRun(cppPointer, name, textValue)
        if (!successCheck) {
            throw TextValueRunException("Could not set text run. No Rive TextValueRun found with name \"$name.\"")
        }
    }

    /**
     * Get a [RiveTextValueRun] with a given [name] on the nested artboard represented at [path].
     * @return The text value run.
     * @throws RiveException if the text run does not exist.
     */
    @Throws(RiveException::class)
    fun textRun(name: String, path: String): RiveTextValueRun {
        val textRunPointer = cppFindTextValueRunAtPath(cppPointer, name, path)
        if (textRunPointer == NULL_POINTER) {
            throw TextValueRunException("No Rive TextValueRun found with name \"$name.\" in nested artboard $path")
        }
        val run = RiveTextValueRun(textRunPointer)
        dependencies.add(run)
        return run
    }

    /**
     * Get the text value for a text run named [name] on the nested artboard
     * represented at [path].
     * @return The text value of the run, or null if the run is not found.
     */
    fun getTextRunValue(name: String, path: String): String? {
        return cppFindValueOfTextValueRunAtPath(cppPointer, name, path)
    }

    /**
     * Set the text value for a text run named [name] to [textValue] on the nested artboard
     * represented at [path].
     * @throws RiveException if the text run does not exist.
     */
    fun setTextRunValue(name: String, textValue: String, path: String) {
        val successCheck = cppSetValueOfTextValueRunAtPath(cppPointer, name, textValue, path)
        if (!successCheck) {
            throw TextValueRunException("Could not set text run value at path. No Rive TextValueRun found with name \"$name.\" in nested artboard \"$path.\"")
        }
    }

    /**
     * Get and set the volume of the [Artboard].
     */
    var volume: Float
        get() = cppGetVolume(cppPointer)
        internal set(value) = cppSetVolume(cppPointer, value )

    /**
     * Get the number of animations stored inside the [Artboard].
     */
    val animationCount: Int
        get() = cppAnimationCount(cppPointer)

    /**
     * Get the number of state machines stored inside the [Artboard].
     */
    val stateMachineCount: Int
        get() = cppStateMachineCount(cppPointer)

    /**
     * Advancing the artboard updates the layout for all dirty components contained in the [Artboard]
     * updates the positions forces all components in the [Artboard] to be laid out.
     *
     * Components are all the shapes, bones and groups of an [Artboard].
     * Whenever components are added to an artboard, for example when an artboard is first loaded, they are considered dirty.
     * Whenever animations change properties of components, move a shape or change a color, they are marked as dirty.
     *
     * Before any changes to components will be visible in the next rendered frame, the artboard needs to be [advance]d.
     *
     * [elapsedTime] is currently not taken into account.
     */
    fun advance(elapsedTime: Float): Boolean {
        synchronized(lock) { return cppAdvance(cppPointer, elapsedTime) }
    }

    /**
     * Draw the the artboard to the [renderer].
     */
    fun drawSkia(rendererAddress: Long) {
        synchronized(lock) { cppDraw(cppPointer, rendererAddress) }
    }

    /**
     * Draw the the artboard to the [renderer].
     * Also align the artboard to the render surface
     */
    fun drawSkia(rendererAddress: Long, fit: Fit, alignment: Alignment) {
        synchronized(lock) { cppDrawAligned(cppPointer, rendererAddress, fit, alignment) }
    }

    /**
     * Get the bounds of Artboard as defined in the rive editor.
     */
    val bounds: RectF
        get() = cppBounds(cppPointer)

    /**
     * Get the names of the animations in the artboard.
     */
    val animationNames: List<String>
        get() = (0 until animationCount).map { cppAnimationNameByIndex(cppPointer, it) }

    /**
     * Get the names of the stateMachines in the artboard.
     */
    val stateMachineNames: List<String>
        get() = (0 until stateMachineCount).map { cppStateMachineNameByIndex(cppPointer, it) }

    private fun convertInput(input: SMIInput): SMIInput {
        val convertedInput = when {
            input.isBoolean -> SMIBoolean(input.cppPointer)
            input.isTrigger -> SMITrigger(input.cppPointer)
            input.isNumber -> SMINumber(input.cppPointer)
            else -> throw StateMachineInputException("Unknown State Machine Input Instance for ${input.name}.")
        }
        return convertedInput
    }
}
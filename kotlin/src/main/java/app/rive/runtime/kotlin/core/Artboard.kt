package app.rive.runtime.kotlin.core

import android.graphics.RectF
import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import app.rive.runtime.kotlin.core.errors.AnimationException
import app.rive.runtime.kotlin.core.errors.StateMachineException
import app.rive.runtime.kotlin.core.errors.StateMachineInputException
import app.rive.runtime.kotlin.core.errors.TextValueRunException
import app.rive.runtime.kotlin.core.errors.ViewModelException
import java.util.concurrent.locks.ReentrantLock

/**
 * [Artboard]s as designed in the Rive animation editor.
 *
 * [Artboard]s provide access to available [animation]s, and some basic properties. You can [draw]
 * artboards using a [Renderer][app.rive.runtime.kotlin.renderers.Renderer] that is tied to a
 * canvas.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 */
@OpenForTesting
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
    private external fun cppSetValueOfTextValueRun(
        cppPointer: Long,
        name: String,
        newText: String
    ): Boolean

    private external fun cppFindTextValueRunAtPath(
        cppPointer: Long,
        name: String,
        path: String
    ): Long

    private external fun cppFindValueOfTextValueRunAtPath(
        cppPointer: Long,
        name: String,
        path: String
    ): String?

    private external fun cppSetValueOfTextValueRunAtPath(
        cppPointer: Long,
        name: String,
        newText: String,
        path: String
    ): Boolean


    private external fun cppDraw(cppPointer: Long, rendererPointer: Long)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected external fun cppDrawAligned(
        cppPointer: Long, rendererPointer: Long,
        fit: Fit, alignment: Alignment,
        scaleFactor: Float
    )

    private external fun cppBounds(cppPointer: Long): RectF

    private external fun cppResetArtboardSize(cppPointer: Long)

    private external fun cppGetArtboardWidth(cppPointer: Long): Float
    private external fun cppSetArtboardWidth(cppPointer: Long, width: Float)
    private external fun cppGetArtboardHeight(cppPointer: Long): Float
    private external fun cppSetArtboardHeight(cppPointer: Long, height: Float)

    private external fun cppSetViewModelInstance(cppPointer: Long, instancePointer: Long)

    external override fun cppDelete(pointer: Long)

    override fun release(): Int = synchronized(lock) {
        super.release()
    }

    /** Get the [name] of the Artboard. */
    val name: String
        get() = cppName(cppPointer)

    /**
     * Get the first [animation][LinearAnimationInstance] of the [Artboard].
     *
     * If you use more than one animation, it is preferred to use the [animation] functions.
     *
     * @throws AnimationException if the animation does not exist.
     */
    val firstAnimation: LinearAnimationInstance
        @Throws(AnimationException::class)
        get() = animation(0)

    /**
     * Get the animation at a given 0-based [index] in the [Artboard].
     *
     * @throws AnimationException If the animation does not exist.
     */
    @Throws(AnimationException::class)
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
     *
     * @throws AnimationException If the animation does not exist.
     */
    @Throws(AnimationException::class)
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
     * Get the first [state machine][StateMachineInstance] of the artboard.
     *
     * If you use more than one animation, it is preferred to use the [stateMachine] functions.
     *
     * @throws StateMachineException If the state machine does not exist.
     */
    val firstStateMachine: StateMachineInstance
        @Throws(StateMachineException::class)
        get() = stateMachine(0)

    /**
     * Get the state machine at a given 0-based [index] in the artboard.
     *
     * @throws StateMachineException If the state machine does not exist.
     */
    @Throws(StateMachineException::class)
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
     * Get the state machine with a given [name] in the artboard.
     *
     * @throws StateMachineException If the state machine does not exist.
     */
    @Throws(StateMachineException::class)
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
     *
     * @throws StateMachineInputException If the input does not exist.
     */
    @Throws(StateMachineInputException::class)
    fun input(name: String, path: String): SMIInput {
        val stateMachineInputPointer = cppInputByNameAtPath(cppPointer, name, path)
        if (stateMachineInputPointer == NULL_POINTER) {
            throw StateMachineInputException("No StateMachineInput found with name \"$name\" in nested artboard $path.")
        }
        val input = SMIInput(stateMachineInputPointer)
        return convertInput(input)
    }

    /**
     * Get a [RiveTextValueRun] with a given [name] in the artboard.
     *
     * @return The text value run.
     * @throws TextValueRunException If the text run does not exist.
     */
    @Throws(TextValueRunException::class)
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
     *
     * @return The text value of the run, or null if the run is not found.
     */
    fun getTextRunValue(name: String): String? = cppFindValueOfTextValueRun(cppPointer, name)

    /**
     * Set the text value for a text run named [name] to [textValue].
     *
     * @throws TextValueRunException If the text run does not exist.
     */
    @Throws(TextValueRunException::class)
    fun setTextRunValue(name: String, textValue: String) {
        val successCheck = cppSetValueOfTextValueRun(cppPointer, name, textValue)
        if (!successCheck) {
            throw TextValueRunException("Could not set text run. No Rive TextValueRun found with name \"$name.\"")
        }
    }

    /**
     * Get a [RiveTextValueRun] with a given [name] on the nested artboard represented at [path].
     *
     * @return The text value run.
     * @throws TextValueRunException If the text run does not exist.
     */
    @Throws(TextValueRunException::class)
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
     * Get the text value for a text run named [name] on the nested artboard represented at [path].
     *
     * @return The text value of the run, or null if the run is not found.
     */
    fun getTextRunValue(name: String, path: String): String? =
        cppFindValueOfTextValueRunAtPath(cppPointer, name, path)

    /**
     * Set the text value for a text run named [name] to [textValue] on the nested artboard
     * represented at [path].
     *
     * @throws TextValueRunException If the text run does not exist.
     */
    @Throws(TextValueRunException::class)
    fun setTextRunValue(name: String, textValue: String, path: String) {
        val successCheck = cppSetValueOfTextValueRunAtPath(cppPointer, name, textValue, path)
        if (!successCheck) {
            throw TextValueRunException("Could not set text run value at path. No Rive TextValueRun found with name \"$name.\" in nested artboard \"$path.\"")
        }
    }

    /** Get and set the volume of the artboard. */
    var volume: Float
        get() = cppGetVolume(cppPointer)
        internal set(value) = cppSetVolume(cppPointer, value)

    /** @return The number of animations stored inside the artboard. */
    val animationCount: Int
        get() = cppAnimationCount(cppPointer)

    /** @return The number of state machines stored inside the artboard. */
    val stateMachineCount: Int
        get() = cppStateMachineCount(cppPointer)

    /**
     * The [ViewModelInstance] assigned to this artboard. Once assigned, modifications to the
     * properties of the instance will be reflected in the bindings of this artboard.
     *
     * Assigning null will do nothing.
     *
     * You should only use assign this property if your file does not contain a state machine. If it
     * does, prefer [StateMachineInstance.viewModelInstance] instead, as it will set the view model
     * for both the state machine and the artboard.
     */
    var viewModelInstance: ViewModelInstance? = null
        set(value) {
            value?.let {
                cppSetViewModelInstance(cppPointer, it.cppPointer)
                field = value
            }
        }

    /**
     * @throws ViewModelException If the instance contained by [transfer] has already been assigned
     *    or disposed.
     */
    @Throws(ViewModelException::class)
    fun receiveViewModelInstance(transfer: ViewModelInstance.Transfer): ViewModelInstance =
        transfer.end().also {
            dependencies.add(it)
            viewModelInstance = it
        }

    /**
     * Advancing the artboard:
     * - Updates the layout for all dirty components contained in the artboard
     * - Updates the positions
     * - Forces all components in the artboard to be laid out
     *
     * Components are all the shapes, bones and groups of an artboard. Whenever components are added
     * to an artboard, for example when an artboard is first loaded, they are considered dirty.
     * Whenever animations change properties of components, move a shape, or change a color, they
     * are marked as dirty.
     *
     * Before any changes to components will be visible in the next rendered frame, the artboard
     * needs to be [advanced][advance].
     *
     * [elapsedTime] is currently not taken into account.
     */
    fun advance(elapsedTime: Float): Boolean =
        synchronized(lock) { cppAdvance(cppPointer, elapsedTime) }

    /** Draw the the artboard to the [renderer][app.rive.runtime.kotlin.renderers.Renderer]. */
    @WorkerThread
    fun draw(rendererAddress: Long) = synchronized(lock) {
        if (!hasCppObject) return
        cppDraw(cppPointer, rendererAddress)
    }

    /**
     * Draw the the artboard to the [renderer][app.rive.runtime.kotlin.renderers.Renderer]. Also
     * align the artboard to the render surface.
     */
    @WorkerThread
    fun draw(rendererAddress: Long, fit: Fit, alignment: Alignment, scaleFactor: Float = 1.0f) =
        synchronized(lock) {
            if (!hasCppObject) return

            cppDrawAligned(
                cppPointer,
                rendererAddress,
                fit,
                alignment,
                scaleFactor
            )
        }

    /** Reset the artboard size to its defaults. */
    fun resetArtboardSize() = cppResetArtboardSize(cppPointer)

    /** @return The bounds of artboard as defined in the Rive Editor. */
    val bounds: RectF
        get() = cppBounds(cppPointer)

    /** The width of the artboard. */
    var width: Float
        get() = cppGetArtboardWidth(cppPointer)
        set(value) = cppSetArtboardWidth(cppPointer, value)

    /** The height of the artboard. */
    var height: Float
        get() = cppGetArtboardHeight(cppPointer)
        set(value) = cppSetArtboardHeight(cppPointer, value)

    /** @return The names of all animations in the artboard. */
    val animationNames: List<String>
        get() = (0 until animationCount).map { cppAnimationNameByIndex(cppPointer, it) }

    /** @return The names of all stateMachines in the artboard. */
    val stateMachineNames: List<String>
        get() = (0 until stateMachineCount).map { cppStateMachineNameByIndex(cppPointer, it) }

    private fun convertInput(input: SMIInput): SMIInput =
        when {
            input.isBoolean -> SMIBoolean(input.cppPointer)
            input.isTrigger -> SMITrigger(input.cppPointer)
            input.isNumber -> SMINumber(input.cppPointer)
            else -> throw StateMachineInputException("Unknown State Machine Input Instance for ${input.name}.")
        }
}
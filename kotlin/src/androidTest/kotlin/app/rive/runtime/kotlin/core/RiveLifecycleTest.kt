package app.rive.runtime.kotlin.core

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveViewLifecycleObserverTest {

    private lateinit var context: Context
    private lateinit var lifecycleOwner: TestUtils.MockLifecycleOwner
    private val testUtils = TestUtils()

    @Before
    fun setup() {
        context = testUtils.context
        lifecycleOwner = TestUtils.MockLifecycleOwner()
    }

    @Test
    fun observer_releasesDependencies_whenLifecycleIsDestroyed() {
        UiThreadStatement.runOnUiThread {
            val view = TestUtils.MockRiveAnimationView(context, attachOnInit = false)
            val controller = view.controller

            // The Controller is now part of the Observer's dependencies.
            val observer = view.createObserver()
            lifecycleOwner.lifecycle.addObserver(observer)
            lifecycleOwner.moveToState(Lifecycle.State.RESUMED)

            // Our View creates the Controller.
            assertEquals(
                "Controller refCount should be 1 after creation.",
                1,
                controller.refCount
            )

            // Attach the View: create the renderer, which acquires the controller.
            view.mockAttach()
            assertEquals(
                "Controller refCount should be 2 after attach.",
                2,
                controller.refCount
            )

            // Detach the view: destroys the renderer, which releases the controller.
            view.mockDetach(destroy = false) // Don't call mockOnDestroy yet.
            assertEquals(
                "Controller refCount should be 1 after detach.",
                1,
                controller.refCount
            )

            // Now, simulate the lifecycle ending, which will trigger the observer's onDestroy.
            lifecycleOwner.moveToState(Lifecycle.State.DESTROYED)

            // The observer's onDestroy is the single source of truth for the final release.
            // It releases the controller.
            assertEquals(
                "Controller refCount should be 0 after observer's onDestroy.",
                0,
                controller.refCount
            )
        }
    }

    @Test
    fun observer_whenViewIsNotAttached_releasesControllerAsFallback() {
        UiThreadStatement.runOnUiThread {
            val view = TestUtils.MockRiveAnimationView(context, attachOnInit = false)
            val controller = view.controller
            // Our View creates the Controller.
            val initialRefCount = controller.refCount
            assertEquals(1, initialRefCount)

            val observer = view.createObserver()
            lifecycleOwner.lifecycle.addObserver(observer)
            lifecycleOwner.moveToState(Lifecycle.State.CREATED)

            // Simulate the lifecycle ending *without the view ever being attached*.
            lifecycleOwner.moveToState(Lifecycle.State.DESTROYED)

            // The observer's onDestroy should act as the sole cleanup mechanism.
            assertEquals(
                "Controller refCount should be 0 after observer's onDestroy.",
                0,
                controller.refCount
            )
        }
    }
}

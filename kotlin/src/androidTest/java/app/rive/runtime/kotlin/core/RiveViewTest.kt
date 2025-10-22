package app.rive.runtime.kotlin.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.errors.ArtboardException
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveViewTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var mockView: RiveAnimationView

    @Before
    fun initView() {
        mockView = TestUtils.MockRiveAnimationView(appContext)
    }

    @Test
    fun viewNoDefaults() {
        UiThreadStatement.runOnUiThread {
            assertFalse(mockView.isPlaying)
            assertNull(mockView.controller.file)
        }
    }

    @Test
    fun viewDefaultsLoadResource() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards, autoplay = false)
            assertNotNull(mockView.controller.file)
            mockView.play(listOf("artboard2animation1", "artboard2animation2"))

            assertTrue(mockView.isPlaying)
            assertTrue(mockView.controller.isActive)
            assertEquals(listOf("artboard2", "artboard1"), mockView.file?.artboardNames)
            assertEquals(
                listOf("artboard2animation1", "artboard2animation2"),
                mockView.animations.map { it.name }.toList()
            )
        }
    }

    @Test
    fun viewDefaultsChangeArtboard() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards)
            assertEquals(true, mockView.isPlaying)
            mockView.artboardName = "artboard1"
            assertEquals(
                listOf("artboard1animation1"),
                mockView.animations.map { it.name }.toList()
            )
        }
    }

    @Test(expected = ArtboardException::class)
    fun viewChangeToMissingArtboard() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards)
            mockView.artboardName = "artboardDoesntExist"
        }

    }

    @Test
    fun viewDefaultsNoAutoplay() {
        UiThreadStatement.runOnUiThread {
            mockView.autoplay = false
            mockView.setRiveResource(R.raw.multipleartboards)
            assert(!mockView.isPlaying)
            mockView.artboardName = "artboard2"
            assertEquals(
                listOf<String>(),
                mockView.animations.map { it.name }.toList()
            )
            mockView.play(listOf("artboard2animation1", "artboard2animation2"))
            assertEquals(
                listOf("artboard2animation1", "artboard2animation2"),
                mockView.animations.map { it.name }.toList()
            )
        }
    }

    @Test
    fun viewPause() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards)
            assertEquals(true, mockView.isPlaying)
            assertEquals(1, mockView.animations.size)
            assertEquals(1, mockView.playingAnimations.size)
            mockView.pause()
            // Paused right away.
            assertFalse(mockView.isPlaying)
            assertNotNull(mockView.artboardRenderer)
            assertEquals(1, mockView.animations.size)
            assertEquals(0, mockView.playingAnimations.size)
        }
    }


    @Test
    fun viewPauseOneByOne() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            mockView.play(listOf("one", "two", "three", "four"))

            assertTrue(mockView.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            mockView.pause("junk")
            assertTrue(mockView.isPlaying)
            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                hashSetOf("one", "two", "three", "four")
            )

            mockView.pause("one")
            assertTrue(mockView.isPlaying)
            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                hashSetOf("two", "three", "four")
            )
            mockView.pause("two")
            assertTrue(mockView.isPlaying)

            mockView.pause("three")
            assertTrue(mockView.isPlaying)

            mockView.pause("four")
            assertTrue(mockView.isPlaying)
            assertNotNull(mockView.artboardRenderer)
            // Pause happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assertFalse(mockView.isPlaying)

            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                emptySet<String>()
            )
        }
    }

    @Test
    fun viewPauseMultiple() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            mockView.play(listOf("one", "two", "three", "four"))
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                hashSetOf("one", "two", "three", "four")
            )

            mockView.pause(listOf("one", "three"))
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                hashSetOf("two", "four")
            )

            mockView.pause(listOf("two", "four"))
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Pause happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assert(!mockView.isPlaying)
            assertEquals(
                mockView.playingAnimations.map { it.name }.toHashSet(),
                emptySet<String>()
            )
        }
    }

    @Test
    fun viewPlay() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards, autoplay = false)
            assert(!mockView.isPlaying)
            assertEquals(0, mockView.animations.size)
            assertEquals(0, mockView.playingAnimations.size)
            mockView.play()
            assertEquals(true, mockView.isPlaying)
            assertEquals(1, mockView.animations.size)
            assertEquals(1, mockView.playingAnimations.size)
        }
    }

    @Test
    fun viewPlayOneByOne() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assert(!mockView.isPlaying)
            assertEquals(
                emptySet<String>(),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            mockView.play("one")
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("one"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
        }
    }

    @Test(expected = RiveException::class)
    fun viewPlayJunk() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assert(!mockView.isPlaying)
            assertEquals(
                emptySet<String>(),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            mockView.play("junk")
        }
    }

    @Test
    fun viewPlayMultiple() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assert(!mockView.isPlaying)
            assertEquals(
                emptySet<String>(),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            mockView.play(listOf("one", "two"))
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("one", "two"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
        }
    }

    @Test
    fun viewPlayLoopMode() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)

            // Mode dictated by animation.
            mockView.play("one")
            assertEquals(Loop.ONESHOT, mockView.playingAnimations.first().loop)

            // forced loop
            mockView.play("one", Loop.LOOP)
            assertEquals(Loop.LOOP, mockView.playingAnimations.first().loop)

            // mode unchanged.
            mockView.play("one")
            assertEquals(Loop.LOOP, mockView.playingAnimations.first().loop)

            // mode unchanged.
            mockView.play("one", Loop.PINGPONG)
            assertEquals(Loop.PINGPONG, mockView.playingAnimations.first().loop)

            // mode unchanged.
            mockView.play("one", Loop.ONESHOT)
            assertEquals(Loop.ONESHOT, mockView.playingAnimations.first().loop)
        }
    }

    @Test
    fun viewPlayDirection() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)

            // setting auto direction doesn't change direction
            mockView.play("one")
            assertEquals(Direction.FORWARDS, mockView.playingAnimations.first().direction)
            mockView.play("one", direction = Direction.AUTO)
            assertEquals(Direction.FORWARDS, mockView.playingAnimations.first().direction)
            mockView.play("one", direction = Direction.BACKWARDS)
            assertEquals(Direction.BACKWARDS, mockView.playingAnimations.first().direction)
            mockView.play("one", direction = Direction.AUTO)
            assertEquals(Direction.BACKWARDS, mockView.playingAnimations.first().direction)
            mockView.pause("one")

            // PingPong cycles between forwards and backwards
            mockView.play("two", loop = Loop.PINGPONG)
            assertEquals(Direction.FORWARDS, mockView.playingAnimations.first().direction)
            assert(mockView.artboardRenderer != null)
            mockView.artboardRenderer!!.advance(1001f)
            assertEquals(Direction.BACKWARDS, mockView.playingAnimations.first().direction)

        }
    }


    @Test
    fun viewPlayPaused() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assertFalse(mockView.isPlaying)
            assertEquals(0, mockView.playingAnimations.size)
            mockView.play("one")
            assertTrue(mockView.isPlaying)
            assertTrue(mockView.artboardRenderer!!.isPlaying)
            assertEquals(
                hashSetOf("one"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            // Pause all.
            mockView.pause()
            assertFalse(mockView.isPlaying)
            assertFalse(mockView.artboardRenderer!!.isPlaying)
            // Restart.
            mockView.play("one")
            assertTrue(mockView.isPlaying)
            assertTrue(mockView.artboardRenderer!!.isPlaying)
            assertEquals(
                hashSetOf("one"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
        }
    }

    @Test
    fun viewPauseAllPlayOne() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assertFalse(mockView.isPlaying)
            assertEquals(0, mockView.playingAnimations.size)
            mockView.play(listOf("one", "two", "three"))
            assertTrue(mockView.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            // Pause all.
            mockView.pause()
            assertFalse(mockView.isPlaying)
            // Restart.
            mockView.play()
            assertTrue(mockView.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )

            // Pause all.
            mockView.pause()
            assertFalse(mockView.isPlaying)
            // Play one
            mockView.play("two")
            assertTrue(mockView.isPlaying)
            assertEquals(
                hashSetOf("two"),
                mockView.playingAnimations.map { it.name }.toHashSet()
            )
            // Check all animations that can be restarted.
            assertEquals(
                hashSetOf("one", "three"),
                mockView.controller.pausedAnimations.map { it.name }.toHashSet()
            )
        }
    }


    @Test
    fun viewSetResourceLoadArtboard() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations)
            assert(mockView.artboardRenderer != null)
            assertEquals(
                listOf("four", "three", "two", "one"),
                mockView.file?.firstArtboard?.animationNames
            )

            mockView.setRiveResource(R.raw.multipleartboards)
            assertEquals(
                listOf("artboard2animation1", "artboard2animation2"),
                mockView.file?.firstArtboard?.animationNames
            )
        }
    }

    @Test
    fun viewSetResourceLoadArtboardArtboardGotcha() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, artboardName = "New Artboard")
            mockView.setRiveResource(R.raw.multipleartboards)
        }
    }

    @Test
    fun viewSetResourceLoadArtboardArtboardGotchaOK() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, artboardName = "New Artboard")
            mockView.setRiveResource(R.raw.multipleartboards, artboardName = "artboard1")
        }
    }


    @Test
    fun viewStop() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards)
            assertEquals(true, mockView.isPlaying)
            assertEquals(1, mockView.animations.size)
            assertEquals(1, mockView.playingAnimations.size)

            mockView.stop()
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Stop happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assert(!mockView.isPlaying)
            assertEquals(0, mockView.animations.size)
            assertEquals(0, mockView.playingAnimations.size)
        }
    }

    @Test
    fun viewStopMultiple() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            mockView.play(listOf("one", "two", "three", "four"))
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                mockView.playingAnimations.map { it.name }.toHashSet(),
            )
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                mockView.animations.map { it.name }.toHashSet(),
            )

            mockView.stop(listOf("one", "three"))
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("two", "four"),
                mockView.playingAnimations.map { it.name }.toHashSet(),
            )
            assertEquals(
                hashSetOf("two", "four"),
                mockView.animations.map { it.name }.toHashSet(),
            )

            mockView.stop(listOf("two", "four"))
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Stop happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assert(!mockView.isPlaying)
            assertEquals(
                hashSetOf<String>(),
                mockView.playingAnimations.map { it.name }.toHashSet(),
            )
            assertEquals(
                hashSetOf<String>(),
                mockView.animations.map { it.name }.toHashSet(),
            )
        }
    }

    @Test
    fun viewStopOneByOne() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            mockView.play(listOf("one", "two", "three", "four"))

            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                mockView.animations.map { it.name }.toHashSet()
            )
            mockView.stop("junk")
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("one", "two", "three", "four"),
                mockView.animations.map { it.name }.toHashSet()
            )

            mockView.stop("one")
            assertEquals(true, mockView.isPlaying)
            assertEquals(
                hashSetOf("two", "three", "four"),
                mockView.animations.map { it.name }.toHashSet()
            )

            mockView.stop("two")
            assertEquals(true, mockView.isPlaying)

            mockView.stop("three")
            assertEquals(true, mockView.isPlaying)

            mockView.stop("four")
            assert(mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            // Stop happens on the next frame.
            mockView.artboardRenderer!!.scheduleFrame()
            assert(!mockView.isPlaying)

            assertEquals(
                emptySet<String>(),
                mockView.animations.map { it.name }.toHashSet()
            )
        }
    }

    @Test
    fun viewStopAnimationDetailsTime() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)

            assert(mockView.artboardRenderer != null)
            val renderer = mockView.artboardRenderer!!
            mockView.play("one", Loop.PINGPONG)
            renderer.advance(0.1f)

            assertEquals(0.1f, mockView.animations.first().time)

            assert(renderer.isPlaying)
            mockView.stop("one")
            renderer.scheduleFrame()
            assert(!renderer.isPlaying)
            mockView.play("one")
            assertEquals(0.0f, mockView.animations.first().time)
            assertEquals(Loop.ONESHOT, mockView.animations.first().loop)
        }
    }

    @Test
    fun viewPauseAnimationDetailsTime() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            assert(!mockView.isPlaying)

            mockView.play("one", Loop.PINGPONG)

            assert(mockView.artboardRenderer != null)
            mockView.artboardRenderer!!.advance(0.1f)
            assertEquals(0.1f, mockView.animations.first().time)

            mockView.pause("one")
            mockView.play("one")
            assertEquals(0.1f, mockView.animations.first().time)
            assertEquals(Loop.PINGPONG, mockView.animations.first().loop)
        }
    }

    @Test
    fun viewReset() {
        // pretty basic test. we could start seeing if the artboards properties are reset properly
        // but we actually would need to expose a lot more of that to do this.
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)

            assert(mockView.artboardRenderer != null)
            mockView.play("one", Loop.PINGPONG)
            val originalPointer = mockView.controller.activeArtboard?.cppPointer
            mockView.reset()
            assertNotEquals(mockView.controller.activeArtboard?.cppPointer, originalPointer)
            assert(!mockView.isPlaying)
        }
    }

    @Test
    fun viewResetTwice() {
        // This used to crash with a null pointer dereference.
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            mockView.reset()
            mockView.reset()
        }
    }

    @Test
    fun viewResetAutoplay() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = true)
            assertEquals(true, mockView.isPlaying)
            assert(mockView.artboardRenderer != null)
            val originalPointer = mockView.controller.activeArtboard?.cppPointer
            mockView.reset()
            assertNotEquals(mockView.controller.activeArtboard?.cppPointer, originalPointer)
            assertEquals(true, mockView.isPlaying)
        }
    }

    @Test
    fun customAssetLoader() {
        UiThreadStatement.runOnUiThread {
            val assetStore = mutableListOf<FileAsset>()
            val customLoader = object : FileAssetLoader() {
                override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
                    return assetStore.add(asset)
                }
            }
            mockView.setAssetLoader(customLoader)
            assert(assetStore.isEmpty()) // Before loading.
            mockView.setRiveResource(R.raw.walle, autoplay = true)
            assertEquals(2, assetStore.size) // All loaded.
        }
    }

    @Test
    fun swapFilesStopsAdvancing() {
        UiThreadStatement.runOnUiThread {
            // Use Noop Renderer so it doesn't advance right away.
            mockView = TestUtils.MockNoopRiveAnimationView(appContext)
            mockView.setRiveResource(R.raw.state_machine_configurations)
            mockView.fireState("trigger_input", "Trigger 1")
            // Inputs need processing and have been scheduled
            assertTrue(mockView.controller.isAdvancing)

            // Set a new file
            mockView.setRiveResource(R.raw.multiple_animations, autoplay = false)
            // Cleaned everything up after setting a new file.
            assertFalse(mockView.controller.isAdvancing)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class TouchPassThroughComposeTest {
    companion object {
        const val BUTTON_TAG = "test_button"
    }

    @Before
    fun setup() {
        Rive.init(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
    }

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * When touch‑pass‑through is disabled (the default), the overlaid RiveAnimationView should
     * consume the tap and the button underneath should not increment.
     */
    @Test
    fun buttonNotClickedWhenPassThroughOff() {
        composeRule.setContent {
            TouchPassThroughDemo(passThrough = false)
        }

        // Also add the wait condition here for robustness.
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(BUTTON_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        // Base case
        composeRule.onNodeWithText("Click Count: 0").assertExists()

        // Tap the button - Rive view is on top
        composeRule.onNodeWithTag(BUTTON_TAG).performTouchInput { click() }

        // The UI should not change, so we can just assert.
        // To be extra safe against recomposition delays, you could add a small delay
        // or a wait condition that checks the count remains 0.
        composeRule.mainClock.advanceTimeBy(200) // Advance compose clock to allow potential changes
        composeRule.onNodeWithText("Click Count: 0").assertExists()
    }

    /**
     * When touch‑pass‑through is enabled, taps should reach the button and increment its counter.
     */
    @Test
    fun buttonClickedWhenPassThroughOn() {
        composeRule.setContent {
            TouchPassThroughDemo(passThrough = true)
        }

        // Add this wait condition before any interaction.
        // This will wait up to a few seconds for the button to appear.
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag(BUTTON_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        // Now that we know the button exists, we can safely interact with it.
        composeRule.onNodeWithTag(BUTTON_TAG).performTouchInput { click() }

        // It's also good practice to wait for the expected outcome.
        composeRule.waitUntil(timeoutMillis = 1000) {
            composeRule.onAllNodesWithText("Click Count: 1").fetchSemanticsNodes().isNotEmpty()
        }
        // Final assertion for clarity, though the waitUntil above already confirms it.
        composeRule.onNodeWithText("Click Count: 1").assertExists()
    }


    // Local composable for these tests
    @Composable
    private fun TouchPassThroughDemo(passThrough: Boolean) {
        var clickCount by remember { mutableStateOf(0) }

        Box(modifier = Modifier.fillMaxSize()) {
            Button(
                onClick = { clickCount++ },
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    disabledElevation = 0.dp,
                ),
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.Center)
                    .testTag(BUTTON_TAG)
            ) {
                Text("Click Count: $clickCount")
            }

            AndroidView(
                factory = { context ->
                    RiveAnimationView(context).apply {
                        setRiveResource(R.raw.touchpassthrough, fit = Fit.FILL)
                        touchPassThrough = passThrough
                    }
                },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

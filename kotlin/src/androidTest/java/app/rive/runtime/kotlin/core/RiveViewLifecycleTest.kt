package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.ResourceType
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.errors.ArtboardException
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveViewLifecycleTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var mockView: RiveAnimationView

    @Before
    fun initView() {
        mockView = TestUtils.MockRiveAnimationView(appContext, false)
    }

    @Test
    fun viewRendererInit() {
        UiThreadStatement.runOnUiThread {
            // No renderer yet.
            assertNull(mockView.artboardRenderer)
            // Renderer is created in onAttachedToWindow().
            (mockView as TestUtils.MockRiveAnimationView).mockAttach()
            assertNotNull(mockView.artboardRenderer)
            // Remove renderer on detach.
            (mockView as TestUtils.MockRiveAnimationView).mockDetach()
            assertNull(mockView.artboardRenderer)

        }
    }

    @Test
    fun viewDefaultRenderAttributes() {
        UiThreadStatement.runOnUiThread {
            val attributes = mockView.rendererAttributes

            // Check defaults have been set.
            assertEquals(
                attributes.alignment,
                Alignment.fromIndex(RiveAnimationView.alignmentIndexDefault)
            )
            assertEquals(attributes.fit, Fit.fromIndex(RiveAnimationView.fitIndexDefault))
            assertEquals(attributes.loop, Loop.fromIndex(RiveAnimationView.loopIndexDefault))
            assertEquals(attributes.autoplay, mockView.defaultAutoplay)

            assertFalse(attributes.riveTraceAnimations)
            assertNull(attributes.artboardName)
            assertNull(attributes.animationName)
            assertNull(attributes.stateMachineName)
            assertNull(attributes.resource)
            assertNull(mockView.artboardRenderer)
        }
    }

    @Test
    fun viewLoadResourceBeforeAttach() {
        UiThreadStatement.runOnUiThread {
            val attributes = mockView.rendererAttributes
            val resourceId = R.raw.multipleartboards

            assertNull(mockView.artboardRenderer)
            assertNull(attributes.resource)
            assertTrue(attributes.autoplay)

            mockView.setRiveResource(
                resourceId,
                autoplay = false,
                fit = Fit.FIT_HEIGHT,
                alignment = Alignment.TOP_LEFT,
                loop = Loop.ONESHOT,
            )

            assert(attributes.resource is ResourceType.ResourceId)
            assertEquals(resourceId, (attributes.resource as ResourceType.ResourceId).id)
            assertFalse(attributes.autoplay)
            assertEquals(attributes.fit, Fit.FIT_HEIGHT)
            assertEquals(attributes.alignment, Alignment.TOP_LEFT)
            assertEquals(attributes.loop, Loop.ONESHOT)

            mockView.play("artboard2animation1")
            // Cannot play without a renderer.
            assertFalse(mockView.isPlaying)
        }
    }


    @Test
    fun viewLoadResourceAndThenAttach() {
        UiThreadStatement.runOnUiThread {
            val attributes = mockView.rendererAttributes
            val resourceId = R.raw.multipleartboards

            assertNull(mockView.artboardRenderer)
            assertNull(attributes.resource)
            assertTrue(attributes.autoplay)

            mockView.setRiveResource(
                resourceId,
                fit = Fit.FIT_HEIGHT,
                alignment = Alignment.TOP_LEFT,
                loop = Loop.ONESHOT,
            )

            assertTrue(attributes.resource is ResourceType.ResourceId)
            assertTrue(attributes.autoplay)
            assertEquals(resourceId, (attributes.resource as ResourceType.ResourceId).id)
            assertEquals(attributes.fit, Fit.FIT_HEIGHT)
            assertEquals(attributes.alignment, Alignment.TOP_LEFT)
            assertEquals(attributes.loop, Loop.ONESHOT)

            mockView.play("artboard2animation1")
            // Cannot play without a renderer.
            assertFalse(mockView.isPlaying)
            (mockView as TestUtils.MockRiveAnimationView).mockAttach()
            // View is set to `autoplay` so it starts playing after attach.
            assertTrue(mockView.isPlaying)
            assertEquals(
                mockView.animations.map { it.name }.toList(),
                listOf("artboard2animation1"),
            )
            assertEquals(attributes.fit, Fit.FIT_HEIGHT)
            assertEquals(attributes.alignment, Alignment.TOP_LEFT)
            // Loop has been reset by `play()`.
            assertEquals(attributes.loop, Loop.AUTO)
        }
    }

    @Test
    fun viewLoadBytesAndThenAttach() {
        UiThreadStatement.runOnUiThread {
            val attributes = mockView.rendererAttributes
            val resourceId = R.raw.multipleartboards

            assertNull(mockView.artboardRenderer)
            assertNull(attributes.resource)
            assertTrue(attributes.autoplay)

            val stream = mockView.resources.openRawResource(resourceId)
            mockView.setRiveBytes(
                stream.readBytes(),
                fit = Fit.FIT_HEIGHT,
                alignment = Alignment.TOP_LEFT,
                loop = Loop.ONESHOT,
            )
            stream.close()

            assertTrue(attributes.resource is ResourceType.ResourceBytes)
            assertTrue(attributes.autoplay)
            assertEquals(attributes.fit, Fit.FIT_HEIGHT)
            assertEquals(attributes.alignment, Alignment.TOP_LEFT)
            assertEquals(attributes.loop, Loop.ONESHOT)

            mockView.play("artboard2animation1")
            // Cannot play without a renderer.
            assertFalse(mockView.isPlaying)
            (mockView as TestUtils.MockRiveAnimationView).mockAttach()
            // View is set to `autoplay` so it starts playing after attach.
            assertTrue(mockView.isPlaying)
            assertEquals(
                mockView.animations.map { it.name }.toList(),
                listOf("artboard2animation1"),
            )
        }
    }

    @Test(expected = NullPointerException::class)
    fun viewGetMissingRenderer() {
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.multipleartboards)
            assertNull(mockView.artboardRenderer)
            mockView.artboardName = "artboard1"
            mockView.artboardName // 💥 renderer doesn't exist
        }
    }
}
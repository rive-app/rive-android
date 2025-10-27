package app.rive.runtime.compatibilitytest

import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.renderers.Renderer
import app.rive.runtime.kotlin.renderers.RiveArtboardRenderer

class OnDrawRenderer(
    private val onDraw: (() -> Unit)?,
    controller: RiveFileController,
    rendererType: RendererType
) :
    RiveArtboardRenderer(trace = false, controller = controller, rendererType = rendererType) {
    override fun draw() {
        super.draw()
        onDraw?.invoke() // Frame drawn
    }
}

class CallbackRiveAnimationView(
    builder: Builder
) : RiveAnimationView(builder) {
    var drawCallback: (() -> Unit)? = null

    override fun createRenderer(): Renderer {
        return OnDrawRenderer(
            onDraw = drawCallback,
            controller = controller,
            rendererType = rendererAttributes.rendererType,
        )
    }
}

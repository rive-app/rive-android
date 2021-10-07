package app.rive.runtime.kotlin.renderers

enum class RendererType {
    CANVAS {
        override fun make(antialias: Boolean): Renderer {
            return Renderer(antialias)
        }
    },
    OPENGL {
        override fun make(antialias: Boolean): RendererOpenGL {
            return RendererOpenGL()
        }
    },
    SKIA {
        override fun make(antialias: Boolean): RendererSkia {
            return RendererSkia()
        }
    };

    abstract fun make(antialias: Boolean = true): BaseRenderer
}

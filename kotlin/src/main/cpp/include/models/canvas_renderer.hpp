#pragma once

#include <algorithm>
#include <jni.h>
#include <vector>

#include "helpers/general.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/rive_log.hpp"
#include "jni_refs.hpp"
#include "rive/renderer.hpp"

namespace rive_android
{
class CanvasRenderer : public rive::Renderer
{
protected:
    jobject m_ktCanvas = nullptr;
    int m_width = -1;
    int m_height = -1;
    std::vector<float> m_opacityStack{1.0f};

private:
    static constexpr auto* TAG = "RiveLN/CanvasRenderer";
    bool m_reportedSurfaceFailure = false;

    /**
     * Clears a pending Surface exception, logging only the first failure until
     * a Canvas is successfully posted. Called only on the renderer worker.
     *
     * @param message Diagnostic identifying the failed Surface operation.
     * @return Whether an exception was cleared.
     */
    bool clearSurfaceException(const char* message);

    /**
     * Clears the currently locked Android Canvas.
     *
     * @param ktCanvas Android Canvas to clear.
     * @return `true` on success; `false` if a JNI exception was cleared.
     */
    static bool Clear(jobject ktCanvas);

public:
    ~CanvasRenderer() override { assert(m_ktCanvas == nullptr); }
    void save() override;
    void restore() override;
    void transform(const rive::Mat2D& transform) override;
    void clipPath(rive::RenderPath* path) override;
    void drawPath(rive::RenderPath* path, rive::RenderPaint* paint) override;
    void drawImage(const rive::RenderImage*,
                   rive::ImageSampler options,
                   rive::BlendMode,
                   float opacity) override;
    void drawImageMesh(const rive::RenderImage*,
                       rive::ImageSampler options,
                       rive::rcp<rive::RenderBuffer> vertices_f32,
                       rive::rcp<rive::RenderBuffer> uvCoords_f32,
                       rive::rcp<rive::RenderBuffer> indices_u16,
                       uint32_t vertexCount,
                       uint32_t indexCount,
                       rive::BlendMode,
                       float opacity) override;
    void modulateOpacity(float opacity) override
    {
        m_opacityStack.back() *= opacity;
    }

    [[nodiscard]] float currentOpacity() const
    {
        return std::max(0.0f, m_opacityStack.back());
    }
    [[nodiscard]] int width() const { return m_width; }
    [[nodiscard]] int height() const { return m_height; }

    /**
     * Locks, measures, and clears the Canvas owned by an Android Surface.
     *
     * Expected Surface lifecycle exceptions are logged and cleared so they
     * abort only the current frame and do not detach the JNI worker thread.
     *
     * @param ktSurface Android Surface whose Canvas should be locked.
     * @return `true` when the Canvas is ready to draw; `false` when the frame
     *         must be aborted.
     */
    bool bindCanvas(jobject ktSurface);

    /**
     * Posts the locked Canvas and always releases its JNI global reference.
     *
     * Expected Surface lifecycle exceptions are logged and cleared so they
     * abort only the current frame and do not detach the JNI worker thread.
     *
     * @param ktSurface Android Surface that owns the locked Canvas.
     * @return `true` when the Canvas was posted; `false` when the frame was
     *         aborted.
     */
    bool unlockAndPost(jobject ktSurface);
};
} // namespace rive_android

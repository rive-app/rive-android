//
// Created by Umberto Sonnino on 6/27/23.
//
#ifndef RIVE_ANDROID_THREAD_STATE_SKIA_HPP
#define RIVE_ANDROID_THREAD_STATE_SKIA_HPP

#include "thread_state_egl.hpp"

#include "GrDirectContext.h"
#include "SkSurface.h"
#include "SkCanvas.h"
#include "SkColorSpace.h"

namespace rive_android
{
class SkiaThreadState : public EGLThreadState
{
public:
    SkiaThreadState() = default;
    ~SkiaThreadState();

    sk_sp<SkSurface> createSkiaSurface(EGLSurface, int width, int height);

    void destroySurface(EGLSurface) override;

    void makeCurrent(EGLSurface) override;

private:
    sk_sp<GrDirectContext> m_skContext = nullptr;
};
} // namespace rive_android

#endif // RIVE_ANDROID_THREAD_STATE_SKIA_HPP

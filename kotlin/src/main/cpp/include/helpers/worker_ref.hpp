#pragma once

#include "helpers/thread_state_egl.hpp"
#include "helpers/worker_thread.hpp"
#include "rive/refcnt.hpp"

namespace rive_android
{
class RefWorker : public WorkerThread
{
public:
  // Returns the current worker of the requested renderer type, or the current
  // Canvas worker if the requested type is not supported.
  static rive::rcp<RefWorker> CurrentOrFallback(RendererType);

  /**
   * Sets the legacy OpenGL restriction before any files or renderers are
   * created. Repeated runtime initialization must supply the same device
   * policy.
   * @param disabled Whether a known driver issue requires Canvas fallback.
   */
  static void SetOpenGLDisabled(bool disabled);

  /**
   * Returns the Rive renderer worker without probing GL on restricted devices.
   * @return The shared Rive worker, or null when unsupported or prohibited.
   */
  static rive::rcp<RefWorker> RiveWorker();

  // Returns the current Canvas renderer worker.
  static rive::rcp<RefWorker> CanvasWorker();

#ifdef DEBUG
  // Testing: makes RiveWorker() return null, as on devices without Rive
  // renderer support, until called again with false. This is process-wide:
  // objects created for the Rive renderer must not create GPU resources
  // while it is on.
  static void SimulateUnsupportedRiveRenderer(bool simulate);
#endif

  ~RefWorker() override;

  // These methods work with rive::rcp<> for tracking _external_ references.
  // They don't necessarily delete this object when the external ref count
  // goes to zero, and they may have other side effects as well.
  void ref();
  void unref();

  static const char* RendererName(RendererType rendererType)
  {
    switch (rendererType)
    {
      case RendererType::None:
        assert(false);
        return "";
      case RendererType::Canvas:
        return "Canvas";
      case RendererType::Rive:
        return "Rive";
    }
  }

private:
  explicit RefWorker(const RendererType rendererType)
      : WorkerThread(RendererName(rendererType), Affinity::None, rendererType)
  {}

  void externalRefCountDidReachZero();

  size_t m_externalRefCount = 0;
};
} // namespace rive_android

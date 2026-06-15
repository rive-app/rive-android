#pragma once

#include <android/api-level.h>
#include <dlfcn.h>

#include "helpers/general.hpp"
#include "helpers/rive_log.hpp"

namespace rive_android
{
/**
 * @brief Abstract interface for trace section instrumentation.
 */
class ITracer
{
public:
    virtual ~ITracer() = default;
    /** Begin a named trace section. */
    virtual void beginSection(const char* sectionName) const = 0;
    /** End the most recently opened trace section. */
    virtual void endSection() const = 0;
};

/**
 * @brief Tracer implementation that performs no instrumentation.
 */
class NoopTracer : public ITracer
{
public:
    void beginSection(const char* sectionName) const override {};
    void endSection() const override {};
};

/**
 * @brief Process-wide no-op tracer singleton.
 */
inline const NoopTracer& noopTracer()
{
    static const NoopTracer tracer;
    return tracer;
}

/**
 * @brief Android tracing implementation backed by libandroid ATrace symbols.
 *
 * Android's dedicated tracing APIs are only guaranteed on newer API levels,
 * while this runtime supports minSdk 21. To avoid hard-linking on unsupported
 * devices, this implementation resolves ATrace symbols dynamically at runtime.
 *
 * Symbol lookup is performed once per process and reused for all Tracer
 * instances. If minSdk is raised to 23 or higher, this can be simplified to
 * direct calls through <android/trace.h>.
 */
class Tracer : public ITracer
{
public:
    void beginSection(const char* sectionName) const override
    {
        static constexpr auto* kInvalidSectionName =
            "Rive/InvalidTraceSectionName";
        if (sectionName == nullptr)
        {
            RiveLogE("Tracer", "beginSection called with null section name");
            sectionName = kInvalidSectionName;
        }
        const auto& traceApi = api();
        if (traceApi.beginSection != nullptr)
        {
            traceApi.beginSection(sectionName);
        }
    }
    void endSection() const override
    {
        const auto& traceApi = api();
        if (traceApi.endSection != nullptr)
        {
            traceApi.endSection();
        }
    }

private:
    using fp_ATrace_beginSection = void (*)(const char* sectionName);
    using fp_ATrace_endSection = void (*)();

    struct TraceAPI
    {
        fp_ATrace_beginSection beginSection = nullptr;
        fp_ATrace_endSection endSection = nullptr;
    };

    static TraceAPI loadAPI()
    {
        TraceAPI result;
        if (android_get_device_api_level() < 23)
        {
            // ATrace APIs are not guaranteed for our current minSdk 21.
            return result;
        }

        // Keep this handle alive for process lifetime so resolved symbols stay
        // valid.
        void* lib = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
        if (lib == nullptr)
        {
            RiveLogE("Tracer", "Tracer cannot load libandroid.so!");
            return result;
        }

        result.beginSection = reinterpret_cast<fp_ATrace_beginSection>(
            dlsym(lib, "ATrace_beginSection"));
        result.endSection = reinterpret_cast<fp_ATrace_endSection>(
            dlsym(lib, "ATrace_endSection"));
        if (result.beginSection == nullptr || result.endSection == nullptr)
        {
            RiveLogE("Tracer", "Tracer cannot resolve ATrace symbols.");
            result.beginSection = nullptr;
            result.endSection = nullptr;
        }
        return result;
    }

    static const TraceAPI& api()
    {
        static const TraceAPI traceApi = loadAPI();
        return traceApi;
    }
};

/**
 * @brief Process-wide tracer singleton.
 *
 * On unsupported API levels or when ATrace symbols cannot be resolved, this
 * tracer effectively behaves as a no-op.
 */
inline const Tracer& defaultTracer()
{
    static const Tracer tracer;
    return tracer;
}

/**
 * @brief RAII helper for virtual ("V") tracer dispatch that opens a trace
 * section on construction and closes it on destruction.
 *
 * @warning The caller must ensure the passed tracer outlives this scope
 * object. Normally this isn't an issue as we use static globals.
 * @see TraceScope
 */
class VTraceScope
{
public:
    /**
     * @param tracer Tracer used to emit begin/end calls.
     * @param sectionName Name of the section to begin.
     */
    VTraceScope(const ITracer& tracer, const char* sectionName) :
        m_tracer(tracer)
    {
        m_tracer.beginSection(sectionName);
    }

    ~VTraceScope() { m_tracer.endSection(); }

    // Non-copyable to avoid duplicate endSection calls for the same scope.
    VTraceScope(const VTraceScope&) = delete;
    VTraceScope& operator=(const VTraceScope&) = delete;

private:
    const ITracer& m_tracer;
};

/**
 * @brief RAII helper for concrete tracer types in hot paths that opens a trace
 * section on construction and closes it on destruction.
 *
 * Unlike VTraceScope, this is templated on the concrete tracer type,
 * allowing calls to beginSection/endSection to be resolved without virtual
 * dispatch when used with concrete tracers.
 *
 * @tparam TracerType Concrete tracer type that provides
 * beginSection/endSection.
 * @warning The caller must ensure the passed tracer outlives this scope
 * object. Normally this isn't an issue as we use static globals.
 * @see VTraceScope
 */
template <typename TracerType> class TraceScope
{
public:
    TraceScope(const TracerType& tracer, const char* sectionName) :
        m_tracer(tracer)
    {
        m_tracer.beginSection(sectionName);
    }

    ~TraceScope() { m_tracer.endSection(); }

    TraceScope(const TraceScope&) = delete;
    TraceScope& operator=(const TraceScope&) = delete;

private:
    const TracerType& m_tracer;
};

} // namespace rive_android

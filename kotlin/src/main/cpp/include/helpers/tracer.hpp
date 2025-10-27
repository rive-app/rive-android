#pragma once

#include <android/api-level.h>
#include <dlfcn.h>

#include "helpers/general.hpp"

namespace rive_android
{
/**
 * Interface for a generic Tracer
 */
class ITracer
{
public:
    ITracer() = default;
    virtual ~ITracer() = default;
    virtual void beginSection(const char* sectionName) = 0;
    virtual void endSection() = 0;
};

class NoopTracer : public ITracer
{
public:
    NoopTracer() = default;
    ~NoopTracer() override = default;
    void beginSection(const char* sectionName) override {};
    void endSection() override {};
};

class Tracer : public ITracer
{
public:
    Tracer()
    {
        void* lib = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
        if (lib != nullptr)
        {
            ATrace_beginSection = reinterpret_cast<fp_ATrace_beginSection>(
                dlsym(lib, "ATrace_beginSection"));
            ATrace_endSection = reinterpret_cast<fp_ATrace_endSection>(
                dlsym(lib, "ATrace_endSection"));
        }
        else
        {
            LOGE("Tracer cannot load libandroid.so!");
        }
    }
    ~Tracer() override = default;
    void beginSection(const char* sectionName) override
    {
        ATrace_beginSection(sectionName);
    };
    void endSection() override { ATrace_endSection(); };

private:
    void* (*ATrace_beginSection)(const char* sectionName);
    void* (*ATrace_endSection)();

    typedef void* (*fp_ATrace_beginSection)(const char* sectionName);
    typedef void* (*fp_ATrace_endSection)();
};

} // namespace rive_android

-- This premake5 script builds the Rive C++ Runtime and its dependencies.
-- Android builds (--for_android) are invoked by CMakeLists.txt in this
-- directory; desktop JVM builds invoke it without --for_android.

-- RIVE_RUNTIME_DIR should be provided via the environment by CMake

local path = require('path')
local rive_runtime_dir = os.getenv('RIVE_RUNTIME_DIR')

-- Build the Rive Renderer
dofile(path.join(rive_runtime_dir, 'renderer/premake5_pls_renderer.lua'))
-- Build the Rive C++ Runtime
dofile(path.join(rive_runtime_dir, 'premake5_v2.lua'))

local libs = {
    'rive',
    'rive_pls_renderer',
    'rive_harfbuzz',
    'rive_sheenbidi',
    'rive_yoga',
    'miniaudio',
    'luau_vm',
}

-- Android decodes images through android.graphics (--no-rive-decoders);
-- desktop builds use Rive's built-in decoders instead.
if not _OPTIONS['for_android'] and not _OPTIONS['no-rive-decoders'] then
    dofile(path.join(rive_runtime_dir, 'decoders/premake5_v2.lua'))
    for _, lib in ipairs({ 'rive_decoders', 'libpng', 'libjpeg', 'libwebp', 'zlib' }) do
        table.insert(libs, lib)
    end
end

-- Consolidate all the libraries into one location.
-- We don't actually link against librive_cpp_runtime.a, but we use it to build the others.
project('rive_cpp_runtime')
do
    kind('StaticLib')
    links(libs)
end

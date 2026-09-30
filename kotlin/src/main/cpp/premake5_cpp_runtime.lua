-- This premake5 script builds the Rive C++ Runtime and its dependencies for Android.
-- It is invoked by CMakeLists.txt in the same directory.

-- RIVE_RUNTIME_DIR should be provided via the environment by CMake

local path = require('path')
local rive_runtime_dir = os.getenv('RIVE_RUNTIME_DIR')

-- Build the Rive Renderer
dofile(path.join(rive_runtime_dir, 'renderer/premake5_pls_renderer.lua'))
-- Build the Rive C++ Runtime
dofile(path.join(rive_runtime_dir, 'premake5_v2.lua'))

-- Keep debug information for the release symbols artifact without changing optimization or LTO.
-- Apply this at workspace scope so the core, renderer, and static dependencies all inherit it.
workspace('rive')
filter('options:config=release')
    symbols('On')
    -- CMake resolves the root for both monorepo and standalone Android checkouts.
    buildoptions({ '-ffile-prefix-map="' .. os.getenv('RIVE_SOURCE_ROOT') .. '"=.' })
filter({})

-- Consolidate all the libraries used by Android into one location.
-- We don't actually link against librive_cpp_runtime.a, but we use it to build the others.
project('rive_cpp_runtime')
do
    kind('StaticLib')
    links({
        'rive',
        'rive_pls_renderer',
        'rive_harfbuzz',
        'rive_sheenbidi',
        'rive_yoga',
        'miniaudio',
        'luau_vm',
    })
end

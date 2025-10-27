-- premake5_pls_renderer.lua is either in the public submodule:
if not pcall(dofile, '../../../../submodules/rive-runtime/renderer/premake5_pls_renderer.lua') then
    -- Or in the monorepo:
    dofile('../../../../../runtime/renderer/premake5_pls_renderer.lua')
end

dofile(RIVE_RUNTIME_DIR .. '/premake5_v2.lua')

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
    })
end

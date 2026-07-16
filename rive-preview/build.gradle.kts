// Resources-only artifact carrying the desktop Rive native libraries.
// Consumers add it as debugImplementation so Android Studio previews (layoutlib,
// which runs the Android classpath on a host JVM) can load and render Rive.
plugins {
    `java-library`
}

val hostArch = System.getProperty("os.arch").let {
    if (it == "aarch64" || it == "arm64") "aarch64" else "x86_64"
}
val kotlinProject = project(":kotlin")
val moltenVkPath = (findProperty("rive.moltenvk") as String?)
    ?: "/opt/homebrew/lib/libMoltenVK.dylib"

tasks.processResources {
    dependsOn(kotlinProject.tasks.named("buildDesktopNative"))
    from(kotlinProject.layout.buildDirectory.file("rive-native-desktop/cmake/librive-jvm.dylib")) {
        into("rive-native/macos-$hostArch")
    }
    from(moltenVkPath) {
        into("rive-native/macos-$hostArch")
    }
}

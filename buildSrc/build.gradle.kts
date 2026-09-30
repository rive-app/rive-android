plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(localGroovy())
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    // Pass the fixture toolchain explicitly rather than depending on the test worker's environment.
    val ndk = providers.environmentVariable("ANDROID_NDK_HOME")
    inputs.property("fixtureNdk", ndk.orElse(""))
    systemProperty("fixtureNdk", ndk.orElse("").get())
}

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}
kotlin { jvmToolchain(17) }
// Android Studio and desktop builds can run concurrently in separate Gradle roots.
layout.buildDirectory.set(layout.projectDirectory.dir("build/${rootProject.name}"))
dependencies {
    api("com.squareup.okhttp3:okhttp:5.5.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    compileOnly("org.json:json:20260814")
    testImplementation("org.json:json:20260814")
    testImplementation("junit:junit:4.13.2")
}

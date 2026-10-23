import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.json:json:20260814")
    testImplementation("junit:junit:4.13.2")
    testImplementation(compose.desktop.uiTestJUnit4)
}
sourceSets.test { resources { srcDir("../tests/fixtures"); include("portrait-looks.json") } }
tasks.processTestResources {
    filesMatching("portrait-looks.json") { name = "portrait-looks-fixtures.json" }
}
sourceSets.main {
    resources.srcDir("../android/app/src/main/assets")
}
compose.desktop {
    application {
        mainClass = "ai.wckd.niobium.desktop.MainKt"
        nativeDistributions {
            appResourcesRootDir.set(layout.projectDirectory.dir("runtime/bundle"))
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Niobium"
            packageVersion = "2.1.0"
            description = "Your Pixels, everywhere. Hermes and local DSH."
            vendor = "WCKD"
            copyright = "© 2026 WCKD"
            includeAllModules = true
            macOS { bundleID = "ai.wckd.niobium.desktop"; iconFile.set(project.file("src/main/resources/niobium.icns")) }
            linux { iconFile.set(project.file("src/main/resources/niobium.png")); packageName = "niobium"; appCategory = "Utility" }
        }
    }
}

val prepareDesktopRuntime = tasks.register<Exec>("prepareDesktopRuntime") {
    workingDir = rootProject.projectDir.parentFile
    commandLine("python3", "tools/prepare-desktop-runtime.py")
    inputs.files("runtime/package.json", "runtime/package-lock.json", "runtime/desktop-host.cjs",
        "../config/desktop-runtime.json", "../tools/prepare-desktop-runtime.py", "../runtime/niobium-agent.cjs",
        "../runtime/niobium-messaging.cjs", "../android/runtime/niobium-local.cjs", "../LICENSE", "../NOTICE")
    outputs.dir("runtime/bundle")
}
tasks.matching { it.name in setOf("prepareAppResources", "createDistributable") }.configureEach { dependsOn(prepareDesktopRuntime) }

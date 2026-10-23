import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}
val niobiumDesktopVersion = "2.1.0"
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
            packageVersion = niobiumDesktopVersion
            description = "Your Pixels, everywhere. Hermes and local DSH."
            vendor = "WCKD"
            copyright = "© 2026 WCKD"
            includeAllModules = true
            macOS { bundleID = "ai.wckd.niobium.desktop"; minimumSystemVersion = "13.0"; iconFile.set(project.file("src/main/resources/niobium.icns")) }
            linux { iconFile.set(project.file("src/main/resources/niobium.png")); packageName = "niobium"; appCategory = "Utility" }
        }
    }
}

val desktopRuntimeInputs = files("runtime/package.json", "runtime/package-lock.json", "runtime/desktop-host.cjs",
    "../config/desktop-runtime.json", "../tools/prepare-desktop-runtime.py", "../runtime/niobium-agent.cjs",
    "../runtime/niobium-messaging.cjs", "../android/runtime/niobium-local.cjs", "../LICENSE", "../NOTICE")
val prepareDesktopRuntime = tasks.register<Exec>("prepareDesktopRuntime") {
    workingDir = rootProject.projectDir.parentFile
    commandLine("python3", "tools/prepare-desktop-runtime.py")
    inputs.files(desktopRuntimeInputs)
    outputs.dir("runtime/bundle")
}
tasks.matching { it.name in setOf("prepareAppResources", "createDistributable", "packageDmg", "packageDeb") }.configureEach {
    dependsOn(prepareDesktopRuntime)
    inputs.files(desktopRuntimeInputs)
}
tasks.test { dependsOn(prepareDesktopRuntime); inputs.files(desktopRuntimeInputs) }

// Share authored familiar sprites with Android instead of maintaining copies.
tasks.processResources {
    from("../android/app/src/main/res/drawable-nodpi") { include("comp_*.png") }
}

// jpackage strips executable bits from additional app resources on macOS.
// Restore the exact pinned bundle modes before creating the installer.
tasks.matching { it.name == "createDistributable" }.configureEach {
    doLast {
        val image = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile.toPath()
        val manifests = Files.walk(image).use { paths -> paths.filter { it.fileName.toString() == "runtime-manifest.json" }.toList() }
        check(manifests.size == 1) { "Expected one packaged private runtime." }
        val manifest = manifests.single()
        val resources = manifest.parent.toRealPath()
        val metadata = JsonSlurper().parse(manifest.toFile()) as Map<*, *>
        val executables = metadata["executables"] as Map<*, *>
        for ((relative, value) in executables) {
            val target = resources.resolve(relative.toString()).toRealPath()
            check(target.startsWith(resources)) { "Runtime executable leaves its package." }
            val mode = (value as Number).toInt()
            val permissions = PosixFilePermission.values().filterIndexed { index, _ -> mode and (1 shl (8 - index)) != 0 }.toSet()
            Files.setPosixFilePermissions(target, permissions)
        }
    }
}
// The Linux DEB bundler normalizes resource modes again when building data.tar.
tasks.matching { it.name == "packageDeb" }.configureEach {
    inputs.file("../tools/restore-desktop-deb-modes.py")
    doLast {
        val tool = projectDir.parentFile.resolve("tools/restore-desktop-deb-modes.py")
        val directory = layout.buildDirectory.dir("compose/binaries/main/deb").get().asFile
        val process = ProcessBuilder("python3", tool.absolutePath, directory.absolutePath, "niobium_${niobiumDesktopVersion}-")
            .directory(projectDir.parentFile).inheritIO().start()
        check(process.waitFor() == 0) { "Could not restore native desktop DEB permissions." }
    }
}

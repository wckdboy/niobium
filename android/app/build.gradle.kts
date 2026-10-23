import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.sovware.niobium"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "io.sovware.niobium"
        minSdk = 28
        targetSdk = 37
        versionCode = 13
        versionName = "2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.isFile) {
                val props = Properties().apply { FileInputStream(propsFile).use(::load) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        jniLibs.keepDebugSymbols += "**/libniobium_*.so"
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            "DebugProbesKt.bin"
        )
    }
    sourceSets["main"].assets.directories += layout.buildDirectory.dir("generated/runtime/assets").get().asFile.path
    sourceSets["androidTest"].assets.directories += layout.buildDirectory.dir("generated/gateway-test-assets").get().asFile.path
    sourceSets["main"].jniLibs.directories += layout.buildDirectory.dir("generated/runtime/jniLibs").get().asFile.path
    androidResources.noCompress += "image"
}

val prepareLocalRuntime = tasks.register<Exec>("prepareLocalRuntime") {
    workingDir = rootProject.projectDir.parentFile
    commandLine("python3", "tools/prepare-android-runtime.py")
    inputs.files("../../config/android-runtime.json", "../../tools/prepare-android-runtime.py",
        "../runtime/supervisor.c", "../runtime/niobium-local.cjs", "../../runtime/niobium-agent.cjs", "../../runtime/niobium-messaging.cjs")
    outputs.dir(layout.buildDirectory.dir("generated/runtime"))
}
val prepareGatewayTestAsset = tasks.register<Copy>("prepareGatewayTestAsset") {
    from(rootProject.file("../tests/fixtures/gateway.mjs"))
    into(layout.buildDirectory.dir("generated/gateway-test-assets"))
}
tasks.named("preBuild").configure { dependsOn(prepareLocalRuntime, prepareGatewayTestAsset) }

dependencies {
    implementation(project(":core"))
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    // Compose's transitive Espresso 3.5 uses an InputManager API removed in Android 16.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}

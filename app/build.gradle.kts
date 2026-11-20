import com.assayflux.pockethost.build.GeneratePocketHostLauncherIcons

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val rustDir = rootProject.layout.projectDirectory.dir("rust-engine")
val relayRustDir = rootProject.layout.projectDirectory.dir("relay-node")
val relayJniOutput = layout.buildDirectory.dir("generated/relay/jniLibs")
val generatedBrandResources = layout.buildDirectory.dir("generated/brandResources")
val generatePocketHostLauncherIcons by tasks.registering(GeneratePocketHostLauncherIcons::class) {
    sourceSvg.set(rootProject.layout.projectDirectory.file("Assets/Pocket Host 7.svg"))
    outputDirectory.set(generatedBrandResources)
}
val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val requestedAbi = providers.gradleProperty("pockethostAbi").orNull
val selectedAbis = if (requestedAbi == null) supportedAbis else {
    require(requestedAbi in supportedAbis) { "Unsupported PocketHost ABI: $requestedAbi" }
    listOf(requestedAbi)
}
val releaseKeystorePath = System.getenv("POCKETHOST_KEYSTORE_FILE")
val releaseStorePassword = System.getenv("POCKETHOST_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("POCKETHOST_KEY_ALIAS")
val releaseKeyPassword = System.getenv("POCKETHOST_KEY_PASSWORD")
val releaseSigningReady = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }
val buildRustAndroid by tasks.registering(Exec::class) {
    workingDir = rustDir.asFile
    commandLine(listOf("cargo", "ndk") + selectedAbis.flatMap { listOf("-t", it) } + listOf(
        "-o", layout.buildDirectory.dir("generated/rust/jniLibs").get().asFile.absolutePath,
        "build", "--release", "--locked"
    ))
    inputs.dir(rustDir)
    outputs.dir(layout.buildDirectory.dir("generated/rust/jniLibs"))
}

val buildNativeRelayAndroid by tasks.registering(Exec::class) {
    workingDir = relayRustDir.asFile
    commandLine(listOf("cargo", "ndk") + selectedAbis.flatMap { listOf("-t", it) } + listOf(
        "-o", relayJniOutput.get().asFile.absolutePath,
        "build", "--release", "--locked"
    ))
    inputs.dir(relayRustDir)
    outputs.dir(relayJniOutput)
}
android {
    namespace = "com.assayflux.pockethost"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.assayflux.pockethost"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.clear()
            abiFilters.addAll(selectedAbis)
        }
    }
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/rust/jniLibs"))
    sourceSets["main"].jniLibs.srcDir(relayJniOutput)
    sourceSets["main"].res.srcDir(generatedBrandResources)
    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
        }
    }
    packaging {
        jniLibs {
            // cloudflared is an Android PIE executable packaged beside native libraries;
            // extraction gives ProcessBuilder a real executable path on API 26+.
            useLegacyPackaging = true
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
tasks.named("preBuild").configure { dependsOn(buildRustAndroid, buildNativeRelayAndroid, generatePocketHostLauncherIcons) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}


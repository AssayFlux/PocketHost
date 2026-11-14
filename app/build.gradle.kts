plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val rustDir = rootProject.layout.projectDirectory.dir("rust-engine")
val releaseKeystorePath = System.getenv("POCKETHOST_KEYSTORE_FILE")
val releaseStorePassword = System.getenv("POCKETHOST_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("POCKETHOST_KEY_ALIAS")
val releaseKeyPassword = System.getenv("POCKETHOST_KEY_PASSWORD")
val releaseSigningReady = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }
val buildRustAndroid by tasks.registering(Exec::class) {
    workingDir = rustDir.asFile
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-t", "armeabi-v7a", "-o",
        layout.buildDirectory.dir("generated/rust/jniLibs").get().asFile.absolutePath, "build", "--release")
    inputs.dir(rustDir)
    outputs.dir(layout.buildDirectory.dir("generated/rust/jniLibs"))
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
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/rust/jniLibs"))
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
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
tasks.named("preBuild").configure { dependsOn(buildRustAndroid) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

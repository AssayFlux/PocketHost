plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

val releaseKeystorePath = System.getenv("POCKETHOST_KEYSTORE_FILE")
val releaseStorePassword = System.getenv("POCKETHOST_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("POCKETHOST_KEY_ALIAS")
val releaseKeyPassword = System.getenv("POCKETHOST_KEY_PASSWORD")
val releaseSigningReady = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.assayflux.pockethost.worker"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.assayflux.pockethost.worker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
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
    kotlinOptions { jvmTarget = "17" }
}

chaquopy {
    defaultConfig {
        version = "3.11"
    }
}

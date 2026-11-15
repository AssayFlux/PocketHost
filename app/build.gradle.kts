import java.security.MessageDigest
import java.net.URL
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}
val rustDir = rootProject.layout.projectDirectory.dir("rust-engine")
val nodeRuntimeDir = layout.buildDirectory.dir("generated/node-runtime")
val prepareNodeRuntime by tasks.registering {
    val output = nodeRuntimeDir
    outputs.dir(output)
    doLast {
        val root = output.get().asFile
        val downloadDir = layout.buildDirectory.dir("downloads").get().asFile
        downloadDir.mkdirs()
        val archive = File(downloadDir, "nodejs-mobile-android-24.22.1-0.zip")
        val expectedSha256 = "d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250"
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        val actualBefore = if (archive.isFile) sha256(archive) else ""
        if (actualBefore != expectedSha256) {
            archive.delete()
            val connection = URL("https://github.com/fogtape/nodejs-mobile/releases/download/v24.22.1-0/nodejs-mobile-android-24.22.1-0.zip")
                .openConnection().apply { connectTimeout = 30_000; readTimeout = 180_000 }
            connection.getInputStream().use { input ->
                archive.outputStream().buffered().use { target -> input.copyTo(target) }
            }
        }
        val actualSha256 = sha256(archive)
        check(actualSha256 == expectedSha256) { "Node.js mobile SDK SHA-256 mismatch." }
        root.deleteRecursively()
        val jniRoot = File(root, "jniLibs")
        val includeRoot = File(root, "include")
        val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                val abi = supportedAbis.firstOrNull { name.endsWith("bin/$it/libnode.so") }
                if (abi != null) {
                    val destination = File(jniRoot, "$abi/libnode.so")
                    destination.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> destination.outputStream().use { input.copyTo(it) } }
                    continue
                }
                val includeIndex = name.indexOf("include/")
                if (includeIndex >= 0) {
                    val relative = name.substring(includeIndex + "include/".length)
                    val destination = File(includeRoot, relative).canonicalFile
                    check(destination.toPath().startsWith(includeRoot.canonicalFile.toPath())) { "Unsafe Node SDK archive path." }
                    destination.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> destination.outputStream().use { input.copyTo(it) } }
                }
            }
        }
        supportedAbis.forEach { abi ->
            check(File(jniRoot, "$abi/libnode.so").isFile) { "Node.js SDK is missing libnode.so for $abi." }
        }
        check(File(includeRoot, "node/node.h").isFile) { "Node.js SDK is missing node.h." }
    }
}
val releaseKeystorePath = System.getenv("POCKETHOST_KEYSTORE_FILE")
val releaseStorePassword = System.getenv("POCKETHOST_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("POCKETHOST_KEY_ALIAS")
val releaseKeyPassword = System.getenv("POCKETHOST_KEY_PASSWORD")
val releaseSigningReady = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }
val buildRustAndroid by tasks.registering(Exec::class) {
    workingDir = rustDir.asFile
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86_64", "-o",
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments += listOf("-DNODE_SDK_DIR=" + nodeRuntimeDir.get().asFile.absolutePath, "-DANDROID_STL=c++_shared")
                cppFlags += listOf("-std=c++17")
            }
        }
    }
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/rust/jniLibs"))
    sourceSets["main"].jniLibs.srcDir(nodeRuntimeDir.map { it.dir("jniLibs") })
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
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
tasks.named("preBuild").configure { dependsOn(buildRustAndroid, prepareNodeRuntime) }
tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) {
        dependsOn(prepareNodeRuntime)
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

chaquopy {
    defaultConfig {
        // Python 3.11 is the newest Chaquopy line retaining 32-bit ARM support.
        version = "3.11"
    }
}

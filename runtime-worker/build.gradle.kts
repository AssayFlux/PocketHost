import java.security.MessageDigest
import java.net.URL
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

val nodeRuntimeDir = layout.buildDirectory.dir("generated/node-runtime")
val prepareNodeRuntime by tasks.registering {
    val output = nodeRuntimeDir
    outputs.dir(output)
    doLast {
        val root = output.get().asFile
        val downloadDir = File(gradle.gradleUserHomeDir, "caches/pockethost-node-runtime")
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
        if (!archive.isFile || sha256(archive) != expectedSha256) {
            archive.delete()
            URL("https://github.com/fogtape/nodejs-mobile/releases/download/v24.22.1-0/nodejs-mobile-android-24.22.1-0.zip")
                .openConnection().apply { connectTimeout = 30_000; readTimeout = 180_000 }
                .getInputStream().use { input ->
                    archive.outputStream().buffered().use { target -> input.copyTo(target) }
                }
        }
        check(sha256(archive) == expectedSha256) { "Node.js mobile SDK SHA-256 mismatch." }
        root.deleteRecursively()
        val jniRoot = File(root, "jniLibs")
        val includeRoot = File(root, "include")
        val assetRoot = File(root, "assets")
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                val lowerName = name.lowercase()
                if (lowerName.split('/').any { it in setOf("licenses", "license") } ||
                    lowerName.substringAfterLast('/').let { it.startsWith("license") || it.startsWith("notice") || it.startsWith("copying") }) {
                    val relativeLicense = name.substringAfter('/', name)
                    val destination = File(assetRoot, "licenses/nodejs-mobile/" + relativeLicense).canonicalFile
                    check(destination.toPath().startsWith(assetRoot.canonicalFile.toPath())) { "Unsafe Node license archive path." }
                    destination.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> destination.outputStream().use { input.copyTo(it) } }
                }
                val abi = abis.firstOrNull { name.endsWith("bin/$it/libnode.so") }
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
        abis.forEach { abi ->
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

android {
    namespace = "com.assayflux.pockethost.worker"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.assayflux.pockethost.worker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64")) }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DNODE_SDK_DIR=" + nodeRuntimeDir.get().asFile.absolutePath, "-DANDROID_STL=c++_shared")
                cppFlags += listOf("-std=c++20")
            }
        }
    }
    sourceSets["main"].jniLibs.srcDir(nodeRuntimeDir.map { it.dir("jniLibs") })
    sourceSets["main"].assets.srcDir(nodeRuntimeDir.map { it.dir("assets") })
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
        pip {
            install("Flask==3.1.1")
        }
    }
}

tasks.named("preBuild").configure { dependsOn(prepareNodeRuntime) }
tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) {
        dependsOn(prepareNodeRuntime)
    }
}

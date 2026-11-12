import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.jorge.flacbox.audio"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}

// --------------------------------------------------------------- Rust

val cargoHome = "${System.getenv("HOME")}/.cargo"
val rustDir = rootProject.layout.projectDirectory.dir("rust")
val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")
val cargoProfile = (findProperty("cargoProfile") as String?) ?: "release"
val ndkVersionFromSdk = "27.2.12479018"
val sdkRoot = System.getenv("ANDROID_HOME") ?: "${System.getenv("HOME")}/Android/Sdk"

val cargoBuild by tasks.registering(Exec::class) {
    group = "rust"
    description = "Compila libflacplayer.so para arm64-v8a y x86_64"

    workingDir = rustDir.asFile
    environment("PATH", "$cargoHome/bin:${System.getenv("PATH")}")
    environment("ANDROID_HOME", sdkRoot)
    environment(
        "ANDROID_NDK_HOME",
        System.getenv("ANDROID_NDK_HOME") ?: "$sdkRoot/ndk/$ndkVersionFromSdk"
    )

    val args = mutableListOf(
        "$cargoHome/bin/cargo",
        "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", jniLibsDir.asFile.absolutePath,
        "build", "--lib"
    )
    if (cargoProfile == "release") args.add("--release")
    commandLine(args)

    inputs.dir(rustDir.dir("src"))
    inputs.file(rustDir.file("Cargo.toml"))
    outputs.dir(jniLibsDir)

    doFirst {
        if (!File("$cargoHome/bin/cargo-ndk").exists()) {
            throw GradleException(
                "cargo-ndk no está instalado. Ejecuta: cargo install cargo-ndk"
            )
        }
    }

    doLast {
        // libflacplayer.so depende de libc++_shared.so (oboe es C++)
        val ndkRoot = System.getenv("ANDROID_NDK_HOME") ?: "$sdkRoot/ndk/$ndkVersionFromSdk"
        listOf(
            "arm64-v8a" to "aarch64-linux-android",
            "x86_64" to "x86_64-linux-android"
        ).forEach { (abi, triple) ->
            val from = File(
                "$ndkRoot/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$triple/libc++_shared.so"
            )
            from.copyTo(
                File(jniLibsDir.asFile, "$abi/libc++_shared.so"),
                overwrite = true
            )
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(cargoBuild)
}

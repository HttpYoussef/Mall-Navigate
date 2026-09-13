plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Firebase
    id("com.google.gms.google-services")
}

android {

    namespace = "com.example.mallar"

    compileSdk = 36

    defaultConfig {

        applicationId = "com.example.mallar"

        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        debug {

            isPseudoLocalesEnabled = true
        }

        release {

            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    kotlinOptions {

        jvmTarget = "11"
    }

    buildFeatures {

        compose = true
    }
    androidResources {
        noCompress += listOf("tflite")
    }

    lint {
        error += "MissingTranslation"
    }

    // TFLite
}

dependencies {

    // Firebase Auth
    implementation(
        "com.google.firebase:firebase-auth:22.3.1"
    )

    // CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.core.splashscreen)

    implementation("io.github.sceneview:arsceneview:2.2.1")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Gson
    implementation("com.google.code.gson:gson:2.11.0")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    implementation(libs.guava)
    implementation(libs.androidx.concurrent.futures)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
}

tasks.register("checkThemeColors") {
    group = "verification"
    description = "Scans Kotlin UI files for un-tokenized color literals and invalid alpha usages."

    doLast {
        val uiDir = file("src/main/java/com/example/mallar/ui")
        val voiceFile = file("src/main/java/com/example/mallar/voice/VoiceAssistantOverlay.kt")

        val inScopeFiles = mutableListOf<File>()
        if (uiDir.exists()) {
            uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                val relToUi = f.relativeTo(uiDir).path.replace('\\', '/')
                if (!relToUi.startsWith("theme/") && relToUi != "theme") {
                    inScopeFiles.add(f)
                }
            }
        }
        if (voiceFile.exists()) {
            inScopeFiles.add(voiceFile)
        }

        val patterns = listOf(
            Regex("""Color\(0x"""),
            Regex("""Color\(\s*red\s*="""),
            Regex("""Color\.(White|Black|Gray|LightGray|DarkGray|Red|Green|Blue|Cyan|Magenta|Yellow)\b"""),
            Regex("""\.copy\(\s*alpha\s*="""),
            Regex("""@color/"""),
            Regex("""R\.color\.""")
        )
        val allowMarker = Regex("""//\s*theme-lint:allow.*$""")

        val errors = mutableListOf<String>()

        for (file in inScopeFiles) {
            val relPath = rootProject.projectDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
            val lines = file.readLines()
            var fileMatchCount = 0
            val offendingLines = mutableListOf<String>()

            lines.forEachIndexed { index, line ->
                val lineNum = index + 1
                if (!allowMarker.containsMatchIn(line)) {
                    var lineMatches = 0
                    for (pattern in patterns) {
                        if (pattern.pattern == """\.copy\(\s*alpha\s*=""" && line.contains("scrim")) {
                            continue
                        }
                        lineMatches += pattern.findAll(line).count()
                    }
                    if (lineMatches > 0) {
                        fileMatchCount += lineMatches
                        offendingLines.add("  $relPath:$lineNum: (matches=$lineMatches) ${line.trim()}")
                    }
                }
            }

            if (fileMatchCount > 0) {
                errors.add("File $relPath has $fileMatchCount banned matches:\n" + offendingLines.joinToString("\n"))
            }
        }

        if (errors.isNotEmpty()) {
            throw GradleException(
                "checkThemeColors failed with ${errors.size} violating file(s):\n\n" +
                        errors.joinToString("\n\n")
            )
        } else {
            println("checkThemeColors: All in-scope files passed cleanly.")
        }
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("checkThemeColors")
}
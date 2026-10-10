import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val ciVersionCode: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2
val ciVersionName: String = System.getenv("GIT_TAG")
    ?: runCatching {
        val process = ProcessBuilder("git", "describe", "--tags", "--always").redirectErrorStream(true).start()
        val tag = process.inputStream.bufferedReader().readLine()?.trim()
        process.waitFor()
        if (!tag.isNullOrEmpty() && !tag.contains("fatal")) tag else null
    }.getOrNull() ?: "0.2.0"

android {
    namespace = "com.attract.attendance"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.attract.attendance"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    androidResources {
        noCompress += "tflite"
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
            }

            val storeFilePath = System.getenv("KEYSTORE_FILE")
                ?: keystoreProperties.getProperty("storeFile")
            val storePass = System.getenv("KEYSTORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword")
            val kAlias = System.getenv("KEY_ALIAS")
                ?: keystoreProperties.getProperty("keyAlias")
            val kPass = System.getenv("KEY_PASSWORD")
                ?: keystoreProperties.getProperty("keyPassword")

            if (!storeFilePath.isNullOrEmpty() && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = kAlias
                keyPassword = kPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // debug settings
        }
    }

    bundle {
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
        language {
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "META-INF/DEPENDENCIES"
        resources.excludes += "META-INF/LICENSE*"
        resources.excludes += "META-INF/NOTICE*"
        resources.excludes += "META-INF/INDEX.LIST"
    }

    sourceSets {
        getByName("test") {
            java.srcDirs("src/test/java", "src/sharedTest/java")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/java", "src/sharedTest/java")
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    // CameraX 1.4.2 — pinned to remain compatible with compileSdk 35 / AGP 8.8.2.
    // CameraX 1.6.1+ requires compileSdk 36 and AGP 8.9.1+.
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    // LiteRT (formerly TFLite) >= 1.4.0 — REQUIRED for physical devices with 16KB kernel page sizes
    // (Android 15+ hardware).
    implementation("com.google.ai.edge.litert:litert:1.0.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.api-client:google-api-client-android:2.7.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20241027-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("copyApkToWorkspace") {
    doNotTrackState("Direct copy to root")
    doLast {
        val src = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val dst = rootProject.file("app-debug.apk")
        if (src.exists()) {
            src.copyTo(dst, overwrite = true)
        }
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("copyApkToWorkspace")
}

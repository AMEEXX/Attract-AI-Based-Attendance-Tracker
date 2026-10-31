plugins {
    id("com.android.application") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

// Redirect build outputs to outside OneDrive only on local Windows environments
// to prevent file-locking issues with OneDrive sync, while keeping standard build
// directories in CI environments and non-Windows systems.
val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
val isCi = !System.getenv("CI").isNullOrBlank() || !System.getenv("GITHUB_ACTIONS").isNullOrBlank()

if (isWindows && !isCi) {
    allprojects {
        layout.buildDirectory.set(file("C:/tmp/attract-build/${project.name}"))
    }
}


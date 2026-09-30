plugins {
    id("com.android.application") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// Redirect build outputs to outside OneDrive to prevent file-locking issues
// during connected Android tests (OneDrive sync locks files in the project tree).
allprojects {
    layout.buildDirectory.set(file("C:/tmp/attract-build/${project.name}"))
}

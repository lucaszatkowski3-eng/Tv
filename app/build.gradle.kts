plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "de.screenos.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.screenos.tv"
        minSdk = 23
        targetSdk = 28
        versionCode = 3
        versionName = "0.3.0"
    }
}
dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
}

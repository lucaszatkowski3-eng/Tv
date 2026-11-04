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
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { buildConfig = true }
}

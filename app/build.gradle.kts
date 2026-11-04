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
        versionCode = 2
        versionName = "0.2.0"
    }
}
dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}

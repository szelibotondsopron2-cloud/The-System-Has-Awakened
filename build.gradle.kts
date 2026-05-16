plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.calis10x.system"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.calis10x.system"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-system-ui"
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.4")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.code.gson:gson:2.11.0")
}

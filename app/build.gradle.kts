
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "gr.laouto.dromoi"
    compileSdk = 35

    defaultConfig {
        applicationId = "gr.laouto.dromoi"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

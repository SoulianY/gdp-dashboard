plugins {
    id("com.android.application")
}

android {
    namespace = "com.souliany.notilog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.souliany.notilog"
        minSdk = 29
        targetSdk = 37
        versionCode = 8
        versionName = "0.8"
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
}

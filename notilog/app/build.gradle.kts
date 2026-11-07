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
        versionCode = 15
        versionName = "1.0"
    }

    signingConfigs {
        // 폰에 설치된 앱과 같은 키로 서명해야 지우지 않고 덮어 설치된다.
        // 키는 비공개 저장소 push-inbox의 signing/에 있다(이 저장소 옆에 클론되어 있다고 본다).
        create("release") {
            storeFile = rootProject.file(
                providers.gradleProperty("alarmlog.keystore").getOrElse("../../push-inbox/signing/alarmlog.keystore"),
            )
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
}

// ./gradlew dist -> dist/AlarmLog_<버전>.apk
tasks.register<Copy>("dist") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "AlarmLog_${android.defaultConfig.versionName}.apk" }
}

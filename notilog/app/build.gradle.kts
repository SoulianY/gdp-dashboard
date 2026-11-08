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
        versionCode = 20
        versionName = "1.5"
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("io.coil-kt.coil3:coil:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.work:work-testing:2.12.0")
}

// ./gradlew dist -> dist/AlarmLog_<버전>.apk
tasks.register<Copy>("dist") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "AlarmLog_${android.defaultConfig.versionName}.apk" }
}

// 화면 캡처 테스트: Robolectric이 안드로이드 jar를 직접 받지 못하는 환경에서는 -Probolectric.deps=<폴더>로 미리 받은 jar를 쓴다.
tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
    providers.gradleProperty("robolectric.deps").orNull?.let {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", it)
    }
}

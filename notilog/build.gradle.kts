plugins {
    id("com.android.application") version "9.5.1" apply false
    // AGP 내장 Kotlin(2.2)보다 새 버전: Coil 3.6이 Kotlin 2.4로 빌드됨
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}

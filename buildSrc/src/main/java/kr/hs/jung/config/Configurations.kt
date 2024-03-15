package kr.hs.jung.config

import org.gradle.api.JavaVersion

object Configurations {
    const val artifactGroup = "kr.hs.jung"

    const val compileSdk = 34
    const val targetSdk = 34
    const val minSdk = 26
    const val versioncode = 1
    private const val majorVersion = 1
    private const val minorVersion = 1
    private const val patchVersion = 1
    const val versionName = "$majorVersion.$minorVersion.$patchVersion"

    const val jvmTarget = "11"
    val sourceCompatibility = JavaVersion.VERSION_11
    val targetCompatibility = JavaVersion.VERSION_11

}

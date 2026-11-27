/*
 * Copyright (C) 2023 Xizhi Zhu
 * Copyright (C) 2026 Daniel Collingwood
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.ide.common.vectordrawable.Svg2Vector
import java.io.ByteArrayOutputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("androidx.baselineprofile")
}

// Launcher label: -PappName, else "USFM Bible".
val appName = (findProperty("appName") as String?)?.takeIf { it.isNotBlank() } ?: "USFM Bible"
val defaultBookIndex = (findProperty("defaultBookIndex") as String?)?.toIntOrNull() ?: 0
require(defaultBookIndex in 0 until 66) { "defaultBookIndex must be a valid 0-based Bible book index" }

android {
    namespace = Configurations.namespace

    compileOptions {
        sourceCompatibility = Versions.java
        targetCompatibility = Versions.java
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(Versions.Kotlin.jvmTarget))
        }
    }

    buildToolsVersion = Versions.Sdk.buildTools
    compileSdk = Versions.Sdk.compile

    defaultConfig {
        applicationId = Configurations.applicationId

        minSdk = Versions.Sdk.min
        targetSdk = Versions.Sdk.target

        // CI passes the release tag (e.g. -PappVersion=0.21.0); local builds fall back to Versions.App.
        val appVersion = (findProperty("appVersion") as String?)?.removePrefix("v")
        val appVersionParts = appVersion?.split(".")?.map { it.toInt() }
        versionCode = appVersionParts?.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch } ?: Versions.App.code
        versionName = appVersion ?: Versions.App.name

        androidResources.localeFilters.addAll(Configurations.supportedLocales)
        manifestPlaceholders["appName"] = appName
        buildConfigField("int", "DEFAULT_BOOK_INDEX", defaultBookIndex.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // F-Droid forbids the signing-block dependency metadata that AGP embeds by default.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            java.setSrcDirs(setOf("src/main/kotlin"))
            // Optional: directory with bundled/list.tsv (id<TAB>name<TAB>language) and bundled/translations/<id>.zip (USFM) to prepackage translations.
            (findProperty("bundledAssetsDir") as String?)?.let { assets.srcDir(it) }
        }
        getByName("debug") { java.setSrcDirs(setOf("src/debug/kotlin")) }
        getByName("release") { java.setSrcDirs(setOf("src/release/kotlin")) }
        getByName("test") { java.setSrcDirs(setOf("src/test/kotlin")) }
        getByName("androidTest") {
            java.setSrcDirs(setOf("src/androidTest/kotlin"))
            // Optional: directory with real USFM books (e.g. HAG.usfm from the full WEB) for the instrumented tests.
            (findProperty("testAssetsDir") as String?)?.let { assets.srcDir(it) }
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(keystorePropertiesFile.inputStream())

                storeFile = File(rootDir, keystoreProperties.getProperty("KEYSTORE_FILE"))
                storePassword = keystoreProperties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Without a keystore, fall back to the debug key so the APK is still installable.
            signingConfig = if (rootProject.file("keystore.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = " debug"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true

        unitTests.all { test ->
            test.maxParallelForks = Runtime.getRuntime().availableProcessors() / 2
            test.jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow", "-Xshare:off")
        }
    }

    packaging {
        resources.excludes.add("**/NOTNOTICE")
        jniLibs.excludes.add("**/libshared.so")
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(Dependencies.Kotlin.coroutines)
    implementation(Dependencies.AndroidX.activity)
    implementation(Dependencies.AndroidX.annotation)
    implementation(Dependencies.AndroidX.appCompat)
    implementation(Dependencies.AndroidX.core)
    implementation(Dependencies.AndroidX.profileInstaller)
    implementation(Dependencies.AndroidX.Fragment.core)
    implementation(Dependencies.AndroidX.Lifecycle.common)
    implementation(Dependencies.AndroidX.Lifecycle.runtime)
    implementation(Dependencies.AndroidX.Lifecycle.viewModel)
    implementation(Dependencies.AndroidX.View.coordinatorLayout)
    implementation(Dependencies.AndroidX.View.drawerLayout)
    implementation(Dependencies.AndroidX.View.recyclerView)
    implementation(Dependencies.AndroidX.View.swipeRefreshLayout)
    implementation(Dependencies.AndroidX.View.viewPager2)
    implementation(Dependencies.AndroidX.profileInstaller)


    implementation(Dependencies.kermit)
    implementation(Dependencies.materialComponent)

    testImplementation(Dependencies.Kotlin.test)
    testImplementation(Dependencies.Kotlin.coroutinesTest)
    testImplementation(Dependencies.Kotlin.reflect)
    debugImplementation(Dependencies.AndroidX.Fragment.test)
    testImplementation(Dependencies.AndroidX.Test.core)
    testImplementation(Dependencies.AndroidX.Test.junit)
    testImplementation(Dependencies.mockk)
    testImplementation(Dependencies.robolectric)

    androidTestImplementation(Dependencies.Kotlin.test)
    androidTestImplementation(Dependencies.AndroidX.Test.core)
    androidTestImplementation(Dependencies.AndroidX.Test.junit)
    androidTestImplementation(Dependencies.AndroidX.Test.runner)

    baselineProfile(project(":baselineprofile"))
}

// Regenerates the API 24+ launcher foreground from arts/ic_launcher.svg with the Android Gradle plugin's own Svg2Vector.
tasks.register("generateLauncherIcon") {
    val svg = rootProject.file("arts/ic_launcher.svg")
    val output = file("src/main/res/drawable-v24/ic_launcher_foreground.xml")
    inputs.file(svg)
    outputs.file(output)
    doLast {
        val xml = ByteArrayOutputStream()
        val errors = Svg2Vector.parseSvgToXml(svg.toPath(), xml)
        check(errors.isEmpty()) { errors }
        output.writeText(
            xml.toString(Charsets.UTF_8)
                .replace(Regex("""android:(width|height)="512dp""""), "android:$1=\"108dp\"")
                .replace("?>", "?>\n<!-- Generated by ./gradlew :app:generateLauncherIcon from arts/ic_launcher.svg. API 23 lacks VectorDrawable gradients, so drawable/ic_launcher_foreground.webp is the fallback. -->")
        )
    }
}

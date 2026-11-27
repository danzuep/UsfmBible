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

plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "${Configurations.namespace}.baselineprofile"
    compileSdk = Versions.Sdk.compile

    compileOptions {
        sourceCompatibility = Versions.java
        targetCompatibility = Versions.java
    }

    defaultConfig {
        // Baseline profile collection needs API 28+ (the generator runs on a rooted or API 33+ device or emulator).
        minSdk = 28
        targetSdk = Versions.Sdk.target
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // CI generates on an emulator; macrobenchmark refuses to run on one otherwise.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
    }

    targetProjectPath = ":app"
}

// Use a connected device or emulator; add a managed device here to generate on CI without a physical device.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(Dependencies.AndroidX.Test.junit)
    implementation(Dependencies.AndroidX.uiAutomator)
    implementation(Dependencies.AndroidX.benchmarkMacro)
}

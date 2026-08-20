plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.sayertv.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sayertv.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "0.1.0-alpha24"
    }

    signingConfigs {
        getByName("debug") {
            // Committed debug-only keystore → consistent signature across build
            // machines/sessions, so alpha testers can update in place.
            storeFile = rootProject.file("signing/seedtv-debug.keystore")
            storePassword = "sayertv-debug"
            keyAlias = "sayertvdebug"
            keyPassword = "sayertv-debug"
        }
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (System.getenv("GITHUB_ACTIONS") == "true") {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            ndk { abiFilters += "arm64-v8a" }  // sideload testing: modern phones; release builds all ABIs
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:jellyfin"))
    implementation(project(":core:playback"))
    implementation(project(":core:anilist"))
    implementation(project(":core:matching"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:library"))
    implementation(project(":feature:player"))
    implementation(project(":feature:syncplay"))
    implementation(project(":feature:anilist"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Adopt Kotlin's future annotation-target default now (silences the
        // 16x "applied to value parameter only" future-compat warnings).
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

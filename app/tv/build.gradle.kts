plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val findroidTvDebug =
    findroidTvVariant("debug", Versions.APP_NAME, Versions.APP_CODE, Versions.ATV_RELEASE_REVISION)
val findroidTvRelease =
    findroidTvVariant(
        "release",
        Versions.APP_NAME,
        Versions.APP_CODE,
        Versions.ATV_RELEASE_REVISION,
    )
val findroidTvBaseApplicationId = findroidTvDebug.applicationId.removeSuffix(".debug")
val findroidTvUniversalApk =
    providers.gradleProperty("findroidTvUniversalApk").map(String::toBoolean).orElse(false)
val findroidTvSigningEnvironment =
    listOf(
            "FINDROID_TV_KEYSTORE_FILE",
            "FINDROID_TV_KEYSTORE_PASSWORD",
            "FINDROID_TV_KEY_ALIAS",
            "FINDROID_TV_KEY_PASSWORD",
        )
        .associateWith { providers.environmentVariable(it).orNull }
val findroidTvMissingSigningInputs =
    findroidTvSigningEnvironment.filterValues { it.isNullOrBlank() }.keys

android {
    namespace = "dev.jdtech.jellyfin"
    compileSdk = Versions.COMPILE_SDK
    buildToolsVersion = Versions.BUILD_TOOLS

    defaultConfig {
        applicationId = findroidTvBaseApplicationId
        minSdk = Versions.MIN_SDK
        targetSdk = Versions.TARGET_SDK

        versionCode = Versions.APP_CODE
        versionName = Versions.APP_NAME
    }

    buildTypes {
        named("debug") {
            applicationIdSuffix =
                findroidTvDebug.applicationId.removePrefix(findroidTvBaseApplicationId)
        }
        named("release") {
            applicationIdSuffix =
                findroidTvRelease.applicationId.removePrefix(findroidTvBaseApplicationId)
            resValue("string", "app_name", findroidTvRelease.label)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            if (findroidTvMissingSigningInputs.isEmpty()) {
                signingConfig =
                    signingConfigs.create("findroidTvRelease") {
                        storeFile =
                            file(
                                findroidTvSigningEnvironment.getValue("FINDROID_TV_KEYSTORE_FILE")!!
                            )
                        storePassword =
                            findroidTvSigningEnvironment.getValue("FINDROID_TV_KEYSTORE_PASSWORD")
                        keyAlias = findroidTvSigningEnvironment.getValue("FINDROID_TV_KEY_ALIAS")
                        keyPassword =
                            findroidTvSigningEnvironment.getValue("FINDROID_TV_KEY_PASSWORD")
                    }
            }
        }
        register("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
        }
    }

    flavorDimensions += "variant"
    productFlavors {
        register("libre") {
            dimension = "variant"
            isDefault = true
        }
    }

    splits {
        abi {
            // Detect app bundle and conditionally disable split abis
            // This is needed due to a "Multiple shrunk-resources files found in directory" error
            // present since AGP 8.9.0, for more info see:
            // https://issuetracker.google.com/issues/402800800
            isEnable =
                findroidTvAbiSplitsEnabled(
                    gradle.startParameter.taskNames,
                    findroidTvUniversalApk.get(),
                )

            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = Versions.JAVA
        targetCompatibility = Versions.JAVA
    }

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.versionName.set(findroidTvRelease.versionName)
            output.versionCode.set(findroidTvRelease.versionCode)
        }
    }
}

gradle.taskGraph.whenReady {
    val releaseArtifactRequested = allTasks.any { task ->
        task.project == project && findroidTvReleaseArtifactRequested(task.name)
    }
    if (releaseArtifactRequested && findroidTvMissingSigningInputs.isNotEmpty()) {
        error(
            "Findroid TV release signing requires environment variables: " +
                findroidTvMissingSigningInputs.sorted().joinToString(", ")
        )
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.data)
    implementation(projects.setup)
    implementation(projects.modes.film)
    implementation(projects.player.core)
    implementation(projects.player.local)
    implementation(projects.settings)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.network.cache.control)
    implementation(libs.coil.svg)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.jellyfin.core)
    ksp(libs.kotlin.metadata.jvm)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    coreLibraryDesugaring(libs.android.desugar.jdk)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------
// Versioning
//   versionCode  = GitHub Actions run number, so every CI build is installable
//                  over the previous one. Local builds stay at 1.
//   versionName  = the git tag that triggered the release (v1.2.0 -> 1.2.0),
//                  or "<base>-dev" locally.
// ---------------------------------------------------------------------------
val baseVersionName = "1.0.0"
val ciRunNumber: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "").toIntOrNull() ?: 1
val gitRefName: String? = System.getenv("GITHUB_REF_NAME")
val resolvedVersionName: String = when {
    gitRefName != null && gitRefName.startsWith("v") -> gitRefName.removePrefix("v")
    else -> "$baseVersionName-dev"
}

// Release signing. Locally read from keystore.properties (gitignored); in CI the
// workflow decodes the keystore and exports these as environment variables.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "KEY_PASSWORD")
val canSignRelease = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() } && file(releaseStoreFile!!).exists()

android {
    namespace = "ca.gpsprobuild.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ca.gpsprobuild.app"
        minSdk = 26
        targetSdk = 35
        versionCode = ciRunNumber
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Consumed by the in-app update check (Settings -> Check for updates).
        buildConfigField("String", "GITHUB_OWNER", "\"${project.findProperty("githubOwner") ?: "tfitzgerald"}\"")
        buildConfigField("String", "GITHUB_REPO", "\"${project.findProperty("githubRepo") ?: "gps-probuild"}\"")
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Falls back to unsigned if secrets are absent, so a fork can still build.
            signingConfig = if (canSignRelease) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*"
        )
    }

    // Name artifacts so a release page reads clearly.
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "GPSProBuild-${versionName}-${name}.apk"
        }
    }
}

// Room schema export — committed to app/schemas so migrations can be tested.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

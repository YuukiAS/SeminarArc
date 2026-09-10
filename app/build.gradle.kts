plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

import java.util.Properties

val seminarArcVersionCode = providers.gradleProperty("seminarArc.versionCode")
    .map { it.toInt() }
    .orElse(50002)
    .get()
val seminarArcVersionName = providers.gradleProperty("seminarArc.versionName")
    .orElse("0.5.0-alpha.2")
    .get()
val defaultInternalSigningPropertiesPath = if (
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
) {
    "D:\\Code\\_secrets\\SeminarArc\\internal-signing.properties"
} else {
    ""
}
val internalSigningPropertiesPath = providers.environmentVariable("SEMINARARC_INTERNAL_SIGNING_PROPERTIES")
    .orElse(defaultInternalSigningPropertiesPath)
    .get()
val internalSigningPropertiesFile = internalSigningPropertiesPath
    .takeIf { it.isNotBlank() }
    ?.let { file(it) }
val internalSigningProperties = Properties().apply {
    if (internalSigningPropertiesFile?.exists() == true) {
        internalSigningPropertiesFile.inputStream().use(::load)
    }
}
val hasInternalSigningConfig = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { internalSigningProperties.getProperty(it).isNullOrBlank().not() }

android {
    namespace = "com.yuukias.seminararc"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.yuukias.seminararc"
        minSdk = 26
        targetSdk = 36
        versionCode = seminarArcVersionCode
        versionName = seminarArcVersionName

        testInstrumentationRunner = "com.yuukias.seminararc.SeminarArcTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasInternalSigningConfig) {
            create("internal") {
                storeFile = file(internalSigningProperties.getProperty("storeFile"))
                storePassword = internalSigningProperties.getProperty("storePassword")
                keyAlias = internalSigningProperties.getProperty("keyAlias")
                keyPassword = internalSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("internal") {
            initWith(getByName("release"))
            applicationIdSuffix = ".internal"
            matchingFallbacks += listOf("release", "debug")
            if (hasInternalSigningConfig) {
                signingConfig = signingConfigs.getByName("internal")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.camerax.core)
    implementation(libs.androidx.camerax.camera2)
    implementation(libs.androidx.camerax.lifecycle)
    implementation(libs.androidx.camerax.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

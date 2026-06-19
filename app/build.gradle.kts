import java.io.File
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.miaclean.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.miaclean.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        resourceConfigurations += setOf("en", "pt-rBR", "es")

        buildConfigField("String", "BILLING_SKU_MONTHLY", "\"pro_monthly\"")
        buildConfigField("String", "BILLING_SKU_YEARLY", "\"pro_yearly\"")
        buildConfigField("String", "BILLING_SKU_LIFETIME", "\"pro_lifetime\"")
        buildConfigField("int", "FREE_DELETES_PER_MONTH", "50")
        buildConfigField("String", "BILLING_PUBLIC_KEY", "\"\"")
        buildConfigField("String", "BILLING_BACKEND_URL", "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn", "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    implementation(libs.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.mlkit.text.recognition)

    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.phashcalc)
    implementation(libs.billing.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}

kapt {
    correctErrorTypes = true
}

val faceModelUrl = "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite"
val generatedAssetsDir = layout.buildDirectory.dir("generated/mediapipeAssets")

val downloadFaceDetectorModel by tasks.registering {
    val outputDir = generatedAssetsDir
    outputs.dir(outputDir)
    doLast {
        val target = outputDir.get().file("face_detector.tflite").asFile
        if (target.exists() && target.length() > 0) return@doLast
        target.parentFile.mkdirs()
        val tmp = File(target.parentFile, "face_detector.tflite.part")
        try {
            URI(faceModelUrl).toURL().openStream().use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
            if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
        } catch (t: Throwable) {
            if (tmp.exists()) tmp.delete()
            if (target.exists()) target.delete()
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(generatedAssetsDir)
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach { dependsOn(downloadFaceDetectorModel) }

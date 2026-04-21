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

        // Play Billing configuration. These are placeholder SKU ids: the real subscription /
        // one-time products must be created in Play Console with these exact ids (or the ids
        // below overridden to match). Until the ids exist on the backend,
        // `queryProductDetails` returns an empty list and the paywall renders its "billing
        // unavailable" state — no crash, no purchase flow.
        buildConfigField("String", "BILLING_SKU_MONTHLY", "\"pro_monthly\"")
        buildConfigField("String", "BILLING_SKU_YEARLY", "\"pro_yearly\"")
        buildConfigField("String", "BILLING_SKU_LIFETIME", "\"pro_lifetime\"")
        // Base-64 RSA public key from Play Console > Monetize > License testing. Used to verify
        // purchase signatures client-side. When empty (debug / pre-release builds) signature
        // verification is skipped with a warning log; callers should NOT rely on verification in
        // this state. Release builds with an empty key should fail fast at the repository layer
        // rather than silently trusting unverified purchases.
        buildConfigField("String", "BILLING_PUBLIC_KEY", "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
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
        generateLocaleConfig = false
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
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

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}

kapt {
    correctErrorTypes = true
}

// Downloads the MediaPipe Face Detector model into a generated assets directory on first build.
// The detector no-ops gracefully when the asset is missing (see SelfieDetector), so CI without
// internet still builds; it just doesn't get face-based selfie detection. Using a dedicated
// generated directory (registered as an extra `assets.srcDir` on the `main` source set) lets AGP
// wire the implicit task dependencies for merge/lint on its own.
val faceModelUrl =
    "https://storage.googleapis.com/mediapipe-models/face_detector/" +
        "blaze_face_short_range/float16/1/blaze_face_short_range.tflite"
val generatedAssetsDir = layout.buildDirectory.dir("generated/mediapipeAssets")

val downloadFaceDetectorModel by tasks.registering {
    description = "Fetches the blaze_face_short_range.tflite model used by SelfieDetector."
    val outputDir = generatedAssetsDir
    outputs.dir(outputDir)
    outputs.upToDateWhen {
        outputDir.get().file("face_detector.tflite").asFile.let { it.exists() && it.length() > 0 }
    }
    doLast {
        val target = outputDir.get().file("face_detector.tflite").asFile
        if (target.exists() && target.length() > 0) return@doLast
        target.parentFile.mkdirs()
        // Download to a sibling temp file and atomically rename on success so an interrupted
        // transfer never leaves a truncated .tflite that the next build would happily treat as
        // up-to-date.
        val tmp = File(target.parentFile, "face_detector.tflite.part")
        if (tmp.exists()) tmp.delete()
        try {
            URI(faceModelUrl).toURL().openStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            logger.lifecycle("Downloaded face_detector.tflite (${target.length() / 1024} KB)")
        } catch (t: Throwable) {
            logger.warn(
                "Could not download MediaPipe face detector model: ${t.message}. " +
                    "SelfieDetector will skip face-based classification.",
            )
            if (tmp.exists()) tmp.delete()
            if (target.exists()) target.delete()
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(
    generatedAssetsDir.map { it.asFile }.also { /* keep the provider alive */ },
)

// Ensure every task that consumes assets waits for the download to run.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(downloadFaceDetectorModel) }
tasks.matching { it.name.startsWith("generate") && it.name.contains("Lint") }
    .configureEach { dependsOn(downloadFaceDetectorModel) }
tasks.matching { it.name.startsWith("package") && it.name.endsWith("Resources") }
    .configureEach { dependsOn(downloadFaceDetectorModel) }

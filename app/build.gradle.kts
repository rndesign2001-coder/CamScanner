import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appVersionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "uz.kitobskaner"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.kitobskaner"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.0.$appVersionCode"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            // Repozitoriydagi kalit — bir xil imzo bilan yangilanishlar ustiga o'rnatiladi.
            // O'z kalitingizni ishlatish uchun SIGNING_* muhit o'zgaruvchilarini bering.
            storeFile = file(System.getenv("SIGNING_STORE_FILE") ?: "../keystore/release.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "kitobskaner2026"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "kitobskaner"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "kitobskaner2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("traineddata", "ttf")
    }
    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
        }
    }
}

// Tesseract "best" modellari (oflayn OCR) — yig'ishdan oldin yuklab olinadi.
val tessLanguages = listOf("eng", "rus", "uzb", "uzb_cyrl")
val downloadTessdata by tasks.registering {
    val outDir = layout.projectDirectory.dir("src/main/assets/tessdata").asFile
    outputs.dir(outDir)
    doLast {
        outDir.mkdirs()
        for (lang in tessLanguages) {
            val target = File(outDir, "$lang.traineddata")
            if (target.exists() && target.length() > 100_000) continue
            val url = "https://github.com/tesseract-ocr/tessdata_best/raw/main/$lang.traineddata"
            logger.lifecycle("Downloading $url")
            val tmp = File(outDir, "$lang.traineddata.part")
            URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) throw GradleException("Cannot write $target")
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadTessdata) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Hujjat skaneri (qirralarni aniqlash, perspektivani to'g'rilash) — qurilmada ishlaydi
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    // Tesseract 5 OCR — qurilma CPU'sida, internetsiz
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // PDF (rasm + yashirin matn qatlami)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}

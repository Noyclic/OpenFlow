import java.io.File
import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val ocrAssetsRoot = layout.buildDirectory.dir("generated/ocr-assets")
val prepareOcrModels = tasks.register("prepareOcrModels") {
    outputs.dir(ocrAssetsRoot)
    doLast {
        val tessdataDir = ocrAssetsRoot.get().dir("tessdata").asFile
        tessdataDir.mkdirs()
        val modelRevision = "87416418657359cb625c412a48b6e1d6d41c29bd"
        listOf("eng", "rus").forEach { language ->
            val target = File(tessdataDir, "$language.traineddata")
            if (!target.isFile || target.length() < 300_000L) {
                val temporary = java.io.File(tessdataDir, "$language.traineddata.download")
                val connection = URL(
                    "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/$modelRevision/$language.traineddata"
                ).openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 90_000
                    setRequestProperty("User-Agent", "OpenFlow-Android-Build")
                }
                connection.getInputStream().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                check(temporary.length() >= 300_000L) { "Invalid OCR model download: $language" }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
            }
            check(target.isFile && target.length() >= 300_000L) { "OCR model missing: $language" }
        }
    }
}

android {
    namespace = "com.noyclic.openflow"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.noyclic.openflow"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.4.3"
        testInstrumentationRunner = "android.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    sourceSets.getByName("main").assets.srcDir(ocrAssetsRoot)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
tasks.named("preBuild").configure { dependsOn(prepareOcrModels) }

dependencies {
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    testImplementation("junit:junit:4.13.2")
}

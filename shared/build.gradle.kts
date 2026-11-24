import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.compiler)
}

val environment = Properties().apply {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) envFile.inputStream().use { load(it) }
}
val configuredApiBaseUrl = providers.environmentVariable("API_BASE_URL")
    .orElse(environment.getProperty("API_BASE_URL") ?: "")
abstract class GenerateApiConfig : DefaultTask() {
    @get:Input abstract val apiBaseUrl: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val value = apiBaseUrl.get().trim().trimEnd('/')
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null && value.none { it in "\"\\\r\n" }) {
            "Set API_BASE_URL to an HTTPS URL in .env or the environment."
        }
        val output = outputDirectory.get().file("com/getmobilestack/kmp/pintar/config/ApiConfig.kt").asFile
        output.parentFile.mkdirs()
        output.writeText("package com.getmobilestack.kmp.pintar.config\n\nobject ApiConfig { const val BASE_URL = \"$value\" }\n")
    }
}
val generateApiConfig = tasks.register<GenerateApiConfig>("generateApiConfig") {
    apiBaseUrl.set(configuredApiBaseUrl)
    outputDirectory.set(layout.buildDirectory.dir("generated/apiConfig/commonMain"))
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_11.toString()
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
            export(libs.decompose)
            export(libs.essenty.lifecycle)
        }
    }

    sourceSets {
        commonMain { kotlin.srcDir(layout.buildDirectory.dir("generated/apiConfig/commonMain")) }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            api(libs.decompose)
            api(libs.decompose.composeExtensions)
            implementation(libs.gitlive.firebase.common)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.config)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.essenty.lifecycle)
            api(libs.napier)
            implementation(libs.kotlinx.datetime)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.kvault)
            implementation(libs.filekit.compose)
            implementation(libs.revenuecat.purchases.core)
            implementation(libs.revenuecat.purchases.datetime)
            implementation(libs.revenuecat.purchases.ui)
            implementation(libs.openai.client)
            implementation(libs.material.icons)
            implementation(projects.camera)
            implementation(projects.imagePicker)
        }
        androidMain.dependencies {
            implementation(libs.decompose.composeExtensions)
            api(project.dependencies.platform(libs.firebase.bom))
            api(libs.firebase.auth)
            api(libs.firebase.common)
            api(libs.firebase.analytics)
            api(libs.firebase.crashlytics)
            api(libs.firebase.config)
            implementation(libs.ktor.okhttp)
        }
        iosMain.dependencies {
            api(libs.decompose)
            api(libs.essenty.lifecycle)
            implementation(libs.ktor.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.mock)
        }

        // RevenueCat uses generated Kotlin bindings for native code on iOS
        named { it.lowercase().startsWith("ios") }.configureEach {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
    }

    task("testClasses") // Fix for cannot locate tasks that match ':shared:testClasses'
}

tasks.configureEach {
    if (name.startsWith("compileKotlin") || name.startsWith("compileDebugKotlin") || name.startsWith("compileReleaseKotlin")) {
        dependsOn(generateApiConfig)
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.getmobilestack.kmp.resources"
    generateResClass = always
}

android {
    namespace = "com.getmobilestack.kmp"
    buildFeatures { buildConfig = true }
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

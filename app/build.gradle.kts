import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("org.openapi.generator")
}

val catalogMockScenario = providers.gradleProperty("catalogMockScenario").orElse("success").get()
require(catalogMockScenario in setOf("success", "empty", "error")) {
    "catalogMockScenario must be success, empty or error"
}

android {
    namespace = "com.example.tusumba"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.tusumba"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "CATALOG_MOCK_SCENARIO", "\"$catalogMockScenario\"")
    }

    buildTypes {
        debug {
            // Для эмулятора localhost компьютера — это 10.0.2.2.
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "BASE_URL", "\"https://api.tusumba.example/\"")
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        getByName("main") {
            java.srcDir("$buildDir/generated/openapi/notifications/src/main/kotlin")
            java.srcDir("$buildDir/generated/openapi/events/src/main/kotlin")
            java.srcDir("$buildDir/generated/openapi/moderation/src/main/kotlin")
            java.srcDir("$buildDir/generated/openapi/identity/src/main/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation(libs.kotlinx.serialization.json)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(kotlin("test"))

    // Тестирование
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("io.mockk:mockk:1.13.10")
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
}

// 1. Задача для Notifications
tasks.register<GenerateTask>("openApiGenerateNotifications") {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/specs/notifications.yaml")
    outputDir.set("$buildDir/generated/openapi/notifications")

    // Базовый пакет для этого модуля (сюда попадет и инфраструктура)
    packageName.set("com.example.tusumba.data.notifications")

    apiPackage.set("com.example.tusumba.data.notifications.api")
    modelPackage.set("com.example.tusumba.data.notifications.model")

    generateApiTests.set(false)
    generateModelTests.set(false)

    configOptions.set(mapOf(
        "library" to "jvm-retrofit2",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
        "dateLibrary" to "java8"
    ))
}

// 2. Задача для Events
tasks.register<GenerateTask>("openApiGenerateEvents") {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/specs/events.yaml")
    outputDir.set("$buildDir/generated/openapi/events")

    // СВОЙ уникальный пакет, чтобы не пересекаться с другими
    packageName.set("com.example.tusumba.data.events")

    apiPackage.set("com.example.tusumba.data.events.api")
    modelPackage.set("com.example.tusumba.data.events.model")

    generateApiTests.set(false)
    generateModelTests.set(false)

    configOptions.set(mapOf(
        "library" to "jvm-retrofit2",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
        "dateLibrary" to "java8"
    ))
}

// 3. Задача для Moderation
tasks.register<GenerateTask>("openApiGenerateModeration") {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/specs/moderation.yaml")
    outputDir.set("$buildDir/generated/openapi/moderation")

    // СВОЙ уникальный пакет
    packageName.set("com.example.tusumba.data.moderation")

    apiPackage.set("com.example.tusumba.data.moderation.api")
    modelPackage.set("com.example.tusumba.data.moderation.model")

    generateApiTests.set(false)
    generateModelTests.set(false)

    configOptions.set(mapOf(
        "library" to "jvm-retrofit2",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
        "dateLibrary" to "java8"
    ))
}

tasks.register<GenerateTask>("openApiGenerateIdentity") {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/specs/identity.yaml")
    outputDir.set("$buildDir/generated/openapi/identity")

    // СВОЙ уникальный пакет
    packageName.set("com.example.tusumba.data.identity")

    apiPackage.set("com.example.tusumba.data.identity.api")
    modelPackage.set("com.example.tusumba.data.identity.model")

    generateApiTests.set(false)
    generateModelTests.set(false)

    configOptions.set(mapOf(
        "library" to "jvm-retrofit2",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
        "dateLibrary" to "java8"
    ))
}

tasks.named("preBuild") {
    dependsOn("openApiGenerateNotifications", "openApiGenerateEvents", "openApiGenerateModeration", "openApiGenerateIdentity")
}
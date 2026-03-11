import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.localmind.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.localmind.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1       // VERSION: manually change karo jab update karna ho
        versionName = "1.0.0" // VERSION: manually change karo jab update karna ho

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // NDK configuration
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DANDROID_ARM_NEON=TRUE"
                )
                cppFlags += listOf("-Os", "-fvisibility=hidden")
            }
        }
    }

    // Load signing credentials from local.properties
    val localProps = Properties().also { props ->
        val f = rootProject.file("local.properties")
        if (f.exists()) props.load(f.inputStream())
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = localProps["KEYSTORE_PASS"] as String?
                ?: System.getenv("KEYSTORE_PASS") ?: ""
            keyAlias = localProps["KEY_ALIAS"] as String?
                ?: System.getenv("KEY_ALIAS") ?: "localmind-release"
            keyPassword = localProps["KEY_PASS"] as String?
                ?: System.getenv("KEY_PASS") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        debug {
            isMinifyEnabled = false
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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

    // Kotlin 2.0+ uses composeCompiler block instead of composeOptions
    composeCompiler {
        // Strong skipping is enabled by default in Kotlin 2.0+ compose compiler
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        disable += setOf("ExtraTranslation", "MissingTranslation")
    }
}

configurations.configureEach {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    // SECURITY: EncryptedSharedPreferences + MasterKey (OWASP MSTG-STORAGE-1)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.material:material:1.12.0")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // DataStore (for preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager (for background downloads)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Gson (for JSON parsing)
    implementation("com.google.code.gson:gson:2.10.1")

    // Networking (Hugging Face API)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coil (Image loading for model cards)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Lottie Animations
    implementation("com.airbnb.android:lottie-compose:6.3.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Markdown (Markwon)
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:syntax-highlight:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:simple-ext:4.6.2")

    // Prism4j core (syntax highlighting with graceful fallback if locator is unavailable)
    implementation("io.noties:prism4j:2.0.0")

    // PDF Extraction (for RAG)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // HTML Parsing (for Web Search)
    implementation("org.jsoup:jsoup:1.17.2")
}

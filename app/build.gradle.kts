// app/build.gradle.kts

import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

// Релизный ключ хранится вне репозитория: keystore.properties в .gitignore.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

android {
    namespace = "by.quty.launch"
    //noinspection GradleDependency
    compileSdk = 37

    defaultConfig {
        applicationId = "by.quty.launch"
        minSdk = 29
        //noinspection OldTargetApi
        targetSdk = 36
    versionCode = 842
    versionName = "0.0.198"
        versionNameSuffix = "Alpha"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: сжатие и обфускация кода + удаление неиспользуемых ресурсов.
            // Правила и объяснения — в proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Без релизного ключа сборка остаётся на debug-подписи: она не годится
            // для распространения (известный сертификат, короткий срок жизни).
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "WARNING: keystore.properties не найден — release подписывается debug-ключом"
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // WebKit
    implementation(libs.androidx.webkit)

    // Material Components (для TabLayout)
    implementation(libs.material)

    // ViewPager2
    implementation(libs.androidx.viewpager2)

    // Fragment KTX
    implementation(libs.androidx.fragment.ktx)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.compiler)

    // CardView
    implementation(libs.androidx.cardview)

    // SwipeRefreshLayout
    implementation(libs.androidx.swiperefreshlayout)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Clé de TEST fixe : toutes les versions ont la même signature,
    // donc chaque nouvel APK s'installe par-dessus l'ancien.
    // Ce n'est pas une clé de publication (mot de passe public).
    signingConfigs {
        create("test") {
            storeFile = file("nexus-test.keystore")
            storePassword = "nexustest"
            keyAlias = "nexustest"
            keyPassword = "nexustest"
        }
    }

    namespace = "fr.nexus.one"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.lacroix.nexusos"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("test") }
        release {
            isMinifyEnabled = false
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

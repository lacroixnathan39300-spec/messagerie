plugins {
    id("com.android.application")
}

android {
    namespace = "fr.terrevivante"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.terrevivante"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("vc")?.toString()?.toIntOrNull()) ?: 1
        versionName = "1.0"
    }
    // Clé stable : les nouvelles versions s'installent par-dessus sans effacer la partie sauvegardée.
    signingConfigs {
        create("stable") {
            storeFile = file("../cle.jks")
            storePassword = "terrevivante2026"
            keyAlias = "terre"
            keyPassword = "terrevivante2026"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}

plugins {
    id("com.android.application")
    id("com.chaquo.python")
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
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")  // signature de test, générée par le runner
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
    }
}

dependencies {
}

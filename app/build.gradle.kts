plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "nl.roanheemstra.cnfixer"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.roanheemstra.cnfixer"
        minSdk = 31
        targetSdk = 36
        versionCode = 8
        versionName = "0.6.1"
    }

    /**
     * One key for every build, from CI secrets. Without it Android refuses to
     * install an update over an existing install ("conflicting signature"),
     * so the keystore has to stay the same for the lifetime of the app.
     */
    signingConfigs {
        create("shared") {
            val storePath = System.getenv("CNFIXER_KEYSTORE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("CNFIXER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CNFIXER_KEY_ALIAS") ?: "cnfixer"
                keyPassword = System.getenv("CNFIXER_KEY_PASSWORD")
                    ?: System.getenv("CNFIXER_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!System.getenv("CNFIXER_KEYSTORE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
        debug {
            // No applicationIdSuffix: the debug APK is what gets side-loaded,
            // so it must install under the real package name, and it has to be
            // signed with the shared key so it can replace older installs.
            if (!System.getenv("CNFIXER_KEYSTORE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("shared")
            }
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
        aidl = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Reaches the hidden voice interaction API that starts Circle to Search,
    // the same way MiCTS does it. No root needed for the call itself.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

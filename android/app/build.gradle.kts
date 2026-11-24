import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing lives outside the repository: `android/keystore.properties` points at the .jks next
// to it. Without that file the release build simply stays unsigned (debug builds are unaffected), so a
// fresh clone still compiles.
val keystoreProperties: Properties? = run {
    val file = rootProject.file("keystore.properties")
    if (!file.exists()) return@run null
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "io.terramap.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.terramap.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 31
        versionName = "0.1.0-rc.4"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        if (keystoreProperties != null) {
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
            // R8 stays off: ShizukuShell calls Shizuku.newProcess via reflection, and the Shizuku
            // provider plus Compose would need a hand-written keep file to survive shrinking.
            isMinifyEnabled = false
            isDebuggable = false
            if (keystoreProperties != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
}

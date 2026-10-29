plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.notile"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.notile"
        minSdk = 33 // RuntimeShader for the glass lens effect
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug") // personal use; no store release yet
        }
    }

    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.11.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")

    implementation("io.github.kyant0:backdrop:2.0.1") // liquid glass: blur + lens refraction
    implementation("sh.calvin.reorderable:reorderable:3.1.0") // drag to reorder the queue

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814") // Android's org.json is a stub in local unit tests
}

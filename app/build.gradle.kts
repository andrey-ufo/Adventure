plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.myapp.adventure"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.myapp.adventure"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    // Kept for the XML theme parent (Theme.Material3.DayNight.NoActionBar)
    // and AppCompatDelegate-based night mode.
    implementation(libs.material)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.androidx.health.connect)

    // ---- Compose ----
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- Storage & serialization for AppThemeConfig ----
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // ---- Test-only ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // JVM variant of PreferenceDataStoreFactory (uses OkioStorage).
    // Overrides the Android artifact in unit tests so Windows file-rename
    // issues (b/203087070) do not affect the test run.
    testImplementation(libs.androidx.datastore.preferences.core)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
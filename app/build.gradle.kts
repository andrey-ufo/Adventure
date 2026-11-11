plugins {
    alias(libs.plugins.android.application)
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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        viewBinding = true
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkReleaseBuilds = false
        // Явно указываем конфиг, чтобы AGP не искал его эвристически.
        lintConfig = file("lint.xml")
        // Именно disable исключает детектор из запуска;
        // severity="ignore" в lint.xml только подавляет отчёт после выполнения.
        disable += "FragmentAddMenuProvider"
        disable += "FragmentBackPressedCallback"
        disable += "FragmentLiveDataObserve"
        disable += "UnsafeRepeatOnLifecycleDetector"
        htmlReport = true
        xmlReport = true
        textReport = true
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.androidx.health.connect)
}
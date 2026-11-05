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
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("com.google.code.gson:gson:2.10.1")
    // Fragment для переключения экранов
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    // RecyclerView для списков
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // ViewModel для хранения логики
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    // LiveData для обновления интерфейса
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.androidx.health.connect)
}
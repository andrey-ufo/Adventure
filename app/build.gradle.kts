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
        // 2026-11-10: TEMPORARY WORKAROUND — remove when fixed upstream.
        //
        // UnsafeFragmentLifecycleObserverDetector (androidx.fragment:fragment-ktx
        // 1.9.1) infinite-recurses through viewLifecycleOwner.lifecycleScope
        // .launch { ... } lambdas: RecursiveMethodVisitor.visitCallExpression
        // :183 <-> :187 loops with no depth limit, pinning one WorkerExecutor
        // thread at 98% CPU and making lintAnalyzeDebug hang for 10+ minutes.
        //
        // The upstream fix exists in AndroidX main but is not in any stable
        // release yet. Relevant commits:
        //   - caa8296 "Avoid duplicate reports in UnsafeFragmentLifecycleObserverDetector"
        //   - 1ff2f34 "Limit the call depth to search"
        //   - (unnamed) "Short-circuit Compose boundaries"
        //
        // ACTION WHEN UPDATING fragment-ktx: once fragment 1.10.0+ (or a
        // 1.9.x point release that mentions these commits in its release
        // notes) is available, bump the version and DELETE the four
        // `disable +=` lines below and the matching entries in
        // app/lint.xml. Verify by running `./gradlew :app:lintDebug
        // --rerun-tasks` — it should finish in well under a minute.
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
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.carcopilot"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.carcopilot"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Phase 8 measurement harness: pick model at build time via -PmodelVariant=E2B
        val modelVariant = (project.findProperty("modelVariant") as? String) ?: "E4B"
        require(modelVariant == "E4B" || modelVariant == "E2B") {
            "modelVariant must be E4B or E2B, got: $modelVariant"
        }
        buildConfigField("String", "MODEL_VARIANT", "\"$modelVariant\"")

        // OBD data source: FIXTURE (default — the locked demo path), EMULATOR
        // (TcpOBDDataSource → Python emulator over WiFi), BLUETOOTH
        // (BluetoothOBDDataSource → paired ELM327 dongle over Classic SPP).
        // Usage:
        //   ./gradlew assembleDebug                          # FIXTURE (demo build)
        //   ./gradlew assembleDebug -PdataSource=EMULATOR
        //   ./gradlew assembleDebug -PdataSource=EMULATOR -PobdHost=192.168.1.5
        //   ./gradlew assembleDebug -PdataSource=BLUETOOTH
        val dataSource = (project.findProperty("dataSource") as? String) ?: "FIXTURE"
        require(dataSource in setOf("FIXTURE", "EMULATOR", "BLUETOOTH")) {
            "dataSource must be FIXTURE, EMULATOR, or BLUETOOTH, got: $dataSource"
        }
        buildConfigField("String", "DATA_SOURCE", "\"$dataSource\"")

        // EMULATOR-only: where the Python emulator socket lives. Android
        // emulator → Mac localhost is 10.0.2.2. On a real device over WiFi,
        // use the host machine's LAN IP, e.g. -PobdHost=192.168.1.5.
        val obdHost = (project.findProperty("obdHost") as? String) ?: "10.0.2.2"
        buildConfigField("String", "OBD_EMULATOR_HOST", "\"$obdHost\"")

        // EMULATOR-only: how often to re-poll the Python emulator for a new
        // snapshot. Default 3 s is fast enough to catch a scenario switch
        // without hammering the socket.
        //   ./gradlew assembleDebug -PdataSource=EMULATOR -PpollInterval=1000
        val pollInterval = (project.findProperty("pollInterval") as? String) ?: "3000"
        buildConfigField("long", "POLL_INTERVAL_MS", "${pollInterval}L")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            // Stops android.util.Log and other framework stubs from throwing
            // RuntimeException("Method ... not mocked") when JVM unit tests
            // touch Android-namespaced code (e.g. SynthesisState.parseOrFallback
            // calls Log.w on the fallback path). Without this, our extractor
            // tests would either need to wrap Log in a wrapper interface or
            // run as instrumented tests on a device — neither pays for itself.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.litertlm.android)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

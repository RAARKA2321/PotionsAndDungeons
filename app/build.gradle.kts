plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val relayEndpoint = providers.gradleProperty("relayEndpoint").orElse("").get()
require(relayEndpoint.matches(Regex("[a-zA-Z0-9.:-]*"))) { "Use a domain or domain:port for relayEndpoint" }

android {
    namespace = "com.example.potionsanddungeons"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.potionsanddungeons"
        minSdk = 24
        targetSdk = 37
        versionCode = 13
        versionName = "1.5.6"
        buildConfigField("String", "RELAY_ENDPOINT", "\"$relayEndpoint\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            if (providers.gradleProperty("vpnTest").isPresent) {
                applicationIdSuffix = ".vpntest"
            }
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
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
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

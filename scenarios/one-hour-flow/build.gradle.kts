plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.mobilebot.scenarios.onehour"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:systemruntime"))
    implementation(project(":scenarios:runtime"))
    implementation(project(":scenarios:pet-grooming"))
    implementation(project(":scenarios:family-shopping"))
    implementation(project(":scenarios:coldchain-delivery"))
    implementation(project(":scenarios:health-supply"))
    testImplementation(libs.junit)
}

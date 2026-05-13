import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mobilebot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mobilebot"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        val localProps =
            run {
                val p = Properties()
                val f = rootProject.file("local.properties")
                if (f.exists()) {
                    f.inputStream().use { p.load(it) }
                }
                p
            }
        fun escapeForBuildConfig(raw: String): String =
            raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        val geminiKey = localProps.getProperty("GEMINI_API_KEY", "").trim()
        val zhipuKey = localProps.getProperty("ZHIPU_API_KEY", "").trim()
        val minimaxKey = localProps.getProperty("MINIMAX_API_KEY", "").trim()
        val dashscopeKey = localProps.getProperty("DASHSCOPE_API_KEY", "").trim()
        buildConfigField("String", "GEMINI_API_KEY", "\"${escapeForBuildConfig(geminiKey)}\"")
        buildConfigField("String", "ZHIPU_API_KEY", "\"${escapeForBuildConfig(zhipuKey)}\"")
        buildConfigField("String", "MINIMAX_API_KEY", "\"${escapeForBuildConfig(minimaxKey)}\"")
        buildConfigField("String", "DASHSCOPE_API_KEY", "\"${escapeForBuildConfig(dashscopeKey)}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        buildConfig = true
    }
}

dependencies {
    implementation(project(":feature:chat"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:bridge"))
    implementation(project(":core:systemruntime"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.androidx.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    implementation("androidx.startup:startup-runtime:1.1.1")
}

import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        if(propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }

        //val groqKey = properties.getProperty("GROQ_API_KEY") ?: ""
        val geminiKey = properties.getProperty("GEMINI_API_KEY") ?: ""
        val weatherKey = properties.getProperty("WEATHER_API_KEY") ?: ""
        val googleKey = properties.getProperty("GOOGLE_API_KEY") ?: ""

        //buildConfigField("String", "GROQ_API_KEY", "\"$groqKey\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        buildConfigField("String", "WEATHER_API_KEY", "\"$weatherKey\"")
        buildConfigField("String", "GOOGLE_API_KEY", "\"$googleKey\"")
    }

    buildFeatures {
        buildConfig = true
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // 1. CHỈ DÙNG 1 BOM DUY NHẤT (Phiên bản ổn định 33.0.0)
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))

    // 2. Thư viện Firebase Auth (Không cần ktx, không cần ghi phiên bản vì đã có BOM)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.1.1")

    // HTTP Request Library
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Thêm SDK Gemini
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    // Android for Cars App Library (Để chạy trên ô tô)
    implementation("androidx.car.app:app:1.4.0-rc01")
    // HTTP Client (Để kết nối Groq API)
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // HTTP Client (OkHttp làm nền tảng)
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // Các thư viện hỗ trợ xử lý giọng nói và âm thanh
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // --- THÊM RETROFIT & GSON Ở ĐÂY ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
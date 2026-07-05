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
        buildConfigField("String", "SERPER_API_KEY", "\"${project.findProperty("SERPER_API_KEY")}\"")
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {

    // 1. Hệ thống Firebase với BOM (Quản lý tập trung, chống xung đột phiên bản)
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-firestore") // Chuẩn mới không cần -ktx
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.1.1")

    // 2. Mạng và Khai triển API (Đã xóa bỏ hoàn toàn các dòng OkHttp trùng lặp)
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 3. Luồng bất đồng bộ và Vòng đời (Đã dọn dẹp trùng lặp)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // 4. Các Core SDK phục vụ tính năng AI & Ô tô của đồ án
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0") // Gemini API
    implementation("androidx.car.app:app:1.4.0") // Android Auto Component

    implementation("com.google.ai.edge.litertlm:litertlm-android:0.12.0")
    // Khai báo file thư viện GenAI Offline bạn vừa tự build
    implementation(files("libs/onnxruntime-genai-release.aar"))

// Kèm theo lõi ONNX Runtime cơ bản (Lõi này có sẵn trên mạng nên Android tự tải được)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:latest.release")

    // 5. Thư viện Giao diện nền tảng (Giữ lại từ Version Catalog, ĐÃ XÓA dòng Firebase trùng lặp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.room.runtime.android)

    // 6. Bộ công cụ kiểm thử (Testing)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
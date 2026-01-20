plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.calendar_vol1"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.calendar_vol1"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        viewBinding = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // 1. Room 数据库
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    kapt("androidx.room:room-compiler:$room_version") // 如果你还没改 ksp，先用这个
    implementation("androidx.room:room-ktx:$room_version")

    // 2. 基础 UI 组件
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 3. ✨ 核心日历库 (这一行必须有，且不能被注释！)
    implementation("com.kizitonwose.calendar:view:2.5.0")

    // 4. 解析 RFC5545 (iCal) 的库
    implementation("net.sf.biweekly:biweekly:0.6.7")
    implementation("com.github.kizitonwose:CalendarView:1.1.0")
    // 5. 农历转换库 (这个是你刚才注释掉的，保持注释就行，先别管它)
    // implementation("com.github.litesuits:android-lunar-calendar:1.0.0")

    // 6. JSON解析
    implementation("com.google.code.gson:gson:2.10.1")

    // 7. 标准库
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

}
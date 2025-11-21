plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.autoeda"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.autoeda"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Java 1.8
        multiDexEnabled = true
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

    // XML UI만 사용 → Compose 끔
    buildFeatures {
        viewBinding = true
        compose = false
    }

    // Java 컴파일 타겟
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Kotlin 컴파일 타겟
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CardView
    implementation("androidx.cardview:cardview:1.0.0")
}

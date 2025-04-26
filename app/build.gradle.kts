plugins {
    id("com.android.application")
}

android {
    namespace = "com.taut0logy.readerforu"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.taut0logy.readerforu"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.2"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

dependencies {

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    //implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.itextpdf:itext7-core:7.1.15")
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.3")
    //implementation("com.github.bumptech.glide:glide:4.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
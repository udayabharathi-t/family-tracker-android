plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.udayabharathi.familytracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.udayabharathi.familytracker"
        minSdk = 34
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
    packaging {
        resources.excludes.add("META-INF/INDEX.LIST")
        resources.excludes.add("META-INF/DEPENDENCIES")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.location)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.sheets)
    implementation(libs.play.services.identity)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credential.manager)
    implementation(libs.androidx.credential.manager.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.googleid)
    implementation("com.google.http-client:google-http-client-android:1.42.3")
    implementation("com.google.http-client:google-http-client-jackson2:1.42.3")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

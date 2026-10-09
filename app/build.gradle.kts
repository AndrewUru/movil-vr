plugins { id("com.android.application") }

android {
    namespace = "com.muralar.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.muralar.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-marker-drawing"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation("com.google.ar:core:1.56.0")
    implementation("org.opencv:opencv:4.12.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    testImplementation("junit:junit:4.13.2")
}

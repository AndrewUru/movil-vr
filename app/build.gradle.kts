plugins { id("com.android.application") }

android {
    namespace = "com.muralar.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.muralar.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-anchor-proof"
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
    testImplementation("junit:junit:4.13.2")
}

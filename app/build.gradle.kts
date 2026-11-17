plugins { id("com.android.application") }
android {
    namespace = "dev.wristlink"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.wristlink"
        minSdk = 36
        targetSdk = 36
        versionCode = 9
        versionName = "0.8.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

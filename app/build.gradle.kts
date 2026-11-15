plugins { id("com.android.application") }
android {
    namespace = "dev.wristlink"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.wristlink"
        minSdk = 36
        targetSdk = 36
        versionCode = 5
        versionName = "0.5-link-recovery"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

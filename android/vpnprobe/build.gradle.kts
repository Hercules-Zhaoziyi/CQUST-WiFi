plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace="cn.ziyis.cqustprobe"
    compileSdk=35
    defaultConfig { applicationId="cn.ziyis.cqustprobe"; minSdk=29; targetSdk=35; versionCode=1;versionName="fixture" }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget="17" }
}

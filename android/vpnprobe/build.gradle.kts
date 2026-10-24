plugins { id("com.android.application") }
android {
    namespace="cn.ziyis.cqustprobe"
    compileSdk { version=release(37){minorApiLevel=0} }
    defaultConfig { applicationId="cn.ziyis.cqustprobe"; minSdk=29; targetSdk=35; versionCode=1;versionName="fixture" }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_21;targetCompatibility=JavaVersion.VERSION_21 }
}

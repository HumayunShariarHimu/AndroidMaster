plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="com.androidmaster.app"; compileSdk=35
 defaultConfig { applicationId="com.androidmaster.app"; minSdk=26; targetSdk=35; versionCode=1; versionName="0.1.0" }
 buildFeatures { compose=true }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.02.00"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
}

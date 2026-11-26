plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStorePath = providers.environmentVariable("LIFETRACK_RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("LIFETRACK_KEYSTORE_PASSWORD").orNull
val releaseAlias = providers.environmentVariable("LIFETRACK_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("LIFETRACK_KEY_PASSWORD").orNull

android {
    namespace = "com.yakhrevan.lifetrack"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.yakhrevan.lifetrack"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Debug builds never require secrets. Release signing is configured only
    // when the isolated CI signing job provides all four inputs at runtime.
    if (listOf(releaseStorePath, releaseStorePassword, releaseAlias, releaseKeyPassword)
            .all { !it.isNullOrBlank() }) {
        signingConfigs.create("secureRelease") {
            storeFile = file(requireNotNull(releaseStorePath))
            storePassword = releaseStorePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("secureRelease")
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

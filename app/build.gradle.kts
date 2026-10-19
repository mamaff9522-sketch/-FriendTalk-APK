import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.example" // per flavor below
    minSdk = 24
    targetSdk = 36
    versionCode = (System.getenv("VERSION_CODE") ?: "4").toInt()
    // AdMob: debug builds always use Google's TEST ids; release uses the owner's real (public) ids, overridable via env/secrets.
    // Test unit ids are also kept in every build so admin accounts get test ads at runtime (no invalid-traffic risk).
    buildConfigField("String", "ADMOB_TEST_REWARDED_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/5354046379\"")
    buildConfigField("String", "ADMOB_TEST_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
    buildConfigField("String", "ADMOB_TEST_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
    buildConfigField("String", "ADMOB_TEST_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
    versionName = "1.2.0"
    // Phones only (keeps the Agora voice/video native libs out of the x86 emulator ABIs → much smaller APK).
    // native ABIs are set per flavor below (flavor filters merge with defaultConfig, so none here)

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS")?.ifBlank { null } ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  
  }

  fun envOr(k: String, d: String) = System.getenv(k)?.takeIf { it.isNotBlank() } ?: d
  fun admob(t: com.android.build.api.dsl.ApplicationBuildType, app: String, ri: String, rw: String, bn: String, ins: String) {
    t.manifestPlaceholders["admobAppId"] = app
    t.buildConfigField("String", "ADMOB_REWARDED_INTERSTITIAL_ID", "\"$ri\"")
    t.buildConfigField("String", "ADMOB_REWARDED_ID", "\"$rw\"")
    t.buildConfigField("String", "ADMOB_BANNER_ID", "\"$bn\"")
    t.buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$ins\"")
  }
  buildTypes {
    getByName("debug") {
      admob(this, "ca-app-pub-3940256099942544~3347511713", "ca-app-pub-3940256099942544/5354046379", "ca-app-pub-3940256099942544/5224354917",
        "ca-app-pub-3940256099942544/6300978111", "ca-app-pub-3940256099942544/1033173712")
    }
    release {
      // No real interstitial unit yet -> empty (interstitial is off by default; app skips loading when blank).
      admob(this, envOr("ADMOB_APP_ID", "ca-app-pub-8235263673334341~5747162954"), envOr("ADMOB_REWARDED_INTERSTITIAL_ID", "ca-app-pub-8235263673334341/8303017417"),
        envOr("ADMOB_REWARDED_ID", "ca-app-pub-8235263673334341/1504665618"), envOr("ADMOB_BANNER_ID", "ca-app-pub-8235263673334341/9427387669"), envOr("ADMOB_INTERSTITIAL_ID", ""))
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
   
  }
  // Two installs of the same app:
  //  - sideload: the test APK shared by link (keeps the old id so testers' installs keep updating in place)
  //  - play:     the Google Play build (real package name, Play-only rules: no self-install permission, no bank-transfer top-up)
  flavorDimensions += "dist"
  productFlavors {
    create("sideload") {
      dimension = "dist"
      applicationId = "com.example"
      buildConfigField("boolean", "PLAY_STORE", "false")
      // test APK shared by link: 64-bit only (~60 MB instead of ~100 MB); nearly every phone since ~2017
      ndk { abiFilters += listOf("arm64-v8a") }
    }
    create("play") {
      dimension = "dist"
      applicationId = "th.friendtalk.app"
      buildConfigField("boolean", "PLAY_STORE", "true")
      // Play splits the bundle per device, so keep 32-bit too (users only download their own ABI)
      ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.ERROR }

dependencies {
  implementation("com.android.billingclient:billing-ktx:8.0.0")
  // Voice/video calls and live streaming through Three Junks (our own servers; Agora removed).
  // SDK sources vendored in app/src/main/java/com/threejunks/sdk. Calls: Google's WebRTC prebuilt (Apache-2.0 / BSD).
  implementation("io.getstream:stream-webrtc-android:1.3.8")
  // Live: LiveKit Android SDK (Apache-2.0); its WebRTC is renamed, so it coexists with the calls' WebRTC.
  implementation("io.livekit:livekit-android:2.29.0")
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
implementation("com.google.firebase:firebase-auth")
implementation("com.google.firebase:firebase-firestore")
implementation("com.google.firebase:firebase-storage")
implementation("com.google.firebase:firebase-database")
implementation("com.google.firebase:firebase-messaging") // push notifications
implementation("com.google.android.gms:play-services-ads:23.6.0")
implementation("com.google.android.ump:user-messaging-platform:3.1.0")
// OpenStreetMap map for "เลือกตำแหน่งจากแผนที่" in the post composer (no API key needed)
implementation("org.osmdroid:osmdroid-android:6.1.20")
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.coil.compose)
  implementation(libs.coil.gif)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.play.services.auth)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

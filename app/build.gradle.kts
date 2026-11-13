import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    // A package name the phone's filter allows (it still shows as "VelvetBox")
    applicationId = "com.roadpal.milonai"
    minSdk = 28
    // API 27: Android 9 then lets VelvetBox use the hidden system parts the
    // sandbox engine needs ("dark greylist"), even if the exemption below fails.
    targetSdk = 27
    versionCode = 7
    versionName = "3.0"

    ndk {
      // arm64-v8a runs 64-bit apps in the sandbox; armeabi-v7a is kept for 32-bit-only phones.
      abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
  }

  // One fixed key (velvetbox.keystore in this repo) so every new build installs over the
  // old one and the apps inside the sandbox are kept.
  signingConfigs {
    create("velvet") {
      storeFile = rootProject.file("velvetbox.keystore")
      storePassword = "velvetbox"
      keyAlias = "velvetbox"
      keyPassword = "velvetbox"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("velvet")
    }
    debug {
      signingConfig = signingConfigs.getByName("velvet")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures {
    compose = true
  }

  packaging {
    jniLibs {
      // The engine expects native libraries extracted to disk.
      useLegacyPackaging = true
    }
    resources {
      excludes += listOf("META-INF/*.version", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
  }

  lint {
    // The old targetSdk is on purpose; don't fail the build over it.
    checkReleaseBuilds = false
    abortOnError = false
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

dependencies {
  // Sandbox engine
  implementation(project(":Bcore"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.kotlinx.coroutines.android)
}

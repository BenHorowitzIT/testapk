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
    applicationId = "com.aistudio.velvetbox.vbxq"
    minSdk = 28
    // The sandbox engine needs the host app to target API 28.
    targetSdk = 28
    versionCode = 2
    versionName = "2.0"

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
    // targetSdk 28 is required by the engine; don't fail the build over it.
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
  // Split APK merger (ARSCLib, Apache-2.0) - downloaded by the GitHub workflow into app/libs
  implementation(files("libs/ARSCLib.jar"))
  // Re-signs merged XAPKs so the engine accepts them
  implementation(libs.apksig)

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

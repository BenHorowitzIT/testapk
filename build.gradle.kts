plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
}

// Values the sandbox engine's build script reads from the root project.
extra["compileSdkVersion"] = 35
extra["targetSdkVersion"] = 28
extra["minSdk"] = 21

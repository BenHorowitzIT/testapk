pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  // PREFER_SETTINGS (not FAIL_ON_PROJECT_REPOS) because the sandbox engine modules
  // are pulled in from another project during the GitHub build.
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://www.jitpack.io") }
  }
}

rootProject.name = "VelvetBox"

include(":app")

// Sandbox engine (NewBlackbox, Apache-2.0). The GitHub workflow downloads it into
// engine/NewBlackbox before building, so it never has to live in this repo.
val engineDir = file("engine/NewBlackbox")
if (!engineDir.exists()) {
  throw GradleException(
    "Sandbox engine missing: engine/NewBlackbox. Build through the GitHub Actions workflow."
  )
}
include(":Bcore", ":black-reflection", ":compiler")
project(":Bcore").projectDir = File(engineDir, "Bcore")
project(":black-reflection").projectDir = File(engineDir, "black-reflection")
project(":compiler").projectDir = File(engineDir, "compiler")

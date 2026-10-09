// Stein Loader. It runs on top of the Stein Bridge and builds with the Stein SDK's readable-name tools: both
// repositories sit next to this one (../bridge and ../sdk, or -Pstein.bridgeDir=... and -Pstein.sdkDir=...).
plugins {
    // JDK 25 for compiling: Gradle finds an installed one or downloads it.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "stein-loader"

for ((repo, default) in listOf("bridge" to "../bridge", "sdk" to "../sdk")) {
    val dir = file(providers.gradleProperty("stein.${repo}Dir").getOrElse(default))
    require(dir.resolve("settings.gradle.kts").isFile) {
        "the $repo repository was not found at $dir: clone it next to this one, or pass -Pstein.${repo}Dir=..."
    }
    includeBuild(dir) { name = "stein-$repo" }
}

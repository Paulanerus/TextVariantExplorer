plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "dev.paulee"
version = rootProject.extra["ui.version"] as String

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(kotlin("reflect"))

    implementation(project(":api"))

    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material-icons-core:${rootProject.extra["icons.version"]}")
}

kotlin {
    jvmToolchain(21)
}
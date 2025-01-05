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

    testImplementation(kotlin("test"))

    implementation(compose.desktop.currentOs)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
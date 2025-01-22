plugins {
    kotlin("jvm")
}

group = "dev.paulee"
version = rootProject.extra["api.version"] as String

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
}
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

    implementation("com.fasterxml.jackson.core:jackson-annotations:${rootProject.extra["jackson.version"]}")
}
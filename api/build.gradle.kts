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

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
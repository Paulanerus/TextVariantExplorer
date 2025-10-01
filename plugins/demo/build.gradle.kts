plugins {
    kotlin("jvm")

    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "dev.paulee"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(project(":api"))

    testImplementation(kotlin("test"))

    implementation(compose.desktop.currentOs)
}

tasks.jar {
    manifest{
        attributes["Main-Class"] = "dev.paulee.demo.DemoPlugin"
    }
}
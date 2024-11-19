plugins {
    kotlin("jvm")
}

group = "dev.paulee"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":api"))

    testImplementation(kotlin("test"))
}

tasks.jar {
    manifest{
        attributes["Main-Class"] = "dev.paulee.demo.DemoPlugin"
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
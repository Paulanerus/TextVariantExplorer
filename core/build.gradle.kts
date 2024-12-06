plugins {
    kotlin("jvm")
}

group = "dev.paulee"
version = rootProject.extra["core.version"] as String

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":api"))

    implementation(kotlin("reflect"))

    implementation("org.xerial:sqlite-jdbc:3.47.0.0")

    implementation("org.apache.lucene:lucene-core:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-common:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-kuromoji:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-stempel:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-smartcn:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-nori:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-morfologik:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-queryparser:${rootProject.extra["lucene.version"]}")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
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

    implementation("org.xerial:sqlite-jdbc:${rootProject.extra["sqlite-jdbc.version"]}")

    implementation("io.github.java-diff-utils:java-diff-utils:${rootProject.extra["jdu.version"]}")

    implementation("org.apache.lucene:lucene-core:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-common:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-kuromoji:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-stempel:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-smartcn:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-nori:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-analysis-morfologik:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-queryparser:${rootProject.extra["lucene.version"]}")
    implementation("org.apache.lucene:lucene-backward-codecs:${rootProject.extra["lucene.version"]}")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${rootProject.extra["jackson.version"]}")

    implementation("org.apache.logging.log4j:log4j-core:${rootProject.extra["log4j.version"]}")
    implementation("org.apache.logging.log4j:log4j-api:${rootProject.extra["log4j.version"]}")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:${rootProject.extra["log4j.version"]}")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutines.version"]}")

    implementation("ai.djl.huggingface:tokenizers:${rootProject.extra["tokenizers.version"]}")
    implementation("com.microsoft.onnxruntime:onnxruntime_gpu:${rootProject.extra["onnx.version"]}")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "dev.paulee"
version = rootProject.extra["app.version"] as String

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)

    implementation(project(":api"))
    implementation(project(":core"))
    implementation(project(":ui"))
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "dev.paulee.MainKt"

        buildTypes.release.proguard {
            isEnabled = false
        }

        jvmArgs += listOf(
            "--add-modules", "java.sql",
            "--add-modules", "jdk.incubator.vector",
            "-Dapi.version=${property("api.version")}",
            "-Dcore.version=${property("core.version")}",
            "-Dui.version=${property("ui.version")}",
            "-Dapp.version=${property("app.version")}"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TextVariantExplorer"

            licenseFile.set(project.file("LICENSE.md"))

            modules += listOf("java.sql", "jdk.incubator.vector")

            linux {
                iconFile.set(project.file("ui/src/main/resources/icon.png"))
            }

            windows {
                iconFile.set(project.file("ui/src/main/resources/icon.ico"))

                menu = true
                upgradeUuid = "C4AF61D5-8472-482D-B2A0-F92E32D7A18C"
            }

            macOS {
                // iconFile.set(project.file("ui/src/main/resources/icon.icns"))

                appCategory = "public.app-category.utilities"
            }
        }
    }
}
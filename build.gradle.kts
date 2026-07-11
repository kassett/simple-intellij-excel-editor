import groovy.json.JsonSlurper
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("com.diffplug.spotless") version "7.2.1"
}

group = "com.github.kassett"

val packageJson = JsonSlurper().parseText(rootProject.file("package.json").readText()) as Map<*, *>

version =
    providers
        .environmentVariable("PLUGIN_VERSION")
        .orElse(packageJson["version"].toString())
        .get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.apache.poi:poi-ooxml:5.4.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion")) {
            useInstaller.set(false)
        }
        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.github.kassett.excelEditor"
        name = "Excel Editor"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
        channels =
            providers
                .environmentVariable("JETBRAINS_MARKETPLACE_CHANNEL")
                .map { listOf(it) }
                .orElse(listOf("default"))
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("platformVersion")) {
                useInstaller.set(false)
            }
        }
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.5.0")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.5.0")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}

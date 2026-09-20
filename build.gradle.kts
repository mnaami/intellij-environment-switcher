import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        // JavaScript/Node APIs ship only with Ultimate; both are optional at runtime
        bundledPlugin("JavaScript")
        bundledPlugin("NodeJS")
        // compile-only: language plugins are optional dependencies at runtime
        plugin(providers.gradleProperty("pythonPlugin"))
        plugin(providers.gradleProperty("goPlugin"))
        plugin(providers.gradleProperty("phpPlugin"))
        plugin(providers.gradleProperty("rubyPlugin"))
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        zipSigner()
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * The verifier matches ignored problems on an exact plugin version, so the
 * template is materialised with the current one: a version bump must not
 * silently stop ignoring them.
 */
abstract class GenerateIgnoredProblems : DefaultTask() {
    @get:InputFile abstract val template: RegularFileProperty

    @get:Input abstract val pluginVersion: Property<String>

    @get:OutputFile abstract val output: RegularFileProperty

    @TaskAction
    fun generate() {
        output.get().asFile.writeText(
            template
                .get()
                .asFile
                .readText()
                .replace("@PLUGIN_VERSION@", pluginVersion.get()),
        )
    }
}

val verifierIgnoredProblems =
    tasks.register<GenerateIgnoredProblems>("verifierIgnoredProblems") {
        template = layout.projectDirectory.file("verifier-ignored-problems.txt.template")
        pluginVersion = providers.gradleProperty("pluginVersion")
        output = layout.buildDirectory.file("verifier-ignored-problems.txt")
    }

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ignoredProblemsFile = verifierIgnoredProblems.flatMap { it.output }
        ides {
            // -PverifyIde=PY:2026.2.3 verifies one IDE: CI runs a job per IDE,
            // because a runner has no room to download the whole set at once.
            val requested = providers.gradleProperty("verifyIde").orNull
            if (requested.isNullOrBlank()) {
                recommended()
            } else {
                val (code, version) = requested.split(":", limit = 2)
                create(IntelliJPlatformType.fromCode(code), version)
            }
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

// Manual checks of Spring Boot run configurations need Ultimate.
intellijPlatformTesting {
    runIde {
        register("runIdeUltimate") {
            type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaUltimate
            version = providers.gradleProperty("platformVersion")
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
    wrapper {
        gradleVersion = "9.7.1"
    }
}

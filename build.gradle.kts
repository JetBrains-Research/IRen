fun properties(key: String) = project.findProperty(key).toString()

plugins {
    java
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.8.0" apply false
    // gradle-intellij-plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij") version "1.12.0"
    // Gradle Qodana Plugin
    id("org.jetbrains.qodana") version "0.1.12"
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
    }
}

val junit = properties("junit")
val kinference = properties("kinference")

allprojects {
    apply {
        plugin("java")
        plugin("kotlin")
        plugin("org.jetbrains.intellij")
        plugin("org.jetbrains.qodana")
    }

    repositories {
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        maven("https://packages.jetbrains.team/maven/p/ki/maven")
    }

    configurations {
        all {
            exclude(group = "org.slf4j")
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        testImplementation("pl.pragmatists:JUnitParams:1.1.1")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.6.1")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junit}")
        testImplementation("org.junit.jupiter:junit-jupiter-api:${junit}")
        testImplementation("org.junit.jupiter:junit-jupiter-params:${junit}")
        testImplementation("org.junit.vintage:junit-vintage-engine:${junit}")

        implementation("io.kinference:inference-api:${kinference}")
        implementation("io.kinference:ndarray:${kinference}")
        implementation("io.kinference:serialization:${kinference}")
        implementation("io.kinference:inference-core:${kinference}")

        implementation("com.microsoft.onnxruntime:onnxruntime:1.11.0")
    }

    // Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
    intellij {
        pluginName.set(properties("pluginName"))
        version.set(properties("platformVersion"))
        type.set(properties("platformType"))
        downloadSources.set(properties("platformDownloadSources").toBoolean())
        updateSinceUntilBuild.set(true)

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
        val plugins = mutableListOf("java")

        when (properties("language")) {
            "java" -> run {}
            "kotlin" -> plugins.add(Plugins.kotlin)
            "python" -> plugins.add(Plugins.python)
            "all" -> {
                plugins.add(Plugins.kotlin)
                plugins.add(Plugins.python)
            }
            else -> throw InvalidUserDataException("Wrong language: `${properties("language")}`, pick one of supported (settings.gradle.kts.kts)")
        }

        this.plugins.set(plugins)
    }

    tasks {
        test {
            useJUnitPlatform()
        }

        // Set the JVM compatibility versions
        properties("javaVersion").let {
            withType<JavaCompile> {
                sourceCompatibility = it
                targetCompatibility = it
            }
            withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                kotlinOptions.jvmTarget = it
            }
        }
    }
}

subprojects {
    sourceSets {
        main {
            java.srcDirs("src")
        }
        test {
            java.srcDirs("test")
            resources.srcDirs("testData")
        }
    }
}
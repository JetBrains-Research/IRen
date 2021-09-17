fun properties(key: String) = project.findProperty(key).toString()

plugins {
    java
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.5.30" apply false
    // gradle-intellij-plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij") version "1.1.6"
    // Gradle Qodana Plugin
    id("org.jetbrains.qodana") version "0.1.12"
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
    }
}

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
            "java"   -> run {}
            "kotlin" -> plugins.add("org.jetbrains.kotlin")
            "all"    -> {
                plugins.add("org.jetbrains.kotlin")
            }
            else     -> throw InvalidUserDataException("Wrong language: `${properties("language")}`, pick one of supported (settings.gradle.kts.kts)")
        }

        this.plugins.set(plugins)
    }

    tasks {
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
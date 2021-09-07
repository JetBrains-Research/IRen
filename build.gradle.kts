fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.5.30" apply false
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
    }
}
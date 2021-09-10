import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS

fun properties(key: String) = project.findProperty(key).toString()

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.redundent:kotlin-xml-builder:1.6.0")
    }
}

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm")
    // gradle-intellij-plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij") version "1.1.6"
    // gradle-changelog-plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
    id("org.jetbrains.changelog") version "1.3.0"
    // Gradle Qodana Plugin
    id("org.jetbrains.qodana") version "0.1.12"
}

val buildPluginPath = "plugin-${properties("pluginVersion")}.zip"

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}
dependencies {
    implementation("org.junit.jupiter:junit-jupiter-params:5.5.1")
    implementation("org.junit.jupiter:junit-jupiter-api:5.5.1")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.5.1")
    implementation("org.jetbrains.intellij.deps.completion:ngram-slp:0.0.3")
//    implementation("org.tensorflow", "tensorflow", "1.13.1")
    implementation("com.github.javaparser:javaparser-core:3.0.0-alpha.4")
    implementation("net.razorvine", "pyrolite", "4.19")
    implementation("org.eclipse.mylyn.github", "org.eclipse.egit.github.core", "2.1.5")
}


// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))
    downloadSources.set(properties("platformDownloadSources").toBoolean())
    updateSinceUntilBuild.set(true)

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
}

// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
qodana {
    cachePath.set(projectDir.resolve(".qodana").canonicalPath)
    reportPath.set(projectDir.resolve("build/reports/inspections").canonicalPath)
    saveReport.set(true)
    showReport.set(System.getenv("QODANA_SHOW_REPORT").toBoolean())
}

tasks {
    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> {
            sourceCompatibility = it
            targetCompatibility = it
        }
        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = it
        }
    }

    patchPluginXml {
        version.set(properties("pluginVersion"))
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            projectDir.resolve("README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) }
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider {
            changelog.run {
                getOrNull(properties("pluginVersion")) ?: getLatest()
            }.toHTML()
        })
    }

    runPluginVerifier {
        ideVersions.set(properties("pluginVerifierIdeVersions").split(',').map(String::trim).filter(String::isNotEmpty))
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
    }

    runIde {
        jvmArgs = listOf("-Xmx12G", "-XX:+UnlockDiagnosticVMOptions",
            "-Dfus.internal.test.mode=true")
    }

    register("generateUpdatePluginsXML") {
        doLast {
            file("updatePlugins.xml").writer().use { writer ->
                val x = xml("plugins") {
                    globalProcessingInstruction("xml", "version" to "1.0", "encoding" to "UTF-8")
                    comment(
                        "AUTO-GENERATED FILE. DO NOT MODIFY. " +
                                "$buildPluginPath is generated by the generateUpdatePluginsXML gradle task"
                    )
                    "plugin"{
                        "name"{ -properties("pluginName") }
                        "id"{ -"com.github.davidenkoim.idnamessuggestingplugin" }
                        "version"{ -properties("pluginVersion") }
                        "idea-version"{ attribute("since-build", properties("pluginSinceBuild")) }
                        "vendor"{
                            attribute("url", "https://www.jetbrains.com")
                            -"JetBrains"
                        }
                        "download-url"{ -properties("buildPluginPath") }
                        "description"{
                            -projectDir.resolve("README.md").readText().lines().run {
                                val start = "<!-- Plugin description -->"
                                val end = "<!-- Plugin description end -->"

                                if (!containsAll(listOf(start, end))) {
                                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                                }
                                subList(indexOf(start) + 1, indexOf(end))
                            }.joinToString("\n").run { markdownToHTML(this) }
                        }
                    }
                }
                writer.write(x.toString(PrintOptions(singleLineTextElements = true)))
            }
        }
    }
}
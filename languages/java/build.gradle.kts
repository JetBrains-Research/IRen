intellij {
    this.plugins.set(listOf(Plugins.java))
}

dependencies {
    implementation(project(":languages:common"))

//    implementation("org.tensorflow", "tensorflow", "1.13.1")
    implementation("com.github.javaparser:javaparser-core:3.0.0-alpha.4")
    implementation("net.razorvine", "pyrolite", "4.19")
    implementation("org.eclipse.mylyn.github", "org.eclipse.egit.github.core", "2.1.5")

    testImplementation(project(":languages:common").dependencyProject.sourceSets["test"].output)
}
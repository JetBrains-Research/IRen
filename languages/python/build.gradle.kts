intellij {
    this.plugins.set(listOf(Plugins.java, Plugins.python))
}

dependencies {
    implementation(project(":languages:common"))

    testImplementation(project(":languages:common").dependencyProject.sourceSets["test"].output)
}
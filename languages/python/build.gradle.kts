intellij {
    this.plugins.set(listOf(Plugins.python))
}

dependencies {
    implementation(project(":languages:common"))
}
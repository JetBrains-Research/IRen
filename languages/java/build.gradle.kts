intellij {
    this.plugins.set(listOf(Plugins.java))
}

dependencies {
    implementation(project(":languages:common"))
}
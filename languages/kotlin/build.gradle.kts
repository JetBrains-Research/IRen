intellij {
    this.plugins.set(listOf(Plugins.java, Plugins.kotlin))
}

dependencies {
    implementation(project(":languages:common"))
}
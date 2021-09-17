intellij {
    this.plugins.set(listOf("java"))
}

dependencies {
    implementation(project(":plugin"))
}
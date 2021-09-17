intellij {
    this.plugins.set(listOf("java", "org.jetbrains.kotlin"))
}

dependencies {
    implementation(project(":plugin"))
    implementation(project(":languages:java"))
}
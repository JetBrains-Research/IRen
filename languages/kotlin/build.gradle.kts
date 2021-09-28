intellij {
    this.plugins.set(listOf("java", "Kotlin"))
}

dependencies {
    implementation(project(":languages:common"))
    implementation(project(":languages:java"))
}
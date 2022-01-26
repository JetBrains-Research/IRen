intellij {
    this.plugins.set(listOf(Plugins.java))
}

dependencies {
//    implementation("org.tensorflow", "tensorflow", "1.13.1")
    implementation("com.github.javaparser:javaparser-core:3.0.0-alpha.4")
    implementation("net.razorvine", "pyrolite", "4.19")
    implementation("org.eclipse.mylyn.github", "org.eclipse.egit.github.core", "2.1.5")
}
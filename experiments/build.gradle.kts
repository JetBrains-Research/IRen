dependencies {
    implementation(project(":plugin"))
    implementation(project(":languages:common"))
    implementation(project(":languages:java"))
    implementation(project(":languages:kotlin"))
    implementation("me.tongfei:progressbar:0.9.2")
}

tasks {
//    (graph)ModelsEvaluator part
    runIde {
        val evaluatorToUse: String? by project
        val dataset: String? by project
        val saveDir: String? by project
        val languageFile: String? by project
        val ngramType: String? by project
        args = listOfNotNull(evaluatorToUse, dataset, saveDir, languageFile, ngramType)
        jvmArgs = listOf("-Djava.awt.headless=true")
        maxHeapSize = "28g"
    }
}
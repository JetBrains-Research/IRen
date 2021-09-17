dependencies {
    implementation(project(":plugin"))
}

tasks {
//    (graph)ModelsEvaluator part
    runIde {
        val evaluatorToUse: String? by project
        val dataset: String? by project
        val saveDir: String? by project
        val ngramContributorType: String? by project
        args = listOfNotNull(evaluatorToUse, dataset, saveDir, ngramContributorType)
        jvmArgs = listOf("-Djava.awt.headless=true")
        maxHeapSize = "8g"
    }
}
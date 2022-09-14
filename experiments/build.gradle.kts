intellij {
    this.plugins.set(listOf(Plugins.java, Plugins.kotlin, Plugins.python))
}

dependencies {
    implementation(project(":plugin"))
    implementation(project(":languages:common"))
    implementation(project(":languages:java"))
    implementation(project(":languages:kotlin"))
    implementation(project(":languages:python"))
    implementation("me.tongfei:progressbar:0.9.2")
}

tasks {
//    (graph)ModelsEvaluator part
    runIde {
        val evaluatorToUse: String? by project
        val dataset: String? by project
        val saveDir: String? by project
        val language: String? by project
        val ngramType: String? by project
        args = listOfNotNull(evaluatorToUse, dataset, saveDir, language, ngramType)
        jvmArgs = listOf(
            "-Djava.awt.headless=true",
//            "-verbose:gc",
//            "-XX:+PrintGCDetails",
//            "-Xlog:gc*:/home/igor/IdeaProjects/IRen/gc.log"
        )
        maxHeapSize = "32g"
    }
}
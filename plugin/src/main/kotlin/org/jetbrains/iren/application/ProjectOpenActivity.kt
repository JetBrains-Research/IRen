package org.jetbrains.iren.application

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.training.ModelBuilder

class ProjectOpenActivity : StartupActivity.Background {
    override fun runActivity(project: Project) {
        LanguageSupporter.removeRenameHandlers()
        ModelBuilder.prepareIRenModels(project)
    }
}
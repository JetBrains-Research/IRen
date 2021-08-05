package org.jetbrains.astrid.inspections.method

import com.intellij.codeInspection.InspectionToolProvider
import org.jetbrains.astrid.downloader.Downloader.checkArchive

class MethodNamesProvider : InspectionToolProvider {
    init {
        checkArchive()
    }

    override fun getInspectionClasses(): Array<Class<MethodNamesInspection>> {
        return arrayOf(MethodNamesInspection::class.java)
    }

}
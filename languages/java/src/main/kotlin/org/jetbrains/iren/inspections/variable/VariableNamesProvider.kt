package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.InspectionToolProvider

class VariableNamesProvider : InspectionToolProvider {
    override fun getInspectionClasses(): Array<Class<VariableNamesInspection>> {
        return arrayOf(VariableNamesInspection::class.java)
    }
}
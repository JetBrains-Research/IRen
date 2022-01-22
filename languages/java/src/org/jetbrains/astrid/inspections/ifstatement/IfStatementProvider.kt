package org.jetbrains.astrid.inspections.ifstatement

import com.intellij.codeInspection.InspectionToolProvider

class IfStatementProvider : InspectionToolProvider {
    override fun getInspectionClasses(): Array<Class<IfStatementInspection>> {
        return arrayOf(IfStatementInspection::class.java)
    }
}
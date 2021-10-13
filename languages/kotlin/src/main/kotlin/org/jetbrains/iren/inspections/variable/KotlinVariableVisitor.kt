package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.iren.IRenBundle
import org.jetbrains.iren.ModelManager
import org.jetbrains.iren.ModelStatsService
import org.jetbrains.iren.rename.IRenKotlinMemeberInplaceRenamer
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

class KotlinVariableVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitProperty(property: KtProperty) {
        val name = ModelManager.getName(holder.project, property.language)
        if (!ModelStatsService.getInstance().isUsable(name)
        ) return
        try {
            if (ConsistencyChecker.getInstance().isInconsistent(property.createSmartPointer())) {
                holder.registerProblem(
                    property.nameIdentifier ?: property,
                    IRenBundle.message("inspection.description.template"),
                    ProblemHighlightType.WEAK_WARNING,
                    RenameVariableQuickFix(property.createSmartPointer())
                )
            }
        } finally {
            super.visitProperty(property)
        }
    }
}
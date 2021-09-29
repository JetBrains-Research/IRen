package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.completion.ngram.slp.translating.Vocabulary
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.iren.utils.LanguageSupporter

class VariableNamesInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return LanguageSupporter.getVariableVisitor(holder.file.language, holder)
    }

    override fun showDefaultConfigurationOptions(): Boolean {
        return false
    }
}

fun isGoodPredictionList(variable: PsiNameIdentifierOwner, predictions: LinkedHashMap<String, Double>): Boolean {
    val varThreshold = predictions[variable.name]
    val vocabThreshold = predictions[Vocabulary.unknownCharacter]
    return (if (varThreshold != null) predictions.filterValues { v -> v > varThreshold } else predictions).size >= 5 &&
            (if (vocabThreshold != null) predictions.filterValues { v -> v > vocabThreshold } else predictions).isNotEmpty()
}
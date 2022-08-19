package org.jetbrains.iren

import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.iren.storages.VarNamePrediction

interface DOBFModelRunner {
    fun predict(variable: PsiNameIdentifierOwner): List<VarNamePrediction>
}
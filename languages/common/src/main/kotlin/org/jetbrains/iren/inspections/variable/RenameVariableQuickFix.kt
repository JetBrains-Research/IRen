package org.jetbrains.iren.inspections.variable

import org.jetbrains.iren.utils.RenameBundle
import org.jetbrains.kotlin.idea.quickfix.RenameIdentifierFix

class RenameVariableQuickFix : RenameIdentifierFix() {
    override fun getName() = RenameBundle.message("inspection.family.name")
}
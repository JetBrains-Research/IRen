// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.iren.rename

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler

open class IRenKotlinVariableInplaceRenameHandler : KotlinVariableInplaceRenameHandler() {
    override fun createRenamer(elementToRename: PsiElement, editor: Editor): VariableInplaceRenamer? {
        val currentElementToRename = elementToRename as PsiNameIdentifierOwner
        val currentName = currentElementToRename.nameIdentifier?.text ?: ""
        return IRenKotlinVariableInplaceRenamer(currentElementToRename, editor, currentName, currentName)
    }
}
package org.jetbrains.astrid.inspections

import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer

class SuggestionsStorage {
    companion object {
        private var map: HashMap<SmartPsiElementPointer<PsiNameIdentifierOwner>, Suggestion> = HashMap()

        fun getSuggestions(element: PsiNameIdentifierOwner): Suggestion? {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            val suggestion = map.get(pointer)
            if (suggestion != null) {
                return suggestion
            }
            return null
        }

        fun put(element: PsiNameIdentifierOwner, suggestion: Suggestion) {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            if (map.contains(pointer)) {
                map.replace(pointer, suggestion)
            } else {
                map.put(pointer, suggestion)
            }
        }

        fun contains(element: PsiNameIdentifierOwner): Boolean {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            return map.contains(pointer)
        }

        fun needRecalculate(element: PsiNameIdentifierOwner): Boolean {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            val suggestion = map.get(pointer) ?: return false
            return suggestion.needRecalculate
        }

        fun recalculateLater(element: PsiNameIdentifierOwner) {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            if (map.containsKey(pointer)) {
                val suggestion = map.get(pointer)
                if (suggestion != null) {
                    suggestion.setRecalculate()
                    map.replace(pointer, suggestion)
                }
            }
            return
        }

        fun ignore(element: PsiNameIdentifierOwner): Boolean {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            val suggestion = map.get(pointer) ?: return false
            return suggestion.ignore
        }

        fun setIgnore(element: PsiNameIdentifierOwner) {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            if (map.containsKey(pointer)) {
                val suggestion = map.get(pointer)
                if (suggestion != null) {
                    suggestion.setIgnore()
                    map.replace(pointer, suggestion)
                }
            }
            return
        }
    }
}
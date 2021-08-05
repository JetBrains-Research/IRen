package org.jetbrains.id.names.suggesting.inspections.variable

import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer

class ProbabilitiesStorage {
    companion object {
        private var map: HashMap<SmartPsiElementPointer<PsiNameIdentifierOwner>, Probability> = HashMap()

        fun getProbability(element: PsiNameIdentifierOwner): Probability? {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            val probability = map.get(pointer)
            if (probability != null) {
                return probability
            }
            return null
        }

        fun put(element: PsiNameIdentifierOwner, probability: Probability) {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            if (map.contains(pointer)) {
                map.replace(pointer, probability)
            } else {
                map.put(pointer, probability)
            }
        }

        fun contains(element: PsiNameIdentifierOwner): Boolean {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            return map.contains(pointer)
        }

        fun needRecalculate(element: PsiNameIdentifierOwner): Boolean {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            val probability = map.get(pointer) ?: return false
            return probability.needRecalculate
        }

        fun recalculateLater(element: PsiNameIdentifierOwner) {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            if (map.containsKey(pointer)) {
                val probability = map.get(pointer)
                if (probability != null) {
                    probability.setRecalculate()
                    map.replace(pointer, probability)
                }
            }
            return
        }

        fun isIgnored(element: PsiNameIdentifierOwner): Boolean {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            val probability = map.get(pointer) ?: return false
            return probability.ignore
        }

        fun setIgnore(element: PsiNameIdentifierOwner) {
            val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)
            if (map.containsKey(pointer)) {
                val probability = map.get(pointer)
                if (probability != null) {
                    probability.setIgnore()
                    map.replace(pointer, probability)
                }
            }
            return
        }
    }
}
package org.jetbrains.iren

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.statistics.JavaStatisticsManager
import com.intellij.refactoring.rename.NameSuggestionProvider
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class MyKotlinNameSuggestionProvider : NameSuggestionProvider {
    override fun getSuggestedNames(
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        result: MutableSet<String>
    ): SuggestedNameInfo? {
        if (element is KtCallableDeclaration) {
            val context = nameSuggestionContext ?: element.parent
            val target = when (element) {
                is KtProperty -> if (element.isLocal) KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE else KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY
                is KtParameter -> KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER
                is KtFunction -> KotlinNameSuggestionProvider.ValidatorTarget.FUNCTION
                else -> KotlinNameSuggestionProvider.ValidatorTarget.CLASS
            }
            val validator = Fe10KotlinNewDeclarationNameValidator(context, element, target, listOf(element))
            val names = SmartList<String>().apply {
//                val name = element.name
//                if (!name.isNullOrBlank()) {
//                    this += KotlinNameSuggester.getCamelNames(name, validator, name.first().isLowerCase())
//                }

                val callableDescriptor = element.unsafeResolveToDescriptor(BodyResolveMode.PARTIAL) as CallableDescriptor
                val type = callableDescriptor.returnType
                if (type != null && !type.isUnit() && !KotlinBuiltIns.isPrimitiveType(type)) {
                    this += Fe10KotlinNameSuggester.suggestNamesByType(type, validator)
                }
            }
            result += names

            if (element is KtProperty && element.isLocal) {
                for (ref in ReferencesSearch.search(element, LocalSearchScope(element.parent))) {
                    val refExpr = ref.element as? KtSimpleNameExpression ?: continue
                    val argument = refExpr.parent as? KtValueArgument ?: continue
                    val callElement = (argument.parent as? KtValueArgumentList)?.parent as? KtCallElement ?: continue
                    val resolvedCall = callElement.resolveToCall() ?: continue
                    val parameterName = (resolvedCall.getArgumentMapping(argument) as? ArgumentMatch)?.valueParameter?.name ?: continue
                    result += parameterName.asString()
                }
            }

            return object : SuggestedNameInfo(names.toTypedArray()) {
                override fun nameChosen(name: String?) {
                    val psiVariable = element.toLightElements().firstIsInstanceOrNull<PsiVariable>() ?: return
                    JavaStatisticsManager.incVariableNameUseCount(
                        name,
                        JavaCodeStyleManager.getInstance(element.project).getVariableKind(psiVariable),
                        psiVariable.name,
                        psiVariable.type
                    )
                }
            }
        }

        return null
    }
}

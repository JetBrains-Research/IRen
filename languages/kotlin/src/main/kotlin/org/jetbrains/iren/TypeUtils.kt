package org.jetbrains.iren

import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer.Companion.withOptions
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.types.KotlinType

fun getType(element: KtDeclaration) = element.type()

fun renderType(type: KotlinType): String = withOptions {
    withDefinedIn = false
    classifierNamePolicy = ClassifierNamePolicy.SHORT
    parameterNameRenderingPolicy = ParameterNameRenderingPolicy.ONLY_NON_SYNTHESIZED
    renderConstructorDelegation = false
}.renderType(type)
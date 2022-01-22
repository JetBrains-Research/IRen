package org.jetbrains.astrid.extractors.features

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import org.jetbrains.astrid.extractors.common.Common
import org.jetbrains.astrid.extractors.common.Common.INTERNAL_SEPARATOR
import org.jetbrains.astrid.extractors.common.MethodContent
import org.jetbrains.astrid.extractors.visitors.FunctionVisitor
import java.util.*

class FeatureExtractor(private var code: String) {

    private var compilationUnit: CompilationUnit
    private val parentTypeToAddChildId: List<String> = listOf(
            "AssignExpr", "ArrayAccessExpr", "FieldAccessExpr", "MethodCallExpr"
    )

    init {
        compilationUnit = parseFileWithRetries(this.code)
    }

    fun extractFeatures(): ArrayList<ProgramFeatures> {
        val functionVisitor = FunctionVisitor()
        functionVisitor.visit(compilationUnit, String::javaClass)
        val methods = functionVisitor.methodContents
        return generatePathFeatures(methods)
    }

    private fun parseFileWithRetries(code: String): CompilationUnit {
        val classPrefix = "public class A {"
        val classSuffix = "}"
        val parsed: CompilationUnit
        parsed = JavaParser.parse(classPrefix + code + classSuffix)
        return parsed
    }

    private fun generatePathFeatures(methods: ArrayList<MethodContent>): ArrayList<ProgramFeatures> {
        val methodsFeatures = ArrayList<ProgramFeatures>()
        for (content in methods) {
            val singleMethodFeatures = generatePathFeaturesForFunction(content)
            if (singleMethodFeatures.features.size != 0) {
                methodsFeatures.add(singleMethodFeatures)
            }
        }
        return methodsFeatures
    }

    private fun generatePathFeaturesForFunction(methodContent: MethodContent): ProgramFeatures {
        val functionLeaves = methodContent.leaves
        val programFeatures = ProgramFeatures(methodContent.name)

        for (i in functionLeaves.indices) {
            for (j in i + 1 until functionLeaves.size) {
                val separator = Common.EMPTY_STRING
                val path = generatePath(functionLeaves[i], functionLeaves[j], separator)
                if (path != Common.EMPTY_STRING) {
                    val source = functionLeaves[i].getUserData(Common.PROPERTY_KEY)
                    val target = functionLeaves[j].getUserData(Common.PROPERTY_KEY)
                    programFeatures.addFeature(source, path, target)
                }
            }
        }
        return programFeatures
    }

    private fun generatePath(source: Node, target: Node, separator: String): String {
        val maxLength = 8
        val maxWidth = 2

        val stringBuilder = StringJoiner(separator)
        val sourceStack = getTreeStack(source)
        val targetStack = getTreeStack(target)

        var commonPrefix = 0
        var currentSourceAncestorIndex = sourceStack.size - 1
        var currentTargetAncestorIndex = targetStack.size - 1
        while (currentSourceAncestorIndex >= 0 && currentTargetAncestorIndex >= 0
                && sourceStack[currentSourceAncestorIndex] === targetStack[currentTargetAncestorIndex]) {
            commonPrefix++
            currentSourceAncestorIndex--
            currentTargetAncestorIndex--
        }

        val pathLength = sourceStack.size + targetStack.size - 2 * commonPrefix
        if (pathLength > maxLength) {
            return Common.EMPTY_STRING
        }

        if (currentSourceAncestorIndex >= 0 && currentTargetAncestorIndex >= 0) {
            val pathWidth = targetStack[currentTargetAncestorIndex]
                    .getUserData(Common.CHILD_ID) - sourceStack[currentSourceAncestorIndex].getUserData(Common.CHILD_ID)
            if (pathWidth > maxWidth) {
                return Common.EMPTY_STRING
            }
        }

        for (i in 0 until sourceStack.size - commonPrefix) {
            val currentNode = sourceStack[i]
            var childId = Common.EMPTY_STRING
            val parentRawType = currentNode.parentNode.getUserData(Common.PROPERTY_KEY).rawType
            if (i == 0 || parentTypeToAddChildId.contains(parentRawType)) {
                childId = saturateChildId(currentNode.getUserData(Common.CHILD_ID))
                        .toString()
            }
            stringBuilder.add(String.format("%s%s%s",
                    currentNode.getUserData(Common.PROPERTY_KEY).getType(true), childId, INTERNAL_SEPARATOR))
        }

        val commonNode = sourceStack[sourceStack.size - commonPrefix]
        var commonNodeChildId = Common.EMPTY_STRING
        val parentNodeProperty = commonNode.parentNode.getUserData(Common.PROPERTY_KEY)
        var commonNodeParentRawType = Common.EMPTY_STRING
        if (parentNodeProperty != null) {
            commonNodeParentRawType = parentNodeProperty.rawType
        }
        if (parentTypeToAddChildId.contains(commonNodeParentRawType)) {
            commonNodeChildId = saturateChildId(commonNode.getUserData(Common.CHILD_ID))
                    .toString()
        }
        stringBuilder.add(String.format("%s%s",
                commonNode.getUserData(Common.PROPERTY_KEY).getType(true), commonNodeChildId))

        for (i in targetStack.size - commonPrefix - 1 downTo 0) {
            val currentNode = targetStack[i]
            var childId = Common.EMPTY_STRING
            if (i == 0 || parentTypeToAddChildId.contains(currentNode.getUserData(Common.PROPERTY_KEY).rawType)) {
                childId = saturateChildId(currentNode.getUserData(Common.CHILD_ID))
                        .toString()
            }
            stringBuilder.add(String.format("%s%s%s", INTERNAL_SEPARATOR,
                    currentNode.getUserData(Common.PROPERTY_KEY).getType(true), childId))
        }

        return stringBuilder.toString()
    }

    private fun saturateChildId(childId: Int): Int {
        return Math.min(childId, Integer.MAX_VALUE)
    }

    private fun getTreeStack(node: Node): ArrayList<Node> {
        val upStack = ArrayList<Node>()
        var current: Node? = node
        while (current != null) {
            upStack.add(current)
            current = current.parentNode
        }
        return upStack
    }
}
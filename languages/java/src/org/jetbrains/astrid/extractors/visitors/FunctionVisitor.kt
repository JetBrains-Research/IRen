package org.jetbrains.astrid.extractors.visitors

import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import org.jetbrains.astrid.extractors.common.Common
import org.jetbrains.astrid.extractors.common.MethodContent
import java.util.*

class FunctionVisitor : VoidVisitorAdapter<Any>() {
    val methodContents = ArrayList<MethodContent>()

    override fun visit(node: MethodDeclaration, arg: Any) {
        visitMethod(node, arg)
        super.visit(node, arg)
    }

    private fun visitMethod(node: MethodDeclaration, arg: Any) {
        val leavesCollectorVisitor = LeavesVisitor()
        leavesCollectorVisitor.visitDepthFirst(node)
        val leaves = leavesCollectorVisitor.leaves
        val normalizedMethodName = Common.normalizeName(node.name, Common.BLANK)
        val splitNameParts = Common.splitToSubtokens(node.name)
        var splitName = normalizedMethodName
        if (splitNameParts.size > 0) {
            splitName = splitNameParts.joinToString(Common.INTERNAL_SEPARATOR)
        }

        if (node.body != null) {
            methodContents.add(MethodContent(leaves, splitName, getMethodLength(node.body.toString())))
        }
    }

    private fun getMethodLength(code: String): Long {
        var cleanCode = code.replace("\r\n".toRegex(), "\n").replace("\t".toRegex(), " ")
        if (cleanCode.startsWith("{\n"))
            cleanCode = cleanCode.substring(3).trim { it <= ' ' }
        if (cleanCode.endsWith("\n}"))
            cleanCode = cleanCode.substring(0, cleanCode.length - 2).trim { it <= ' ' }
        return if (cleanCode.isEmpty()) {
            0
        } else Arrays.stream(cleanCode.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                .filter { line ->
                    line.trim { it <= ' ' } != "{" && line.trim { it <= ' ' } != "}"
                            && line.trim { it <= ' ' } != ""
                }.filter { line ->
                    !line.trim { it <= ' ' }
                            .startsWith("/") && !line.trim { it <= ' ' }.startsWith("*")
                }.count()
    }
}

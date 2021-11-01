package org.jetbrains.astrid.extractors.visitors

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.comments.Comment
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.visitor.TreeVisitor
import org.jetbrains.astrid.extractors.common.Common
import org.jetbrains.astrid.extractors.features.Property

class LeavesVisitor : TreeVisitor() {
    val leaves = ArrayList<Node>()

    override fun process(node: Node) {
        if (node is Comment) {
            return
        }
        var isLeaf = false
        val isGenericParent = isGenericParent(node)
        if (hasNoChildren(node) && isNotComment(node)) {
            if (!node.toString().isEmpty() && ("null" != node.toString() || node is NullLiteralExpr)) {
                leaves.add(node)
                isLeaf = true
            }
        }
        val childId = getChildId(node)
        node.setUserData(Common.CHILD_ID, childId)
        val property = Property(node, isLeaf, isGenericParent)
        node.setUserData(Common.PROPERTY_KEY, property)
    }

    private fun isGenericParent(node: Node): Boolean {
        return (node is ClassOrInterfaceType
                && node.typeArguments != null
                && node.typeArguments.size > 0)
    }

    private fun hasNoChildren(node: Node): Boolean {
        return node.childrenNodes.size == 0
    }

    private fun isNotComment(node: Node): Boolean {
        return node !is Comment && node !is Statement
    }

    private fun getChildId(node: Node): Int {
        val parent = node.parentNode
        val parentsChildren = parent.childrenNodes
        var childId = 0
        for (child in parentsChildren) {
            if (child.range == node.range) {
                return childId
            }
            childId++
        }
        return childId
    }
}

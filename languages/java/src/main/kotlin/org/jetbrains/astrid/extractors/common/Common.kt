package org.jetbrains.astrid.extractors.common

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.UserDataKey
import org.jetbrains.astrid.extractors.features.Property

object Common {
    val PROPERTY_KEY: UserDataKey<Property> = object : UserDataKey<Property>() {
    }

    val CHILD_ID: UserDataKey<Int> = object : UserDataKey<Int>() {
    }
    const val EMPTY_STRING = ""
    private const val METHOD_DECLARATION = "MethodDeclaration"
    private const val NAME_EXPR = "NameExpr"
    const val BLANK = "BLANK"
    const val MAX_LABEL_LENGTH = 50
    const val METHOD_NAME = "METHOD_NAME"
    const val INTERNAL_SEPARATOR = "|"

    fun normalizeName(original: String, defaultString: String): String {
        val normalizedString = original.toLowerCase().replace("\\\\n".toRegex(), "")
                .replace("//s+".toRegex(), "")
                .replace("[\"',]".toRegex(), "")
                .replace("\\P{Print}".toRegex(), "")
        val stripped = normalizedString.replace("[^A-Za-z]".toRegex(), "")
        return when {
            stripped.isEmpty() -> {
                val carefulStripped = normalizedString.replace(" ".toRegex(), "_")
                if (carefulStripped.isEmpty()) {
                    defaultString
                } else {
                    carefulStripped
                }
            }
            else -> stripped
        }
    }

    fun isMethod(node: Node, type: String?): Boolean {
        val parentProperty = node.parentNode.getUserData(PROPERTY_KEY) ?: return false
        val parentType = parentProperty.type
        return NAME_EXPR.equals(type) && METHOD_DECLARATION.equals(parentType)
    }

    fun splitToSubtokens(str: String): ArrayList<String> {
        val s = str.trim { it <= ' ' }
        val strings = ArrayList<String>()
        s.split("(?<=[a-z])(?=[A-Z])|_|[0-9]|(?<=[A-Z])(?=[A-Z][a-z])|\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray().forEach {
                    if (it.isNotEmpty()) {
                        val normalizedName = normalizeName(it, EMPTY_STRING)
                        if (normalizedName.isNotEmpty()) {
                            strings.add(normalizedName)
                        }
                    }
                }
        return strings
    }
}

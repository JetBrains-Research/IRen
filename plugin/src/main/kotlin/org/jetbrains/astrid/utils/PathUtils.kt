package org.jetbrains.astrid.utils

import org.jetbrains.astrid.extractors.features.Extractor

object PathUtils {
    private fun getContextsFromMethodBody(methodBody: String): String {
        val extractor = Extractor(methodBody)
        return extractor.processCodeBlock()
    }

    fun getCombinedPaths(methodBody: String): String {
        val pathContexts: String = getContextsFromMethodBody(methodBody)
        if (pathContexts.isEmpty()) return ""
        val parts = pathContexts.split(' ')
        val methodName = parts[0]
        val currentResultLineParts = arrayListOf(methodName)
        val contexts = parts.subList(1, parts.size)
        val maxContextsCount = 1000
        var contextParts: List<String>
        var resultLine = ""
        val list = if (contexts.size < maxContextsCount) contexts else contexts.subList(0, maxContextsCount - 1)
        for (context: String in list) {
            contextParts = context.split(',')
            currentResultLineParts.add("${contextParts[0]},${contextParts[1]},${contextParts[2]}")
        }
        currentResultLineParts.forEach { part ->
            resultLine += " $part"
        }
        val spaceCount = maxContextsCount - contexts.size
        for (i in 0..spaceCount - 2) {
            resultLine += " "
        }
        return resultLine
    }
}
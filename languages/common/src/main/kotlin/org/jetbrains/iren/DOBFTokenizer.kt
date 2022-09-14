package org.jetbrains.iren

import org.jetbrains.iren.utils.StringUtils

open class DOBFTokenizer {
    open val token2char = mapOf(
        "STOKEN00" to "//",
        "STOKEN01" to "/*",
        "STOKEN02" to "*/",
        "STOKEN03" to "/**",
        "STOKEN04" to "**/",
        "STOKEN05" to "\"\"\"",
        "STOKEN06" to "\\n",
        "STOKEN07" to "\\r",
        "STOKEN08" to ";",
        "STOKEN09" to "{",
        "STOKEN10" to "}",
        "STOKEN11" to "\\\'",
        "STOKEN12" to "\\\"",
        "STOKEN13" to "\\\\",
    )
    private val char2token: Map<String, String> by lazy { token2char.map { (k, v) -> v to " $k " }.toMap() }

    val UTOKEN = "UTOKEN"
    val XTOKEN = "XTOKEN"

    open fun process(text: String): String {
        return text.replace("\\u", UTOKEN)
            .replace("\\x", XTOKEN)
            .replace("\r", "")
            .replace("\\r", "")
            .replace(" ", " ${StringUtils.SPACE_TOKEN} ")
            .replace("\n", " ${StringUtils.STR_NEW_LINE_TOKEN} ")
            .replace("\t", " ${StringUtils.STR_TAB_TOKEN} ")
            .replaceTokens(char2token)
            .processWithSTokens()
            .replace(" +".toRegex(), " ")
            .replace("(\\D)(\\p{Punct})".toRegex(), "$1 $2 ")
            .replace("(\\p{Punct})(\\D)".toRegex(), " $1 $2")
            .replace("(\\p{S})".toRegex(), " $1 ")
            .replace(" +".toRegex(), " ")
            .replaceTokens(token2char)
            .replace(UTOKEN, "\\u")
            .replace(XTOKEN, "\\x")
            .trim()
    }

    open fun String.processWithSTokens(): String {
        return this
    }
}

fun String.replaceTokens(map: Map<String, String>): String {
    var result = this
    for ((k, v) in map) {
        result = result.replace(k, v)
    }
    return result
}
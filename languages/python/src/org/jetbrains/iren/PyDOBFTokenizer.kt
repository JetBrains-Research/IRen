package org.jetbrains.iren

class PyDOBFTokenizer : DOBFTokenizer() {
    override val token2char = mapOf(
        "STOKEN00" to "#",
        "STOKEN1" to "\\n",
        "STOKEN2" to "\"\"\"",
        "STOKEN3" to "'''",
    )

    override fun String.processWithSTokens(): String {
        val res = this.replace("^[ru]".toRegex(), "")
        return if (res.contains("\'")) res
        else res.replace("^\"".toRegex(), "\' ")
            .replace("\"$".toRegex(), " \'")
            .replace("^STOKEN2".toRegex(), "STOKEN3 ")
            .replace("STOKEN2$".toRegex(), " STOKEN3")
            .replace("^f\"".toRegex(), "f\'")
    }
}
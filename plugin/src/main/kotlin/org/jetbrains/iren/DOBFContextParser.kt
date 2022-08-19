package org.jetbrains.iren

import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.iren.bpe.FastBPEAnalyzer
import org.jetbrains.iren.storages.Context
import org.jetbrains.iren.storages.PersistentVocabulary
import java.nio.file.Path

const val EOS_TOKEN = "</s>"
const val VAR_TOKEN = "VAR_0"

class DOBFContextParser(modelDir: Path, private val maxSequenceLength: Int = 512) {
    val bpe = FastBPEAnalyzer(modelDir.resolve("codes").toFile())
    val vocab = PersistentVocabulary.readFromPath(modelDir.resolve("vocab.txt"))
    val eosIdx = vocab.toIndex(EOS_TOKEN)

    fun getContext(variable: PsiNameIdentifierOwner): List<Int> {
//        Data preparation
        val context = (LanguageSupporter.getInstance(variable.language)?.getContext(variable, true, false, false)
            ?: return listOf()).with(VAR_TOKEN)

//        Apply BPE to context
        val bpeTokens = getBpeTokens(context)

//        String tokens to vocab indices
        val idxs = getIndices(bpeTokens)
        return truncateIdxs(idxs)
    }

    private fun getBpeTokens(context: Context<String>): String {
        val toBpe = ArrayList<String>()
        val tokensLists = context.splitByUsages()
        for (tokensList in tokensLists) {
            toBpe.add(bpe.applyBpe(java.lang.String.join(" ", tokensList)))
        }
        val bpeTokens = java.lang.String.join(" $VAR_TOKEN ", toBpe)
        return bpeTokens
    }

    private fun getIndices(bpeTokens: String): List<Int> {
        val tokensList = mutableListOf("</s>")
        tokensList.addAll(bpeTokens.split(" "))
        tokensList.add("</s>")
        return vocab.toIndices(tokensList)
    }

    /**
     * Truncate sequence of indices to max sequence length of transformer encoder
     **/
    private fun truncateIdxs(idxs: List<Int>): List<Int> {
        if (idxs.size <= maxSequenceLength) return idxs
        val (minIdx, maxIdx) = getMinMaxIdxsOfVar(idxs)
        val leftOffset = 100
        val (left, right) = if (maxIdx - minIdx > maxSequenceLength - leftOffset * 2) {
            minIdx - leftOffset to minIdx - leftOffset + maxSequenceLength
        } else {
            val meanIdx = (minIdx + maxIdx) / 2
            meanIdx - maxSequenceLength / 2 to meanIdx + maxSequenceLength / 2
        }
        return idxs.subList(maxOf(0, left), minOf(idxs.size, right) - 1)
    }

    private fun getMinMaxIdxsOfVar(idxs: List<Int>): Pair<Int, Int> {
        val varIdx = vocab.toIndex(VAR_TOKEN)
        return idxs.indexOf(varIdx) to idxs.lastIndexOf(varIdx)
    }
}
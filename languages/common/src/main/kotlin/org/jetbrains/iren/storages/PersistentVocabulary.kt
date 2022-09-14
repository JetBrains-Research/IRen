package org.jetbrains.iren.storages

import com.intellij.openapi.util.ShutDownTracker
import com.intellij.util.io.PersistentStringEnumerator
import com.intellij.util.io.exists
import com.intellij.util.io.size
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import java.io.*
import java.nio.file.Path
import kotlin.io.path.readLines

class PersistentVocabulary(
    val vocabularyPath: Path,
    val unkToken: String = unknownCharacter
) : Vocabulary() {
    val enumerator: PersistentStringEnumerator = PersistentStringEnumerator(vocabularyPath, true)
    val enum2idx: Int2IntOpenHashMap
    val idx2enum: IntArray
    val unkIdx: Int

    init {
        idx2enum = loadIdx2enum()
        enum2idx = idx2enum.mapIndexed { idx, enum -> enum to idx }.toMap(Int2IntOpenHashMap())
        unkIdx = enum2idx[enumerator.tryEnumerate(unkToken)]
        ShutDownTracker.getInstance().registerShutdownTask(enumerator::close)
    }

    override fun size() = enum2idx.size

    override fun toIndex(token: String) = try {
        enum2idx[enumerator.tryEnumerate(token)]
    } catch (e: IOException) {
        unkIdx
    }

    override fun toWord(index: Int) = enumerator.valueOf(idx2enum[index]) ?: unkToken

    override fun store(token: String, count: Int) = -1

    override fun getCount(token: String): Int? = null

    private fun loadIdx2enum(): IntArray {
        DataInputStream(BufferedInputStream(FileInputStream(getEnum2idxFile(vocabularyPath))))
            .use {
                val size = it.readInt()
                val res = IntArray(size)
                for (i in 0 until size) {
                    res[i] = it.readInt()
                }
                return res
            }
    }

    companion object {
        @JvmStatic
        @kotlin.jvm.Throws(IOException::class)
        fun saveVocabulary(vocabulary: Vocabulary, path: Path) {
            save(vocabulary.words, path)
        }

        fun readFromFile(path: Path, vocabularyPath: Path = path.parent.resolve("vocabulary"), unkToken: String = unknownCharacter): PersistentVocabulary {
            if (vocabularyPath.exists() && vocabularyPath.size() > 0) return PersistentVocabulary(vocabularyPath, unkToken)
            save(path.readLines(), vocabularyPath)
            return PersistentVocabulary(vocabularyPath, unkToken)
        }

        private fun save(words: List<String>, path: Path) {
            val enumerator = PersistentStringEnumerator(path, true)
            val idx2enum = IntArray(words.size)
            for ((idx, word) in words.withIndex()) {
                val enum = enumerator.enumerate(word)
                idx2enum[idx] = enum
            }
            enumerator.close()
            saveIdx2enum(idx2enum, path)
        }

        private fun saveIdx2enum(idx2enum: IntArray, path: Path) =
            DataOutputStream(BufferedOutputStream(FileOutputStream(getEnum2idxFile(path))))
                .use {
                    it.writeInt(idx2enum.size)
                    for (enum in idx2enum) it.writeInt(enum)
                }

        private fun getEnum2idxFile(path: Path) = "$path.idx2enum"
    }
}
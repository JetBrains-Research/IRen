/* MIT License

 Copyright (c) 2018 SLP-team

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.
 */

package org.jetbrains.iren.storages

import gnu.trove.TObjectIntHashMap
import java.io.Serializable
import java.util.*

/**
 * Translation (to integers) is the second step (after Lexing) before any modeling takes place.
 * The this is global (static) and is open by default; it can be initialized through
 * the [thisRunner] class or simply left open to be filled by the modeling code
 * (as has been shown to be more appropriate for modeling source code).
 * <br></br>
 * *Note:* the counts in this class are for informative purposes only:
 * these are not (to be) used by any model nor updated with training.
 *
 * @author Vincent Hellendoorn
 */
open class Vocabulary : Serializable {

    val wordIndices: TObjectIntHashMap<String> = TObjectIntHashMap()
    val words: MutableList<String> = ArrayList()
    val counts: MutableList<Int> = ArrayList()

    private var closed: Boolean = false

    private var checkPoint: Int = 0

    init {
        addUnk()
    }

    private fun addUnk() {
        wordIndices.put(unknownCharacter, 0)
        words.add(unknownCharacter)
        counts.add(0)
    }

    open fun size(): Int {
        return words.size
    }

    fun close() {
        closed = true
    }

    fun open() {
        closed = false
    }

    fun setCheckpoint() {
        checkPoint = words.size
    }

    fun restoreCheckpoint() {
        for (i in words.size downTo checkPoint + 1) {
            counts.removeAt(counts.size - 1)
            val word = words.removeAt(words.size - 1)
            wordIndices.remove(word)
        }
    }

    open fun store(token: String, count: Int = 1): Int {
        var index = wordIndices.get(token)
        if (index == 0 && token != unknownCharacter) {
            index = wordIndices.size()
            wordIndices.put(token, index)
            words.add(token)
            counts.add(count)
        } else {
            counts[index] = count
        }
        return index
    }

    fun toIndices(tokens: Sequence<String>): Sequence<Int> {
        return tokens.map { toIndex(it) }
    }

    fun toIndices(tokens: List<String>): List<Int> {
        return tokens.map { toIndex(it) }
    }

    open fun toIndex(token: String): Int {
        var index: Int = wordIndices.get(token)
        if (index == 0) {
            if (closed) {
                return wordIndices.get(unknownCharacter)
            } else {
                index = wordIndices.size()
                wordIndices.put(token, index)
                words.add(token)
                counts.add(1)
            }
        }
        return index
    }

    open fun getCount(token: String): Int? {
        val index = wordIndices.get(token)
        return if (index == 0) 0 else counts[index]
    }

    fun toWords(indices: Sequence<Int>): Sequence<String> {
        return indices.map { toWord(it) }
    }

    fun toWords(indices: List<Int>): List<String> {
        return indices.map { toWord(it) }
    }

    open fun toWord(index: Int): String {
        return words[index]
    }

    fun clear() {
        wordIndices.clear()
        words.clear()
        counts.clear()
        open()
        addUnk()
    }

    companion object {

        const val unknownCharacter = "<unknownCharacter>"
        const val beginOfString = "<s>"
        const val endOfString = "</s>"
    }
}
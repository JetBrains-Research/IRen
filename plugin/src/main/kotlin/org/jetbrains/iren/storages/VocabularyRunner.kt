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

import java.io.*
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

object VocabularyRunner {

    private val PRINT_FREQ = 1000000
    private var cutOff = 0

    /**
     * Set counts cut-off so that only events seen >= cutOff are considered. Default: 0, which includes every seen token
     * <br></br>
     * *Note:* this has been shown to give a distorted perspective on models of particularly source code,
     * but may be applicable in some circumstances
     *
     * @param cutOff The minimum number of counts of an event in order for it to be considered.
     */
    fun cutOff(cutOff: Int) {
        var cutOff = cutOff
        if (cutOff < 0) {
            println("VocabularyBuilder.cutOff(): negative cut-off given, set to 0 (which includes every token)")
            cutOff = 0
        }
        VocabularyRunner.cutOff = cutOff
    }


    /**
     * Writes vocabulary to file with one entry per line. Format: tab-separated count, index and word.
     * <br></br>
     * Note: count is informative only and is not updated during training!
     *
     * @param file File to write vocabulary to.
     */
    fun write(vocabulary: Vocabulary, file: File) {
        BufferedWriter(OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8)).use { fw ->
            for (i in 0 until vocabulary.size()) {
                val count = vocabulary.counts[i]
                val word = vocabulary.words[i]
                fw.append(count.toString() + "\t" + i + "\t" + word + "\n")
            }
        }
    }

    /**
     * Read vocabulary from file, where it is assumed that the vocabulary is written as per [VocabularyRunner.write]:
     * tab-separated, having three columns per line: count, index and token (which may contain tabs))
     * <br></br>*Note:*: index is assumed to be strictly incremental starting at 0!
     *
     * @return vocabulary
     */
    fun read(file: File): Vocabulary {
        return read(file, Vocabulary())
    }

    fun read(file: File, vocabulary: Vocabulary): Vocabulary {
        readLines(file)!!.stream()
            .map { x: String ->
                x.split("\t".toRegex(),
                    limit = 3).toTypedArray()
            }
            .filter { x: Array<String> ->
                x[0].toInt() >= cutOff
            }
            .forEachOrdered { split: Array<String> ->
                val count = split[0].toInt()
                val index = split[1].toInt()
                if (index > 0 && index != vocabulary.size()) {
                    println("VocabularyRunner.read(): non-consecutive indices while reading vocabulary!")
                }
                val token = split[2]
                vocabulary.store(token, count)
            }
        return vocabulary
    }

    private fun readLines(file: File): List<String>? {
        try {
            val dec = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.IGNORE)
            BufferedReader(Channels.newReader(FileChannel.open(file.toPath()), dec, -1)).use { br ->
                return br.lines()
                    .collect(Collectors.toList())
            }
        } catch (e: IOException) {
            System.err.println("VocabularyManager.readLines(): Files.lines failed, reading full file using BufferedReader instead")
            val lines: MutableList<String> = ArrayList()
            try {
                BufferedReader(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)).use { br ->
                    var line: String
                    while (br.readLine().also { line = it } != null) {
                        lines.add(line)
                    }
                }
            } catch (e2: IOException) {
                e2.printStackTrace()
                return null
            }
            return lines
        } catch (e: UncheckedIOException) {
            System.err.println("VocabularyManager.readLines(): Files.lines failed, reading full file using BufferedReader instead")
            val lines: MutableList<String> = ArrayList()
            try {
                BufferedReader(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)).use { br ->
                    var line: String
                    while (br.readLine().also { line = it } != null) {
                        lines.add(line)
                    }
                }
            } catch (e2: IOException) {
                e2.printStackTrace()
                return null
            }
            return lines
        }
    }
}
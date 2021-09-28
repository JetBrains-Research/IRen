package org.jetbrains.iren;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.completion.ngram.slp.translating.VocabularyRunner;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class VocabularyManager {
    public static int cutOff = 0;

    /**
     * Read vocabulary from file, where it is assumed that the vocabulary is written as per {@link VocabularyRunner#write(Vocabulary, File)}:
     * tab-separated, having three columns per line: count, index and token (which may contain tabs))
     * <br /><em>Note:</em>: index is assumed to be strictly incremental starting at 0!
     *
     * @return vocabulary
     */
    public static Vocabulary read(File file) {
        return read(file, new Vocabulary());
    }

    public static Vocabulary read(File file, Vocabulary vocabulary) {
        readLines(file).stream()
                .map(x -> x.split("\t", 3))
                .filter(x -> Integer.parseInt(x[0]) >= cutOff)
                .forEachOrdered(split -> {
                    int count = Integer.parseInt(split[0]);
                    int index = Integer.parseInt(split[1]);
                    if (index > 0 && index != vocabulary.size()) {
                        System.out.println("VocabularyRunner.read(): non-consecutive indices while reading vocabulary!");
                    }
                    String token = split[2];
                    vocabulary.store(token, count);
                });
        return vocabulary;
    }

    public static List<String> readLines(File file) {
        try {
            CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.IGNORE);
            try (BufferedReader br = new BufferedReader(Channels.newReader(FileChannel.open(file.toPath()), dec, -1))) {
                return br.lines().collect(Collectors.toList());
            }
        } catch (IOException | UncheckedIOException e) {
            System.err.println("Reader.readLines(): Files.lines failed, reading full file using BufferedReader instead");
            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException e2) {
                e2.printStackTrace();
                return null;
            }
            return lines;
        }
    }

    public static void clear(@NotNull Vocabulary myVocabulary) {
        myVocabulary.getWordIndices().clear();
        myVocabulary.getWords().clear();
        myVocabulary.getCounts().clear();
        myVocabulary.open();
        myVocabulary.store(Vocabulary.unknownCharacter, 0);
    }
}

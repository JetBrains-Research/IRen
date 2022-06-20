package org.jetbrains.iren.bpe;

import com.intellij.openapi.util.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;

// Copied from https://github.com/mitre/jfastbpe
public class FastBPEAnalyzer {

    final public static String K_END_WORD = "</w>";
    final public static int K_END_WORD_LENGTH = K_END_WORD.length();
    final public static String K_TOKEN_DELIM = "@@";
    final public static int K_TOKEN_DELIM_LENGTH = K_TOKEN_DELIM.length();

    final private HashMap<Pair<String, String>, Integer> codes;
    final private HashMap<String, Pair<String, String>> reversedCodes;
    final private Map<String, Integer> vocab;

    final private int numThreads;

    public FastBPEAnalyzer(final File codesFile, final File vocabFile, final int numThreads) throws IOException {
        Pair<HashMap<Pair<String, String>, Integer>, HashMap<String, Pair<String, String>>> codeMappings = readCodes(codesFile);
        codes = codeMappings.first;
        reversedCodes = codeMappings.second;
        vocab = vocabFile == null ? new HashMap<>() : readVocab(vocabFile);
        this.numThreads = numThreads;
    }

    public FastBPEAnalyzer(final File codesFile, final int numThreads) throws IOException {
        this(codesFile, null, numThreads);
    }

    public FastBPEAnalyzer(final File codesFile) throws IOException {
        this(codesFile, null, 4);
    }

    private Map<String, Integer> readVocab(final File vocabFile) throws IOException {
        Map<String, Integer> vocab = new HashMap<>();
        Files.lines(vocabFile.toPath()).filter(l -> !"".equals(l)).forEach(line -> {
            String[] splits = line.split(" ");
            assert splits.length == 2 : line;
            String word = splits[0];
            Integer count = Integer.parseInt(splits[1]);
            assert !vocab.containsKey(word) : String.format("Vocab doesn't have word %s", word);
            vocab.put(word, count);
        });
        return vocab;
    }

    private Pair<HashMap<Pair<String, String>, Integer>, HashMap<String, Pair<String, String>>> readCodes(
            final File codesFile
    ) throws IOException {
        HashMap<Pair<String, String>, Integer> codes = new HashMap<>();
        HashMap<String, Pair<String, String>> reversedCodes = new HashMap<>();

        Files.lines(codesFile.toPath()).filter(l -> !"".equals(l)).forEach(line -> {
            String[] splits = line.split(" ");
            Pair<String, String> pair = Pair.create(splits[0], splits[1]);
            String concat = splits[0] + splits[1];

            assert !codes.containsKey(pair);
            assert !reversedCodes.containsKey(concat);

            codes.put(pair, codes.size());
            reversedCodes.put(concat, pair);
        });

        return Pair.create(codes, reversedCodes);
    }

    String processBpe(Vector<String> subwords) {
        Vector<String> subwordsCopy = new Vector<>(subwords);
        // merge subWords as much as possible
        Vector<String> newSubwords;
        while (subwordsCopy.size() > 1) {
            // find the best pair
            int bestPairId = -1;

            Pair<Pair<String, String>, Integer> bestPair = null;
            for (int i = 0; i < subwordsCopy.size() - 1; i++) {
                Pair<String, String> pair = Pair.create(subwordsCopy.elementAt(i), subwordsCopy.elementAt(i + 1));
                int pairRank = codes.getOrDefault(pair, -1);
                if (pairRank >= 0 && (bestPairId == -1 || bestPair.second > pairRank)) {
                    bestPair = Pair.create(pair, pairRank);
                    bestPairId = i;
                }
            }

            // if we cannot merge anything, stop
            if (bestPairId == -1) {
                break;
            }
            // otherwise, merge subWords
            boolean justMerged = false;
            newSubwords = new Vector<>();
            for (int i = 0; i < subwordsCopy.size(); i++) {
                if ((i + 1 < subwordsCopy.size()) && (!justMerged) &&
                        subwordsCopy.elementAt(i).equals(bestPair.first.first) &&
                        subwordsCopy.elementAt(i + 1).equals(bestPair.first.second)) {
                    newSubwords.add(subwordsCopy.elementAt(i) + subwordsCopy.elementAt(i + 1));
                    justMerged = true;
                } else {
                    if (!justMerged) {
                        newSubwords.add(subwordsCopy.elementAt(i));
                    }
                    justMerged = false;
                }
            }
            subwordsCopy = newSubwords;
        }
        // check that we are only using words in the dictionary
        if (vocab.size() > 0) {
            newSubwords = new Vector<>();
            limitVocab(subwords, newSubwords);
            subwords = newSubwords;
        }

        // concat subWords
        StringBuilder result = new StringBuilder();
        for (String x : subwordsCopy) {
            result.append(x).append(K_TOKEN_DELIM).append(" ");
        }

        // "</w>@@ "
        int endLength = K_END_WORD_LENGTH + K_TOKEN_DELIM_LENGTH + 1;
        return result.toString().substring(0, result.toString().length() - endLength);
    }

    void limitVocab(Vector<String> subwords, Vector<String> newSubwords) {
        String query = "";
        for (int i = 0; i < subwords.size(); i++) {
            boolean isFinal = i == subwords.size() - 1;
            String subword = subwords.elementAt(i);
            if (isFinal) {
                query = subword.substring(0, subword.length() - 4);
            } else {
                query = subword + K_TOKEN_DELIM;
            }
            if (!vocab.containsKey(query)) {
                decompose(subword, newSubwords, isFinal);
            } else {
                newSubwords.add(subword);
            }
        }
    }

    private Map<String, Vector<String>> tokenizeString(final Map<String, Integer> wordCount) {
        Map<String, Vector<String>> words = new HashMap<>();
        for (String word : wordCount.keySet()) {
            words.put(word, new Vector<>());
            int pos = 0;
            int realLength = 0;
            int lastStart = 0;

            while (pos < word.length()) {
                boolean newChar = (word.charAt(pos) & 0xc0) != 0x80; // not a continuation byte
                realLength += (newChar ? 1 : 0);
                // new token
                if (newChar && pos > 0) {
                    String new_token = word.substring(lastStart, lastStart + pos - lastStart);
                    words.get(word).add(new_token);
                    lastStart = pos;
                }
                pos++;
            }
            String new_token = word.substring(lastStart) + K_END_WORD;
            words.get(word).add(new_token);
        }

        return words;
    }

    private void decompose(String s, Vector<String> newSubwords, boolean isFinal) {
        Pair<String, String> d = reversedCodes.get(s);
        if (d == null) {
            // TODO this whole block below is just some sanity check
            // if we cannot un-merge a subword, it has to be a char
            String s2 = isFinal ? s.substring(0, s.length() - K_END_WORD_LENGTH) : s;
            int count = 0;
            for (int j = 0; j < s2.length(); j++) {
                if ((s2.charAt(j) & 0xc0) != 0x80) {
                    count++;
                }
            }
            assert(count == 1);
            newSubwords.add(s);
            return;
        }
        String token1 = d.first;
        if (vocab.get(token1 + K_TOKEN_DELIM) == null) {
            decompose(token1, newSubwords, false);
        } else {
            newSubwords.add(token1);
        }
        String token2 = d.second;
        String query = token2 + K_TOKEN_DELIM;
        if (isFinal) {
            query = token2.substring(0, token2.length() - K_END_WORD_LENGTH);
        }
        if (vocab.get(query) == null) {
            decompose(token2, newSubwords, isFinal);
        } else {
            newSubwords.add(token2);
        }
    }

    private Map<String, Integer> readWords(final String line) {
        Map<String, Integer> wordCount = new HashMap<>();
        String currWord = "";
        for (int i = 0; i < line.length(); ++i) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\n') {
                if (currWord.length() == 0) {
                    return wordCount;
                }
                Integer count = wordCount.getOrDefault(currWord, 0);
                wordCount.put(currWord, count + 1);
                currWord = "";
            } else {
                currWord += c;
            }
        }
        return wordCount;
    }

    private void calculateInThread(int thisThread, Vector<Map<String, String>> bpe, Vector<Pair<String, Vector<String>>> bpeTokVec) {
        for (int w = thisThread; w < bpeTokVec.size(); w += numThreads) {
            Pair<String, Vector<String>> x = bpeTokVec.elementAt(w);
            bpe.elementAt(thisThread).put(x.first, processBpe(x.second));
        }
    }

    private Map<String, String> runBpeMultithreaded(Vector<Pair<String, Vector<String>>> bpeTokVec) throws InterruptedException {
        Vector<Map<String, String>> bpe = new Vector<>(numThreads);
        Vector<Thread> threads = new Vector<>();
        for(int i = 0; i < numThreads; ++i) {
            int finalI = i;
            bpe.add(new HashMap<>());
            threads.add(new Thread(() -> calculateInThread(finalI, bpe, bpeTokVec)));
        }

        threads.forEach(Thread::start);

        Map<String, String> finalBpe = new HashMap<>();
        for (int i = 0; i < numThreads; i++) {
            threads.elementAt(i).join();
            for (Map.Entry<String, String> x : bpe.elementAt(i).entrySet()) {
                finalBpe.put(x.getKey(), x.getValue());
            }
        }

        return finalBpe;
    }

    private String outputString(final String s, Map<String, String> bpe) {
        StringBuilder currentWord = new StringBuilder();
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n') {
                if (currentWord.length() == 0) {
                    continue;
                }
                String currWord = currentWord.toString();
                String out = bpe.get(currWord);
                output.append(out);
                currentWord = new StringBuilder();
                output.append(c);
            } else {
                currentWord.append(c);
            }
        }
        return output.toString();
    }

    public String applyBpe(final String inputSent) throws InterruptedException {
        Map<String, Integer> wordCount = readWords(inputSent + "\n");
        Map<String, Vector<String>> tokens = tokenizeString(wordCount);

        Vector<Pair<String, Vector<String>>> bpeTokVec =
                tokens.entrySet().stream().map(entry -> Pair.create(entry.getKey(), entry.getValue())).collect(Collectors.toCollection(Vector::new));

        Map<String, String> finalBpe = runBpeMultithreaded(bpeTokVec);

        return outputString(inputSent + "\n", finalBpe).trim();
    }
}
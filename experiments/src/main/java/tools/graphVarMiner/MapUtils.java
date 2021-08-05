package tools.graphVarMiner;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiVariable;

import java.util.*;

public class MapUtils {
    public static IdentityHashMap<PsiVariable, Multiset<PsiIdentifier>> copyMap(
            IdentityHashMap<PsiVariable, Multiset<PsiIdentifier>> map) {
        IdentityHashMap<PsiVariable, Multiset<PsiIdentifier>> copy = new IdentityHashMap<>();
        for (Map.Entry<PsiVariable, Multiset<PsiIdentifier>> entry : map.entrySet()) {
            copy.put(entry.getKey(), HashMultiset.create(entry.getValue()));
        }
        return copy;
    }

    public static Map<String, Map<Integer, Set<Integer>>> copyMap(
            Map<String, Map<Integer, Set<Integer>>> map) {
        Map<String, Map<Integer, Set<Integer>>> copy = new HashMap<>();
        for (Map.Entry<String, Map<Integer, Set<Integer>>> entry : map.entrySet()) {
            Map<Integer, Set<Integer>> copy1 = new HashMap<>();
            for (Map.Entry<Integer, Set<Integer>> entry1 : entry.getValue().entrySet()) {
                copy1.put(entry1.getKey(), new HashSet<>(entry1.getValue()));
            }
            copy.put(entry.getKey(), copy1);
        }
        return copy;
    }

    public static Map<String, Map<Integer, Set<Integer>>> copyMap(
            Map<String, Map<Integer, Set<Integer>>> map, String... keysToCopy) {
        Map<String, Map<Integer, Set<Integer>>> copy = new HashMap<>(map);
        for (String key : keysToCopy) {
            if (!map.containsKey(key)) continue;
            Map<Integer, Set<Integer>> copy1 = new HashMap<>();
            for (Map.Entry<Integer, Set<Integer>> entry1 : map.get(key).entrySet()) {
                copy1.put(entry1.getKey(), new HashSet<>(entry1.getValue()));
            }
            copy.put(key, copy1);
        }
        return copy;
    }

    public static void mergeMapIntoFirst(IdentityHashMap<PsiVariable, Multiset<PsiIdentifier>> first,
                                         IdentityHashMap<PsiVariable, Multiset<PsiIdentifier>> other) {
        for (Map.Entry<PsiVariable, Multiset<PsiIdentifier>> otherEntry : other.entrySet()) {
            if (!first.containsKey(otherEntry.getKey())) {
                first.put(otherEntry.getKey(), otherEntry.getValue());
            } else {
                first.get(otherEntry.getKey()).addAll(otherEntry.getValue());
            }
        }
    }

    public static long mapSetSize(IdentityHashMap<PsiVariable, Multiset<PsiIdentifier>> map) {
        long size = 0;
        for (Multiset<PsiIdentifier> set : map.values()) {
            size += set.size();
        }
        return size;
    }

    public static void subtractMapFromFirst(IdentityHashMap<PsiVariable, Multiset<PsiIdentifier>> first, IdentityHashMap<PsiVariable, Multiset<PsiIdentifier>> other) {
        for (PsiVariable otherKey : other.keySet()) {
            first.computeIfPresent(otherKey, (k, v) -> {
                v.removeAll(other.get(otherKey));
                return v;
            });
        }
    }
}

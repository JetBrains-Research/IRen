package org.jetbrains.id.names.suggesting;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.FileVariableNamesContributor;
import org.jetbrains.id.names.suggesting.storages.VarNamePrediction;
import org.jetbrains.id.names.suggesting.utils.NotificationsUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.jetbrains.id.names.suggesting.utils.PsiUtils.isColliding;

public class IdNamesSuggestingService {
    public static final int PREDICTION_CUTOFF = 10;

    public static IdNamesSuggestingService getInstance() {
        return ServiceManager.getService(IdNamesSuggestingService.class);
    }

    public LinkedHashMap<String, Double> suggestVariableName(@NotNull PsiVariable variable) {
        Instant timerStart = Instant.now();
        List<VarNamePrediction> nameSuggestions = new ArrayList<>();
        Instant end = Instant.now();
        boolean isDeveloperMode = NotificationsUtil.isDeveloperMode();
        Map<String, Double> stats = new LinkedHashMap<>();
        if (isDeveloperMode) {
            double p = getVariableNameProbability(variable);
            stats.put("p", p);
            // toNanos because toMillis return long but I want it to be more precise, plus stats already has probability(p) which is anyway Double.
            stats.put("t (ms)", Duration.between(timerStart, end).toNanos() / 1_000_000.);
        }
        int prioritiesSum = 0;
        for (final VariableNamesContributor modelContributor : VariableNamesContributor.EP_NAME.getExtensions()) {
            Instant start = Instant.now();
            prioritiesSum += modelContributor.contribute(variable, nameSuggestions);
            end = Instant.now();
            stats.put(String.format("%s (ms)",
                    modelContributor.getClass().getSimpleName()),
                    Duration.between(start, end).toNanos() / 1_000_000.);
        }

        LinkedHashMap<String, Double> result = rankSuggestions(variable, nameSuggestions, prioritiesSum);
        Instant timerEnd = Instant.now();
        stats.put("Total time (ms)", Duration.between(timerStart, timerEnd).toNanos() / 1_000_000.);
        notify(variable.getProject(), stats);
        return result;
    }

    private void notify(Project project, Map<String, Double> stats) {
        StringBuilder notifications = new StringBuilder();
        for (Map.Entry<String, Double> kv : stats.entrySet()) {
            notifications.append(String.format("%s : %.3f;\n",
                    kv.getKey(),
                    kv.getValue()));
        }
        NotificationsUtil.notify(project,
                "Id Names Suggesting Stats",
                notifications.toString());
    }

    public @NotNull Double getVariableNameProbability(@NotNull PsiVariable variable) {
        double nameProbability = 0.0;
        int prioritiesSum = 0;
        for (final VariableNamesContributor modelContributor : VariableNamesContributor.EP_NAME.getExtensions()) {
            Pair<Double, Integer> probPriority = modelContributor.getProbability(variable);
            nameProbability += probPriority.getFirst() * probPriority.getSecond();
            prioritiesSum += probPriority.getSecond();
        }
        if (prioritiesSum != 0) {
            return nameProbability / prioritiesSum;
        } else return 0.0;
    }

    private boolean isAllowedToForgetContext(VariableNamesContributor contributor) {
        return contributor.getClass() == FileVariableNamesContributor.class;
    }

    private LinkedHashMap<String, Double> rankSuggestions(PsiElement variable, List<VarNamePrediction> nameSuggestions, int prioritiesSum) {
        if (prioritiesSum == 0) {
            return new LinkedHashMap<>();
        }
        Map<String, Double> rankedSuggestions = new HashMap<>();
        for (VarNamePrediction prediction : nameSuggestions) {
            Double prob = rankedSuggestions.get(prediction.getName());
            double addition = prediction.getProbability() * prediction.getPriority() / prioritiesSum;
            if (prob == null) {
                rankedSuggestions.put(prediction.getName(), addition);
            } else {
                rankedSuggestions.put(prediction.getName(), prob + addition);
            }
        }
        double unknownElementProb = rankedSuggestions.getOrDefault(Vocabulary.unknownCharacter, 0.);
        return rankedSuggestions.entrySet()
                .stream()
                .sorted((e1, e2) -> -Double.compare(e1.getValue(), e2.getValue()))
                .filter(e -> !isColliding(variable, e.getKey()))
                .limit(PREDICTION_CUTOFF)
                .filter(e -> e.getValue() > Math.max(0.001, unknownElementProb))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> {
                            throw new IllegalStateException();
                        },
                        LinkedHashMap::new));
    }
}

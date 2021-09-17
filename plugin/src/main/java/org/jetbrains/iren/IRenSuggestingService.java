package org.jetbrains.iren;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.api.VariableNamesContributor;
import org.jetbrains.iren.contributors.FileVariableNamesContributor;
import org.jetbrains.iren.storages.VarNamePrediction;
import org.jetbrains.iren.utils.NotificationsUtil;
import org.jetbrains.iren.utils.PsiUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class IRenSuggestingService {
    public static final int PREDICTION_CUTOFF = 10;

    public static IRenSuggestingService getInstance() {
        return ApplicationManager.getApplication().getService(IRenSuggestingService.class);
    }

    public LinkedHashMap<String, Double> suggestVariableName(@NotNull PsiNameIdentifierOwner variable) {
        Instant timerStart = Instant.now();
        List<VarNamePrediction> nameSuggestions = new ArrayList<>();
        boolean isDeveloperMode = NotificationsUtil.isDeveloperMode();
        Map<String, Double> stats = new LinkedHashMap<>();
        if (isDeveloperMode) {
            double p = getVariableNameProbability(variable);
            stats.put("p", p);
            // toNanos because toMillis return long but I want it to be more precise, plus stats already has probability(p) which is anyway Double.
            stats.put("t (ms)", Duration.between(timerStart, Instant.now()).toNanos() / 1_000_000.);
        }
        int prioritiesSum = 0;
        for (final VariableNamesContributor modelContributor : VariableNamesContributor.EP_NAME.getExtensions()) {
            Instant start = Instant.now();
            prioritiesSum += modelContributor.contribute(variable, nameSuggestions);
            stats.put(String.format("%s (ms)",
                    modelContributor.getClass().getSimpleName()),
                    Duration.between(start, Instant.now()).toNanos() / 1_000_000.);
        }

        LinkedHashMap<String, Double> result = rankSuggestions(variable, nameSuggestions, prioritiesSum);
        stats.put("Total time (ms)", Duration.between(timerStart, Instant.now()).toNanos() / 1_000_000.);
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
                "IRen Stats",
                notifications.toString());
    }

    public @NotNull Double getVariableNameProbability(@NotNull PsiNameIdentifierOwner variable) {
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
        return rankedSuggestions.entrySet()
                .stream()
                .sorted((e1, e2) -> -Double.compare(e1.getValue(), e2.getValue()))
                .filter(e -> !PsiUtils.getInstance(variable.getLanguage()).isColliding(variable, e.getKey()))
                .limit(PREDICTION_CUTOFF)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> {
                            throw new IllegalStateException();
                        },
                        LinkedHashMap::new));
    }
}

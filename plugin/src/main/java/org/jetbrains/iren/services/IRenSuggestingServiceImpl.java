package org.jetbrains.iren.services;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.VariableNamesContributor;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;
import org.jetbrains.iren.config.ModelType;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.VarNamePrediction;
import org.jetbrains.iren.utils.NotificationsUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class IRenSuggestingServiceImpl implements IRenSuggestingService {
    //    TODO: mb change InferenceStrategies to parameters ¯\_(ツ)_/¯
    @Override
    public @NotNull List<VarNamePrediction> suggestVariableName(Project project, @NotNull PsiNameIdentifierOwner variable, @Nullable PsiElement selectedElement, Collection<ModelType> modelTypes) {
        Instant timerStart = Instant.now();
        List<VarNamePrediction> nameSuggestions = new ArrayList<>();
        boolean verboseInference = NotificationsUtil.isVerboseInference();
        Map<String, Double> stats = new LinkedHashMap<>();
        if (verboseInference) {
            double p = getVariableNameProbability(variable);
            stats.put("p", p);
            // toNanos because toMillis return long, but I want it to be more precise, plus stats already has probability(p) which is anyway Double.
            stats.put("t (ms)", Duration.between(timerStart, Instant.now()).toNanos() / 1_000_000.);
        }
        int prioritiesSum = 0;
        for (final VariableNamesContributor modelContributor : VariableNamesContributor.EP_NAME.getExtensions()) {
            if (!modelTypes.contains(modelContributor.getModelType()))
                continue;
            Instant start = Instant.now();
            prioritiesSum += modelContributor.contribute(variable, selectedElement, nameSuggestions);
            stats.put(String.format("%s (ms)", modelContributor.getClass().getSimpleName()), Duration.between(start, Instant.now()).toNanos() / 1_000_000.);
        }

        List<VarNamePrediction> result = rankSuggestions(variable, nameSuggestions, prioritiesSum);
        stats.put("Total time (ms)", Duration.between(timerStart, Instant.now()).toNanos() / 1_000_000.);
        notify(project, stats);
        return result;
    }

    private void notify(Project project, Map<String, Double> stats) {
        StringBuilder notifications = new StringBuilder();
        for (Map.Entry<String, Double> kv : stats.entrySet()) {
            notifications.append(String.format("%s : %.3f;\n", kv.getKey(), kv.getValue()));
        }
        NotificationsUtil.notify(project, "IRen Stats", notifications.toString());
    }

    @Override
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

    @Override
    public @NotNull Context.Statistics getVariableContextStatistics(@NotNull PsiNameIdentifierOwner variable) {
        final ProjectVariableNamesContributor contributor = VariableNamesContributor.EP_NAME.findExtension(ProjectVariableNamesContributor.class);
        return contributor == null ? Context.Statistics.EMPTY : contributor.getContextStatistics(variable);
    }

    private @NotNull List<VarNamePrediction> rankSuggestions(@NotNull PsiElement variable, @NotNull List<VarNamePrediction> nameSuggestions, int prioritiesSum) {
        Map<String, ModelType> modelTypes = new HashMap<>();
        Map<String, Double> rankedSuggestions = new HashMap<>();
        List<VarNamePrediction> defaultSuggestions = new ArrayList<>();
        Language variableLanguage = variable.getLanguage();
        LanguageSupporter supporter = LanguageSupporter.getInstance(variableLanguage);
        for (VarNamePrediction prediction : nameSuggestions) {
            if (prediction.getModelType() == ModelType.DEFAULT) {
                defaultSuggestions.add(prediction);
                continue;
            }
            String predictionName = prediction.getName();
            if (!LanguageNamesValidation.isIdentifier(variableLanguage, predictionName, variable.getProject())) continue;
            Double prob = rankedSuggestions.get(predictionName);
            double addition = prediction.getProbability() * prediction.getPriority() / prioritiesSum;
            if (prob == null) {
                rankedSuggestions.put(predictionName, addition);
                modelTypes.put(predictionName, prediction.getModelType());
            } else {
                rankedSuggestions.put(predictionName, prob + addition);
                if (!Objects.equals(modelTypes.get(predictionName), prediction.getModelType()))
                    modelTypes.put(predictionName, ModelType.BOTH);
            }
        }
        List<VarNamePrediction> result = rankedSuggestions.entrySet().stream()
                .filter(e -> supporter != null && !supporter.isColliding(variable, e.getKey()))
                .sorted((e1, e2) -> -Double.compare(e1.getValue(), e2.getValue())).limit(PREDICTION_CUTOFF)
                .map(entry -> new VarNamePrediction(entry.getKey(), entry.getValue(), modelTypes.get(entry.getKey())))
                .collect(Collectors.toList());
        result.addAll(defaultSuggestions);
        return result;
    }
}

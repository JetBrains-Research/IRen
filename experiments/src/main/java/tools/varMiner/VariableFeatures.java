package tools.varMiner;

import com.intellij.psi.PsiVariable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class VariableFeatures implements Serializable {
    public final String variable;
    public final List<Object> ngrams = new ArrayList<>();
    public final List<Object> otherFeatures = new ArrayList<>();
    public String psiInterface = null;

    public VariableFeatures(String variable){
        this.variable = variable;
    }

    public VariableFeatures(PsiVariable variable, List<UsageFeatures> collect) {
        this(variable.getName(), collect);
        this.psiInterface = variable.getClass().getInterfaces()[0].getSimpleName();
    }

    public VariableFeatures(String variable, List<UsageFeatures> usages) {
        this(variable);
        for (UsageFeatures usage: usages){
            this.ngrams.add(usage.ngram);
            this.otherFeatures.add(usage.otherFeatures.values());
        }
    }
}

class UsageFeatures implements Serializable{
    public final Object ngram;
    public final HashMap<String, Integer> otherFeatures = new LinkedHashMap<>();

    public UsageFeatures(Object ngram, int distanceToDeclaration) {
        this.ngram = ngram;
        this.otherFeatures.put("distanceToDeclaration", distanceToDeclaration);
    }
}

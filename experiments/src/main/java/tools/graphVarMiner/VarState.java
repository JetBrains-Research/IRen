package tools.graphVarMiner;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.IdentityHashMap;

import static tools.graphVarMiner.MapUtils.*;

public class VarState {
    public IdentityHashMap<PsiVariable, Multiset<PsiIdentifier>> lastUse = new IdentityHashMap<>();
    public IdentityHashMap<PsiVariable, Multiset<PsiIdentifier>> lastWrite = new IdentityHashMap<>();

    public void distinct() {
        lastUse.replaceAll((k, v) -> HashMultiset.create(v.elementSet()));
        lastWrite.replaceAll((k, v) -> HashMultiset.create(v.elementSet()));
    }

    public VarState copy() {
        VarState clone = new VarState();
        clone.lastUse = copyMap(this.lastUse);
        clone.lastWrite = copyMap(this.lastWrite);
        return clone;
    }

    public static VarState of(Collection<VarState> states) {
        VarState res = new VarState();
        res.mergeWith(states, true);
        return res;
    }

    public void mergeWith(@Nullable Collection<VarState> varStates, boolean distinct) {
        if (varStates == null) return;
        varStates.forEach(x -> this.mergeWith(x, false));
        if (distinct) distinct();
    }

    public void mergeWith(@Nullable Collection<VarState> varStates) {
        this.mergeWith(varStates, true);
    }

    public void mergeWith(VarState otherState, boolean distinct) {
        mergeMapIntoFirst(this.lastUse, otherState.lastUse);
        mergeMapIntoFirst(this.lastWrite, otherState.lastWrite);
        if (distinct) distinct();
    }

    public void mergeWith(VarState otherState) {
        this.mergeWith(otherState, true);
    }

    public void init(PsiVariable variable) {
        lastUse.putIfAbsent(variable, HashMultiset.create());
        lastWrite.putIfAbsent(variable, HashMultiset.create());
    }

    public Multiset<PsiIdentifier> getLastUse(PsiVariable variable) {
        return lastUse.get(variable);
    }

    public Multiset<PsiIdentifier> getLastWrite(PsiVariable variable) {
        return lastWrite.get(variable);
    }

    public boolean contains(PsiVariable variable) {
        return lastUse.containsKey(variable);
    }

    public void subtract(@Nullable Collection<VarState> subtract) {
        if (subtract == null) return;
        subtract.forEach(this::subtract);
    }

    private void subtract(VarState other) {
        subtractMapFromFirst(this.lastUse, other.lastUse);
        subtractMapFromFirst(this.lastWrite, other.lastWrite);
    }
}

package org.jetbrains.iren.storages;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class VarNamePrediction {
    private final String myName;
    private final double myProbability;
    private final int myPriority;

    public VarNamePrediction(String name, double probability, int priority) {
        myName = name;
        myProbability = probability;
        myPriority = priority;
    }

    public VarNamePrediction(String name, double probability) {
        myName = name;
        myProbability = probability;
        myPriority = 1;
    }

    public String getName() {
        return myName;
    }

    public double getProbability() {
        return myProbability;
    }

    public int getPriority() {
        return myPriority;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VarNamePrediction){
            return myName.equals(((VarNamePrediction) obj).getName());
        }
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return myName + ':' + myProbability;
    }

    public static class List extends ArrayList<VarNamePrediction> {
        public Integer usageNumber;
        public Integer countsSum;

        public List() {
            super();
        }

        public List(@NotNull Collection<? extends VarNamePrediction> c, int usageNumber, int countsSum) {
            super(c);
            this.usageNumber = usageNumber;
            this.countsSum = countsSum;
        }

        public List(@NotNull Collection<? extends VarNamePrediction> c) {
            super(c);
        }
    }
}

package org.jetbrains.id.names.suggesting;

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
}

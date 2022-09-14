package org.jetbrains.iren.storages;

import org.jetbrains.iren.config.ModelType;

public class VarNamePrediction {
    private final String myName;
    private final double myProbability;
    private final double myPriority;
    private final ModelType myModelType;

    public VarNamePrediction(String name, double probability, ModelType modelType, double priority) {
        myName = name;
        myProbability = probability;
        myModelType = modelType;
        myPriority = priority;
    }

    public VarNamePrediction(String name, double probability, ModelType modelType) {
        myName = name;
        myProbability = probability;
        myModelType = modelType;
        myPriority = 1;
    }

    public String getName() {
        return myName;
    }

    public double getProbability() {
        return myProbability;
    }

    public double getPriority() {
        return myPriority;
    }

    public ModelType getModelType() {
        return myModelType;
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

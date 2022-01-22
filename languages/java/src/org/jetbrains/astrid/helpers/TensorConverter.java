package org.jetbrains.astrid.helpers;

//import org.tensorflow.Tensor;

public class TensorConverter {

//    public static List<List> parsePredictions(Tensor tensor) {
//        int predictionsCount = 10;
//        int maxSubtokenCount = (int) tensor.shape()[1];
//        int[][][] predictionsMatrix = new int[(int) tensor.shape()[0]][maxSubtokenCount][(int) tensor.shape()[2]];
//        tensor.copyTo(predictionsMatrix);
//
//        List<List> predictions = new ArrayList<>(maxSubtokenCount);
//        for (int i = 0; i < maxSubtokenCount; i++) {
//            predictions.add(Collections.singletonList(predictionsMatrix[0][i]));
//        }
//
//        List<List> listsOfIndexes = new ArrayList<>();
//        for (int i = 0; i < predictionsCount; i++) {
//            List subtokenIndexes = new ArrayList();
//            for (int z = 0; z < maxSubtokenCount; z++) {
//                subtokenIndexes.add(((int[]) predictions.get(z).get(0))[i]);
//            }
//            listsOfIndexes.add(subtokenIndexes);
//        }
//        return listsOfIndexes;
//    }
//
//    public static List<Double> parseScores(Tensor tensor) {
//        int scoresCount = 10;
//        int maxSubtokenCount = (int) tensor.shape()[1];
//        float[][][] predictionsMatrix = new float[(int) tensor.shape()[0]][maxSubtokenCount][(int) tensor.shape()[2]];
//        tensor.copyTo(predictionsMatrix);
//
//        List<List> listOfSubtokenScores = new ArrayList<>(maxSubtokenCount);
//        for (int i = 0; i < maxSubtokenCount; i++) {
//            listOfSubtokenScores.add(Collections.singletonList(predictionsMatrix[0][i]));
//        }
//
//        List<Double> listsOfScores = new ArrayList<>();
//        for (int i = 0; i < scoresCount; i++) {
//            double score = 0.0;
//            for (int z = 0; z < maxSubtokenCount; z++) {
//                score += ((float[]) listOfSubtokenScores.get(z).get(0))[i];
//            }
//            listsOfScores.add(Math.exp(score));
//        }
//        return listsOfScores;
//    }

}

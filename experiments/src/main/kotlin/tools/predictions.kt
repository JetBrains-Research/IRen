package tools

class VarNamePredictions(
    val groundTruth: String,
    val nGramPrediction: List<ModelPrediction>,
    val nGramEvaluationTime: Double,
    val nnPrediction: Any,
    val nnResponseTime: Double,
    val psiInterface: String,
    val inplaceRenameAvailable: Boolean
)

class ModelPrediction(val name: Any, val p: Double)
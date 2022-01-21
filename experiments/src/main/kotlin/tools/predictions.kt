package tools

class VarNamePredictions(
    val groundTruth: String,
    val nGramPrediction: ModelPredictions,
    val nnPrediction: Any,
    val psiInterface: String,
    val inplaceRenameAvailable: Boolean
)

class ModelPrediction(val name: Any, val p: Double)

open class ModelPredictions(val predictions: Any, val time: Double)
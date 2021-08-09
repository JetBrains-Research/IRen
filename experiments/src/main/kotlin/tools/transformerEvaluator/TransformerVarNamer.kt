package tools.transformerEvaluator

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiVariable
import com.intellij.util.io.HttpRequests
import tools.modelsEvaluatorApi.VarNamer
import tools.varMiner.DatasetExtractor

class TransformerVarNamer : VarNamer() {
    private val LOG = logger<TransformerVarNamer>()
    private val TRANSFORMER_SERVER_URL = "http://127.0.0.1:5000/"

    override fun predictWithNN(variable: PsiVariable): Any {
        val variableFeatures = DatasetExtractor.getVariableFeatures(variable, variable.containingFile)
        return HttpRequests.post(TRANSFORMER_SERVER_URL, HttpRequests.JSON_CONTENT_TYPE)
            .connect(HttpRequests.RequestProcessor {
                val objectMapper = ObjectMapper()
                it.write(objectMapper.writeValueAsBytes(variableFeatures))
                val str = it.readString()
                objectMapper.readValue(str, Any::class.java)
            }, null, LOG)
    }
}
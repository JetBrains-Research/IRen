package tools.transformerEvaluator

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiVariable
import com.intellij.util.io.HttpRequests
import org.jetbrains.iren.LanguageSupporter
import tools.modelsEvaluatorApi.VarNamer
import tools.varMiner.DatasetExtractor
import java.nio.file.Path

class TransformerVarNamer(saveDir: Path, supporter: LanguageSupporter, ngramType: String) : VarNamer(saveDir, supporter,
    ngramType
) {
    private val LOG = logger<TransformerVarNamer>()
    private val TRANSFORMER_SERVER_URL = "http://127.0.0.1:5000/"

    override fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
//        works only with java
        val variableFeatures = DatasetExtractor.getVariableFeatures(variable as PsiVariable, variable.containingFile)
        return HttpRequests.post(TRANSFORMER_SERVER_URL, HttpRequests.JSON_CONTENT_TYPE)
            .connect( {
                val objectMapper = ObjectMapper()
                it.write(objectMapper.writeValueAsBytes(variableFeatures))
                val str = it.readString()
                objectMapper.readValue(str, Any::class.java)
            }, null, LOG)
    }
}
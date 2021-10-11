package tools.gnnEvaluator

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiVariable
import com.intellij.util.io.HttpRequests
import org.jetbrains.iren.utils.LanguageSupporter
import tools.graphVarMiner.GraphDatasetExtractor
import tools.graphVarMiner.JavaGraphExtractor
import tools.modelsEvaluatorApi.VarNamePredictions
import tools.modelsEvaluatorApi.VarNamer
import java.nio.file.Path

class GNNVarNamer(saveDir: Path, supporter: LanguageSupporter, ngramType: String) : VarNamer(saveDir, supporter,
    ngramType
) {
    private lateinit var graphExtractor: JavaGraphExtractor
    private val LOG = logger<GNNVarNamer>()
    private val GNN_SERVER_URL = "http://127.0.0.1:5000/"

    override fun predictPsiFile(file: PsiFile): List<VarNamePredictions>? {
        graphExtractor = JavaGraphExtractor(file)
        return super.predictPsiFile(file)
    }

    override fun predictWithNN(variable: PsiNameIdentifierOwner): Any {
//        works only with java
        val varData = GraphDatasetExtractor.getVarData(variable as PsiVariable, graphExtractor.file, graphExtractor)
        return HttpRequests.post(GNN_SERVER_URL, HttpRequests.JSON_CONTENT_TYPE)
            .connect(HttpRequests.RequestProcessor {
                it.write(GsonBuilder().create().toJson(varData))
                val str = it.readString()
                ObjectMapper().readValue(str, Any::class.java)
            }, null, LOG)
    }
}
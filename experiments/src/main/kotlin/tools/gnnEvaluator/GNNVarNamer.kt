package tools.gnnEvaluator

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiVariable
import com.intellij.util.io.HttpRequests
import org.jetbrains.iren.LanguageSupporter
import tools.VarNamePredictions
import tools.graphVarMiner.GraphDatasetExtractor
import tools.graphVarMiner.JavaGraphExtractor
import tools.modelsEvaluatorApi.VarNamer
import java.nio.file.Path

class GNNVarNamer(saveDir: Path, supporter: LanguageSupporter, ngramType: String) : VarNamer(saveDir, supporter,
    ngramType
) {
    private lateinit var graphExtractor: JavaGraphExtractor
    private val LOG = logger<GNNVarNamer>()
    private val GNN_SERVER_URL = "http://127.0.0.1:5000/"

    override fun predictPsiFile(file: PsiFile, thread: Int): Collection<VarNamePredictions>? {
        graphExtractor = JavaGraphExtractor(file)
        return super.predictPsiFile(file, thread)
    }

    override fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
//        works only with java
        val varData = GraphDatasetExtractor.getVarData(variable as PsiVariable, graphExtractor.file, graphExtractor)
        return HttpRequests.post(GNN_SERVER_URL, HttpRequests.JSON_CONTENT_TYPE)
            .connect( {
                it.write(GsonBuilder().create().toJson(varData))
                val str = it.readString()
                ObjectMapper().readValue(str, Any::class.java)
            }, null, LOG)
    }
}
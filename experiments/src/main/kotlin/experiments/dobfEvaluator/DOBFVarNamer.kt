package experiments.dobfEvaluator

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.util.io.HttpRequests
import experiments.modelsEvaluatorApi.VarNamer
import org.jetbrains.iren.LanguageSupporter
import tools.DOBFPreprocessor
import tools.VariableContext
import java.nio.file.Path

class DOBFVarNamer(saveDir: Path, supporter: LanguageSupporter, ngramType: String) : VarNamer(
    saveDir, supporter,
    ngramType
) {
    private val LOG = logger<DOBFVarNamer>()
    private val DOBF_SERVER_URL = "http://127.0.0.1:5000/"
    override val maxNumberOfThreads = 7
    override fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
        val variableContext = ReadAction.compute<VariableContext?, Exception> {
            DOBFPreprocessor.process(
                variable,
                variable.containingFile
            )
        }
        synchronized(this) {
            return HttpRequests.post(DOBF_SERVER_URL, HttpRequests.JSON_CONTENT_TYPE)
                .connect({
                    val objectMapper = ObjectMapper()
                    it.write(objectMapper.writeValueAsBytes(variableContext))
                    val str = it.readString()
                    objectMapper.readValue(str, Any::class.java)
                }, null, LOG)
        }
    }

    override fun ignoreVarWithName(name: String): Boolean {
        return name == "self"
    }
}
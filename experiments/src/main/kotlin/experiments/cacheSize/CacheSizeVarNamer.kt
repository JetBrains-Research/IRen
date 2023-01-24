package experiments.cacheSize

import com.intellij.completion.ngram.slp.counting.trie.my.persistent.CountersCache
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiNameIdentifierOwner
import experiments.ModelPrediction
import experiments.ModelPredictions
import experiments.modelsEvaluatorApi.VarNamer
import experiments.modelsEvaluatorApi.addText
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.ngram.NGramModelRunner
import org.jetbrains.iren.ngram.PersistentNGramModelRunner
import org.jetbrains.iren.storages.Context
import org.jetbrains.iren.storages.VarNamePrediction
import java.nio.file.Path
import kotlin.io.path.Path

open class CacheSizeVarNamer(
    saveDir: Path,
    supporter: LanguageSupporter,
    ngramType: String,
) : VarNamer(saveDir, supporter, ngramType) {
    override val maxNumberOfThreads = 6
    private val intellijModelDir =
        Path("C:\\Users\\Igor.Davidenko\\AppData\\Local\\JetBrains\\IntelliJIdea2022.1\\models\\intellij_JAVA_2")
    val cacheSize = 7000

    override fun preparePersistentRunners(): List<NGramModelRunner> {
        println("Preparing persistent counters...")
        addText(myStatsFile, "1.31 Gb,")
        CountersCache.MAXIMUM_CACHE_SIZE = cacheSize
        return (0 until if (runParallel) maxNumberOfThreads else 1).map {
            println("Loading ${it + 1}")
            val modelRunner = PersistentNGramModelRunner()
            modelRunner.load(intellijModelDir, null)
            modelRunner.vocabulary.close()
            modelRunner
        }
    }

    override fun predictWithNGram(variable: PsiNameIdentifierOwner, thread: Int): ModelPredictions {
        val allTimeStart = System.nanoTime()
        val runner = prepareThreadRunner(thread, variable)
        val intContext =
            ReadAction.compute<Context<Int>, Exception> { runner.getContext(variable) }
        val counterTimeStart = System.nanoTime()
        val nameSuggestions = runner.suggestNames(intContext)
        val counterTime = (System.nanoTime() - counterTimeStart) / 1.0e9
        val allTime = (System.nanoTime() - allTimeStart) / 1.0e9
        return NGramPredictionsWithTimes(
            nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) },
            allTime,
            counterTime
        )
    }

    override fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
        return listOf<Any>()
    }
}

class NGramPredictionsWithTimes(
    predictions: Any,
    time: Double,
    val counterTime: Double,
) :
    ModelPredictions(predictions, time)
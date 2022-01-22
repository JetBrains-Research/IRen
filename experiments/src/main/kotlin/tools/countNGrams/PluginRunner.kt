package tools.countNGrams

import com.google.gson.GsonBuilder
import com.intellij.completion.ngram.slp.sequencing.NGramSequencer
import com.intellij.completion.ngram.slp.translating.Vocabulary
import com.intellij.completion.ngram.slp.translating.VocabularyRunner.write
import com.intellij.ide.impl.ProjectUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.PythonLanguage
import me.tongfei.progressbar.ProgressBar
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.zip.GZIPOutputStream
import kotlin.system.exitProcess

open class PluginRunner : ApplicationStarter {
    private lateinit var dataset: File
    protected lateinit var saveDir: Path
    protected lateinit var supporter: LanguageSupporter

    private val projectList: List<String>? = listOf("intellij")

    override fun getCommandName(): String = "countNGrams"

    override fun main(args: Array<out String>) {
        try {
            dataset = File(args[1])
            saveDir = Paths.get(args[2])
            supporter = LanguageSupporter.getInstance(
                when (args[3].lowercase(Locale.getDefault())) {
                    "java" -> JavaLanguage.INSTANCE
                    "python" -> PythonLanguage.INSTANCE
                    "kotlin" -> KotlinLanguage.INSTANCE
                    else -> throw AssertionError("Unknown language")
                }
            )
            countNGrams()
        } catch (e: OutOfMemoryError) {
            println("Not enough memory!")
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            exitProcess(0)
        }
    }

    private fun countNGrams() {
        println("Counting NGrams...")
        var projectToClose: Project? = null
        for (projectDir in projectList
            ?: listOf(*dataset.list { file, name -> file.isDirectory && !name.startsWith(".") } ?: return)) {
            val projectPath = dataset.resolve(projectDir)
            println("Opening project $projectDir...")
            val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue

            val files = ReadAction.compute<Collection<VirtualFile>, RuntimeException> {
                FileTypeIndex.getFiles(
                    supporter.fileType,
                    GlobalSearchScope.projectScope(project)
                )
            }
            val vocabulary = Vocabulary()
            val nGramMap = HashMap<List<Int>, Int>()
            val progressBar = ProgressBar(project.name, files.size.toLong())
            files.stream().forEach { file ->
                progressBar.step()
                val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@forEach
                NGramSequencer.sequenceForward(
                    vocabulary.toIndices(supporter.lexPsiFile(psiFile)).filterNotNull(), 6
                ).forEach {
                    val count = nGramMap[it] ?: 0
                    nGramMap[it] = count + 1
                }
            }
            progressBar.close()
            println("Vocabulary size: ${vocabulary.size()}")
            println("Number of unique NGrams: ${nGramMap.size}")
            val vocabularyFile: File = saveDir.resolve("vocabulary.txt").toFile()
            vocabularyFile.parentFile.mkdirs()
            vocabularyFile.createNewFile()
            write(vocabulary, vocabularyFile)
            val gson = GsonBuilder().create()
            val ngramsFile = saveDir.resolve("ngrams.json.gz").toFile()
            ngramsFile.createNewFile()
            FileOutputStream(ngramsFile).use {
                val writer: Writer = OutputStreamWriter(GZIPOutputStream(it), "UTF-8")
                writer.write(gson.toJson(nGramMap))
                writer.close()
            }
            projectToClose = project
        }
        if (projectToClose != null) {
            ProjectManager.getInstance().closeAndDispose(projectToClose)
        }
    }
}


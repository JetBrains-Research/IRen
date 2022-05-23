package tools

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.PythonLanguage
import me.tongfei.progressbar.ProgressBar
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.LanguageSupporterBase
import org.jetbrains.kotlin.idea.KotlinLanguage
import utils.ChunkWriter
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class DOBFPreprocessor : ApplicationStarter {
    private val log = logger<DOBFPreprocessor>()
    protected lateinit var dataset: File
    protected lateinit var saveDir: Path
    protected lateinit var supporter: LanguageSupporter
    val numberOfThreads = 7

    override fun getCommandName(): String = "buildDOBFDataset"

    override fun main(args: List<String>) {
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
            )!!
//            build()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            exitProcess(0)
        }
    }

    companion object {
        private const val VAR_TOKEN = "VAR_%d"
        private const val FUNC_TOKEN = "FUNC_%d"
        private const val CLASS_TOKEN = "CLASS_%d"

        fun process(variable: PsiNameIdentifierOwner, file: PsiFile): VariableContext? {
            return VariableContext(variable.name ?: return null, replaceUsages(file, variable) ?: return null)
        }

        private fun replaceUsages(file: PsiFile, variable: PsiNameIdentifierOwner): List<String>? {
            val supporter = (LanguageSupporter.getInstance(variable.language)?: return null) as LanguageSupporterBase
            val usages = mutableSetOf(supporter.getIdentifier(variable) ?: return null)
            supporter.findReferences(variable, file)?.mapNotNull { e -> supporter.getIdentifier(e) }?.let {
                usages.addAll(it)
            }
            return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(TextRange(0, 64 * 1024)) // first 128 KB of chars
                .forceIgnore { node -> node is PsiComment }
                .filter { element -> element != null && supporter.shouldLex(element) }
                .toList()
                .asSequence()
                .map { token -> if (usages.contains(token)) VAR_TOKEN.format(0) else token.text }
                .toList()
        }
    }

    fun buildDataset(project: Project, prefix: String?) {
        val files = FileTypeIndex.getFiles(
            supporter.language.associatedFileType ?: return,
            GlobalSearchScope.projectScope(project)
        )
        val total = files.size
        System.out.printf("Number of files to parse: %s\n", total)
        val psiManager = PsiManager.getInstance(project)
        val writer = ChunkWriter<ObfuscatedCode>(prefix, 2000)
        val progressBar = ProgressBar(project.name, total.toLong())
        files.withIndex().groupBy { it.index % numberOfThreads }.map { (_, fs) ->
            thread {
                for ((_, f) in fs) {
                    processFile(f, psiManager, writer, project, progressBar)
                }
            }
        }
        writer.close()
        progressBar.close()
    }

    private fun processFile(
        file: VirtualFile,
        psiManager: PsiManager,
        writer: ChunkWriter<ObfuscatedCode>,
        project: Project,
        progressBar: ProgressBar
    ) {
        progressBar.step()
        try {
            val fileSize = Files.size(Paths.get(file.path))
            if (fileSize > 128 * 1024) {  // 128 KB
                log.info(String.format("Skip file %s with size %dB", file.path, fileSize))
                return
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val psiFile = psiManager.findFile(file) ?: return
        writer.add(obfuscate(psiFile))
//        Clear resolve cache
        ResolveCache.getInstance(project).clearCache(true)
    }

    fun obfuscate(file: PsiFile): ObfuscatedCode {
        return ObfuscatedCode("", HashMap(), supporter.language.displayName)
    }
}

class ObfuscatedCode(val file: String, val identifiersMap: Map<String, String>, val language: String)

data class VariableContext(val gt: String, val context: List<String>)
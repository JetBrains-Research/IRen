package org.jetbrains.astrid.downloader

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.astrid.enums.OSType
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object Downloader {
    private const val archiveName = "model.zip"
    private const val dirName = "org/jetbrains/astrid/model"
    private const val pluginName = "astrid_plugin"
    const val modelSubDir = "/org/jetbrains/astrid/model"
    const val beamSubDirLinux = "/beam_search/_beam_search_ops.so"
    const val beamSubDirMac = "/beam_search/mac/_beam_search_ops.so"
    const val dictSubDir = "/org/jetbrains/astrid/model/dict/targets.dict"
    const val modelLink = "https://www.dropbox.com/s/j2ydkxq3js33d93/model.zip?dl=1"
    private val tmp: String = System.getProperty("java.io.tmpdir")

    fun getArchivePath(): Path = Paths.get(tmp, pluginName, archiveName)
    fun getPluginPath(): Path = Paths.get(tmp, pluginName)
    fun getModelPath(): Path = Paths.get(tmp, pluginName, dirName)

    fun checkArchive() {
//        val progressManager: ProgressManager = ProgressManager.getInstance();
//        progressManager.run(object : Task.Backgroundable(ProjectManager.getInstance().defaultProject,
//                "Generating suggestions", true) {
//            override fun run(indicator: ProgressIndicator) {
//                if (!Files.exists(getModelPath())) {
//                    getPluginPath().toFile().mkdir()
//                    downloadArchive(URL(modelLink), getArchivePath(),
//                            ProgressManager.getInstance().progressIndicator)
//                    if (indicator.isCanceled) return
//                    ProgressManager.getInstance().progressIndicator.text = "Extracting archive"
//                    FileUtils.unzip(getArchivePath().toString(), getModelPath().toString())
//                }
//            }
//        })
    }

    fun downloadArchive(url: URL, path: Path, indicator: ProgressIndicator) {
//        indicator.text = "Downloading model for suggesting methods' name..."
//        path.toFile().parentFile.mkdirs()
//        val urlConnection = url.openConnection()
//        val contentLength = urlConnection.contentLength
//
//        BufferedInputStream(url.openStream()).use {
//            val out = FileOutputStream(path.toFile())
//            val data = ByteArray(1024)
//            var totalCount = 0
//            var count = it.read(data, 0, 1024)
//            while (count != -1 && !indicator.isCanceled) {
//                out.write(data, 0, count)
//                totalCount += count
//                if (contentLength == 0) {
//                    indicator.fraction = 0.0
//                } else {
//                    indicator.fraction = totalCount.toDouble() / contentLength
//                }
//                count = it.read(data, 0, 1024)
//            }
//        }
//        indicator.fraction = 1.0
    }

    private fun getOperatingSystemType(): OSType {
        val OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
        if (OS.contains("mac") || OS.contains("darwin")) {
            return OSType.MACOS
        } else if (OS.contains("win")) {
            return OSType.WINDOWS
        } else if (OS.contains("nux")) {
            return OSType.LINUX
        } else {
            return OSType.OTHER
        }
    }

    fun getPathToBeamModule(): String? {
        val os = getOperatingSystemType()
        var path = getModelPath().toString() + modelSubDir
        when {
            os.equals(OSType.MACOS) -> path += beamSubDirMac
            os.equals(OSType.LINUX) -> path += beamSubDirLinux
        }
        return null
    }

}
package org.jetbrains.iren

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.download.DownloadableFileService
import org.jetbrains.iren.utils.ModelUtils
import org.jetbrains.iren.utils.ModelUtils.INTELLIJ_NAME
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

// TODO: make it better somehow
val INTELLIJ_MODEL_URL =
    "https://iren-intellij-model.s3.eu-north-1.amazonaws.com/intellij-${ModelUtils().version}.zip"
val DOBF_MODEL_URL = "https://iren-dobf-models.s3.eu-north-1.amazonaws.com/%s.zip"
const val TMP_MODEL_FILE = "tmp_model.zip"
private val log = Logger.getInstance("org.jetbrains.iren.ModelLoader")

/**
 * Download and unzip model.
 */
fun downloadAndExtractModel(
    indicator: ProgressIndicator,
    modelName: String = INTELLIJ_NAME,
    modelsDirectory: Path = ModelUtils().modelsDirectory
) {
    val modelZipPath = modelsDirectory.resolve(TMP_MODEL_FILE)
    try {
        log.info(IRenBundle.message("downloading.model", modelName))
        indicator.text = IRenBundle.message("downloading.model", modelName)
        downloadModel(modelsDirectory, modelName)
        indicator.checkCanceled()
        indicator.isIndeterminate = true
        log.info(IRenBundle.message("extracting.model", modelName))
        indicator.text = IRenBundle.message("extracting.model", modelName)
        unzip(modelZipPath)
        log.info(IRenBundle.message("downloading.model.done", modelName))
    } catch (e: IOException) {
        log.error(e)
    } finally {
        Files.deleteIfExists(modelZipPath)
    }
}

private fun downloadModel(modelsDirectory: Path, modelName: String) {
    val downloadableFileService = DownloadableFileService.getInstance()
    val targetDir = modelsDirectory.toFile()
    targetDir.mkdir()
    downloadableFileService.createDownloader(
        listOf(downloadableFileService.createFileDescription(getModelUrl(modelName), TMP_MODEL_FILE)),
        IRenBundle.message("downloading.model", modelName)
    ).download(targetDir)
}

private fun getModelUrl(modelName: String) =
    if (modelName == INTELLIJ_NAME) INTELLIJ_MODEL_URL else DOBF_MODEL_URL.format(modelName)


@Throws(IOException::class)
private fun unzip(modelZipPath: Path) {
    val zis = ZipInputStream(FileInputStream(modelZipPath.toFile()))
    var zipEntry: ZipEntry? = zis.nextEntry
    val buffer = ByteArray(1024)
    while (zipEntry != null) {
        val newFile = newFile(modelZipPath.parent.toFile(), zipEntry)
        if (zipEntry.isDirectory) {
            if (!newFile.isDirectory && !newFile.mkdirs()) {
                throw IOException("Failed to create directory $newFile")
            }
        } else {
            // fix for Windows-created archives
            val parent: File = newFile.parentFile
            if (!parent.isDirectory && !parent.mkdirs()) {
                throw IOException("Failed to create directory $parent")
            }

            // write file content
            val fos = FileOutputStream(newFile)
            var len: Int
            while (zis.read(buffer).also { len = it } > 0) {
                fos.write(buffer, 0, len)
            }
            fos.close()
        }
        zipEntry = zis.nextEntry
    }
    zis.closeEntry()
    zis.close()
}

@Throws(IOException::class)
private fun newFile(destinationDir: File, zipEntry: ZipEntry): File {
    val destFile = File(destinationDir, zipEntry.name)
    val destDirPath = destinationDir.canonicalPath
    val destFilePath = destFile.canonicalPath
    if (!destFilePath.startsWith(destDirPath + File.separator)) {
        throw IOException("Entry is outside of the target dir: " + zipEntry.name)
    }
    return destFile
}
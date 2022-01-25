package org.jetbrains.iren

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.download.DownloadableFileService
import org.jetbrains.iren.utils.ModelUtils.CURRENT_MODEL_VERSION
import org.jetbrains.iren.utils.ModelUtils.MODELS_DIRECTORY
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

const val INTELLIJ_MODEL_URL =
    "https://iren-intellij-model.s3.eu-north-1.amazonaws.com/intellij-$CURRENT_MODEL_VERSION.zip"
const val TMP_MODEL_FILE = "intellij.zip"
private val log = Logger.getInstance("org.jetbrains.iren.ModelLoader")

/**
 * Download and unzip intellij models.
 */
fun downloadAndExtractIntellijModels(indicator: ProgressIndicator) {
    val modelZipPath = MODELS_DIRECTORY.resolve(TMP_MODEL_FILE)
    try {
        log.info(IRenBundle.message("loading.intellij.models"))
        indicator.text = IRenBundle.message("loading.intellij.models")
        downloadIntellijModels()
        indicator.checkCanceled()
        indicator.isIndeterminate = true
        log.info(IRenBundle.message("extracting.intellij.models"))
        indicator.text = IRenBundle.message("extracting.intellij.models")
        unzip(modelZipPath)
        log.info(IRenBundle.message("loading.intellij.models.done"))
    } catch (e: IOException) {
        log.error(e)
    } finally {
        Files.deleteIfExists(modelZipPath)
    }
}

private fun downloadIntellijModels() {
    val downloadableFileService = DownloadableFileService.getInstance()
    val targetDir = MODELS_DIRECTORY.toFile()
    targetDir.mkdir()
    downloadableFileService.createDownloader(
        listOf(downloadableFileService.createFileDescription(INTELLIJ_MODEL_URL, TMP_MODEL_FILE)),
        IRenBundle.message("loading.intellij.models")
    ).download(targetDir)
}


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
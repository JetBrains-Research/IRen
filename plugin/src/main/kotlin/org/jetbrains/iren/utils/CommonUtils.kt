package org.jetbrains.iren.utils

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager


/**
 * Returns new list with appended [item]
 */
fun <T> List<T>.append(item: T) = listOf(this, listOf(item)).flatten()

fun isCancelled(): Boolean {
    return try {
        ProgressManager.checkCanceled()
        false
    } catch (_: ProcessCanceledException) {
        true
    }
}
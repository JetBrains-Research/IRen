package org.jetbrains.astrid.extractors.features

import com.intellij.openapi.diagnostic.Logger
import org.apache.commons.lang3.StringUtils
import org.jetbrains.astrid.extractors.common.Common

class Extractor(private val code: String) {

    private val log: Logger = Logger.getInstance(FeatureExtractor::class.java)

    private fun extractFromCodeBlock(): ArrayList<ProgramFeatures> {
        val featureExtractor = FeatureExtractor(this.code)
        return featureExtractor.extractFeatures()
    }

    fun processCodeBlock(): String {
        val features: ArrayList<ProgramFeatures>?
        try {
            features = extractFromCodeBlock()
        } catch (e: Exception) {
            log.info("Error was occurred while parsing method body.")
            return ""
        }
        val toPrint = featuresToString(features)
        return if (toPrint.isNotEmpty()) toPrint else ""
    }

    private fun featuresToString(features: ArrayList<ProgramFeatures>?): String {
        if (features == null || features.isEmpty()) {
            return Common.EMPTY_STRING
        }

        val methodsOutputs = ArrayList<String>()

        for (singleMethodFeatures in features) {
            val builder = StringBuilder()
            val feature = singleMethodFeatures.toString()
            builder.append(feature)
            methodsOutputs.add(builder.toString())
        }
        return StringUtils.join(methodsOutputs, "\n")
    }
}
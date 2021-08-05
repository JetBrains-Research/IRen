package org.jetbrains.id.names.suggesting.inspections.variable

/**
 * This class contains information about identifier probability.
 */
class Probability(val prob: Double) {
    var needRecalculate: Boolean = false

    var ignore: Boolean = false

    fun setRecalculate() {
        this.needRecalculate = true
    }

    fun setIgnore() {
        this.ignore = true
    }
}
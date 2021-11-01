package org.jetbrains.astrid.inspections

/**
 * This class contains information about suggestion for method.
 * Suggestion is a list of pairs of the type (name, score),
 * where score is a probability of the corresponding name
 */
class Suggestion(val names: ArrayList<Pair<String, Double>>) {
    var needRecalculate: Boolean = false

    var ignore: Boolean = false

    fun setRecalculate() {
        this.needRecalculate = true
    }

    fun setIgnore() {
        this.ignore = true
    }

    fun containsName(name: String): Boolean {
        for (pair in names) {
            if (pair.first.equals(name)) return true
        }
        return false
    }

    fun addName(value: String) {
        this.names.add(Pair(value, -1.0))
    }

    fun removeName(value: String) {
        for (pair in names) {
            if (pair.first.equals(value)) names.remove(pair)
        }
    }

    fun getScores(except: String): ArrayList<Double> {
        val scores: ArrayList<Double> = ArrayList()
        for (pair in names) {
            if (!except.equals(pair.first)) {
                scores.add(pair.second)
            }
        }
        return scores
    }
}
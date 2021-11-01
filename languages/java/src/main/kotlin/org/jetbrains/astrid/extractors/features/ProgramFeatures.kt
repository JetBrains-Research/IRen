package org.jetbrains.astrid.extractors.features

import java.util.*

class ProgramFeatures(name: String) {
    val features = ArrayList<ProgramRelation>()
    val name: String

    init {
        this.name = name
    }

    override fun toString(): String {
        val joiner = StringJoiner(" ")
        for (feature in features) {
            val toString = feature.toString()
            joiner.add(toString)
        }
        return name + " " +
                joiner.toString()
    }

    internal fun addFeature(source: Property, path: String, target: Property) {
        val newRelation = ProgramRelation(source, target, path)
        features.add(newRelation)
    }

}

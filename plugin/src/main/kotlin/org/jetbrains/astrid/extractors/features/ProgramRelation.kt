package org.jetbrains.astrid.extractors.features

class ProgramRelation(private val source: Property, private val target: Property, private val path: String) {

    override fun toString(): String {
        return String.format("%s,%s,%s", source.getName(), path,
                target.getName())
    }

}

package org.jetbrains.astrid.stats

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "RenameMethodStatistics", storages = [(Storage("astrid_stats.xml"))])
class RenameMethodStatistics : PersistentStateComponent<RenameMethodState> {
    private var statsState: RenameMethodState = RenameMethodState()

    override fun loadState(state: RenameMethodState) {
        statsState = state
    }

    override fun getState(): RenameMethodState {
        return statsState
    }

    companion object {
        fun applyCount(score: Double) {
            val stats = getInstance().statsState
            stats.applyRenameMethod(score)
        }

        fun ignoreCount(scores: List<Double>) {
            val stats = getInstance().statsState
            stats.ignoreRenameMethod(scores)
        }

        fun getInstance(): RenameMethodStatistics = ServiceManager.getService(RenameMethodStatistics::class.java)
    }
}

class RenameMethodState {

    var applied: Int = 0
    var ignored: Int = 0
    var appliedScores: ArrayList<Double> = ArrayList()
    var ignoredScores: ArrayList<Double> = ArrayList()

    fun applyRenameMethod(score: Double) {
        applied += 1
        appliedScores.add(score)
    }

    fun ignoreRenameMethod(scores: List<Double>) {
        ignored += 1
        ignoredScores.addAll(scores)
    }

}
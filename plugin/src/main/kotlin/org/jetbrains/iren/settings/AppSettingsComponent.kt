// Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.iren.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.layout.enableIf
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import org.jetbrains.iren.IRenBundle
import java.time.temporal.ChronoUnit
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Supports creating and managing a [JPanel] for the Settings Dialog.
 */
class AppSettingsComponent {
    val panel: JPanel
    private val myAutomaticTrainingStatus = JBCheckBox(IRenBundle.message("settings.automatic.training"))
    private val myMaxTrainingTimeText = IntegerField("30", 2, 10000)
    private val myVocabularyCutOff = IntegerField("3", 0, 20)
    private val myModelsLifetime = IntegerField()
    private val chronoUnits = listOf(ChronoUnit.HOURS, ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS)
    private val myModelsLifetimeUnit =
        ComboBox(chronoUnits.stream().map { obj: ChronoUnit -> obj.toString() }.toArray())

    init {
        val columnWidth = 9
        myMaxTrainingTimeText.columns = columnWidth
        myVocabularyCutOff.columns = columnWidth
        myModelsLifetime.columns = columnWidth
        panel = panel {
            row {
                myAutomaticTrainingStatus()
            }.largeGapAfter()
            titledRow(IRenBundle.message("settings.title.model.parameters")) {
                row(IRenBundle.message("settings.max.training.time")) {
                    cell {
                        myMaxTrainingTimeText()
                        label("seconds")
                    }
                }
                row(IRenBundle.message("settings.vocabulary.cutoff")) {
                    cell {
                        myVocabularyCutOff()
                        comment(IRenBundle.message("settings.vocabulary.cutoff.comment"))
                    }
                }
            }
            titledRow(IRenBundle.message("settings.title.retraining")) {
                row(IRenBundle.message("settings.models.lifetime")) {
                    cell {
                        myModelsLifetime()
                        myModelsLifetimeUnit()
                        comment(IRenBundle.message("settings.models.lifetime.comment"))
                    }
                }.enableIf(myAutomaticTrainingStatus.selected)
            }
        }
    }

    val preferredFocusedComponent: JComponent
        get() = myAutomaticTrainingStatus

    var automaticTrainingStatus: Boolean
        get() = myAutomaticTrainingStatus.isSelected
        set(newStatus) {
            myAutomaticTrainingStatus.isSelected = newStatus
        }

    @get:Throws(NumberFormatException::class)
    var maxTrainingTime: Int
        get() = myMaxTrainingTimeText.text.toInt()
        set(newText) {
            myMaxTrainingTimeText.text = newText.toString()
        }

    @get:Throws(NumberFormatException::class)
    var vocabularyCutOff: Int
        get() = myVocabularyCutOff.text.toInt()
        set(newText) {
            myVocabularyCutOff.text = newText.toString()
        }

    @get:Throws(NumberFormatException::class)
    var modelsLifetime: Int
        get() = myModelsLifetime.text.toInt()
        set(newText) {
            myModelsLifetime.text = newText.toString()
        }
    var modelsLifetimeUnit: ChronoUnit
        get() = chronoUnits[myModelsLifetimeUnit.selectedIndex]
        set(newUnit) {
            myModelsLifetimeUnit.selectedIndex = chronoUnits.indexOf(newUnit)
        }
}
// Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.iren.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import org.jetbrains.iren.IRenBundle
import java.time.temporal.ChronoUnit
import javax.swing.JPanel

/**
 * Supports creating and managing a [JPanel] for the Settings Dialog.
 */
class AppSettingsComponent {
    val panel: JPanel
    private lateinit var automaticTrainingCell: Cell<JBCheckBox>
    private lateinit var maxTrainingTimeCell: Cell<JBTextField>
    private lateinit var vocabCutoffCell: Cell<JBTextField>
    private lateinit var modelsLifetimeCell: Cell<JBTextField>
    private lateinit var modelsLifetimeUnitCell: Cell<ComboBox<ChronoUnit>>
    private val chronoUnits = listOf(ChronoUnit.HOURS, ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS)

    init {
        val columnWidth = 9
        panel = panel {
            row {
                automaticTrainingCell = checkBox(IRenBundle.message("settings.automatic.training"))
            }
            group(IRenBundle.message("settings.title.model.parameters")) {
                row(IRenBundle.message("settings.max.training.time")) {
                    maxTrainingTimeCell = intTextField(2..10000, 10).columns(columnWidth)
                    label("seconds")
                }
                row(IRenBundle.message("settings.vocabulary.cutoff")) {
                    vocabCutoffCell = intTextField(0..20, 1).columns(columnWidth)
                    comment(IRenBundle.message("settings.vocabulary.cutoff.comment"))
                }
            }
            group(IRenBundle.message("settings.title.retraining")) {
                row(IRenBundle.message("settings.models.lifetime")) {
                    modelsLifetimeCell = intTextField(1..Int.MAX_VALUE, 1).columns(columnWidth)
                    modelsLifetimeUnitCell = comboBox(chronoUnits).columns(columnWidth)
                    comment(IRenBundle.message("settings.models.lifetime.comment"))
                }.enabledIf(automaticTrainingCell.selected)
            }
        }
    }

    var automaticTrainingStatus: Boolean
        get() = automaticTrainingCell.component.isSelected
        set(newStatus) {
            automaticTrainingCell.component.isSelected = newStatus
        }

    var maxTrainingTime: Int
        get() = maxTrainingTimeCell.component.text.toInt()
        set(newText) {
            maxTrainingTimeCell.component.text = newText.toString()
        }

    var vocabularyCutOff: Int
        get() = vocabCutoffCell.component.text.toInt()
        set(newText) {
            vocabCutoffCell.component.text = newText.toString()
        }

    var modelsLifetime: Int
        get() = modelsLifetimeCell.component.text.toInt()
        set(newText) {
            modelsLifetimeCell.component.text = newText.toString()
        }
    var modelsLifetimeUnit: ChronoUnit
        get() = chronoUnits[modelsLifetimeUnitCell.component.selectedIndex]
        set(newUnit) {
            modelsLifetimeUnitCell.component.selectedIndex = chronoUnits.indexOf(newUnit)
        }
}

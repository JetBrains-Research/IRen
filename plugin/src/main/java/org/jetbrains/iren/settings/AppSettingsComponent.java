// Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.iren.settings;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Supports creating and managing a {@link JPanel} for the Settings Dialog.
 */
public class AppSettingsComponent {

    private final JPanel myMainPanel;
    private final JBCheckBox myAutomaticTrainingStatus = new JBCheckBox("Automatic training of models.");
    private final IntegerField myMaxTrainingTimeText = new IntegerField("30", 0, 10000);
    private final IntegerField myVocabularyCutOff = new IntegerField("0", 0, 20);
    private final IntegerField myModelsLifetime = new IntegerField();
    private final List<ChronoUnit> chronoUnits = Arrays.asList(ChronoUnit.HOURS, ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS);
    private final ComboBox<String> myModelsLifetimeUnit = new ComboBox<>(chronoUnits.stream().map(ChronoUnit::toString).toArray(String[]::new));

    public AppSettingsComponent() {
        int columnWidth = 9;
        myMaxTrainingTimeText.setColumns(columnWidth);
        myVocabularyCutOff.setColumns(columnWidth);
        myModelsLifetime.setColumns(columnWidth);
        myMainPanel = FormBuilder.createFormBuilder()
                .addComponent(myAutomaticTrainingStatus, 1)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("Maximal training time of models (s): "), myMaxTrainingTimeText, 1, false)
                .addLabeledComponent(new JBLabel("Vocabulary cutoff: "), myVocabularyCutOff, 1, false)
                .addTooltip("Remove words with small frequencies.")
                .addLabeledComponent(new JBLabel("Models lifetime: "), myModelsLifetime, 1, false)
                .addComponentToRightColumn(this.myModelsLifetimeUnit)
                .addTooltip("The time after which model will be retrained.")
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return myMainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return myAutomaticTrainingStatus;
    }

    public boolean getAutomaticTrainingStatus() {
        return myAutomaticTrainingStatus.isSelected();
    }

    public void setAutomaticTrainingStatus(boolean newStatus) {
        myAutomaticTrainingStatus.setSelected(newStatus);
    }

    public Integer getMaxTrainingTime() {
        try {
            return Integer.parseInt(myMaxTrainingTimeText.getText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setMaxTrainingTime(int newText) {
        myMaxTrainingTimeText.setText(String.valueOf(newText));
    }

    public Integer getVocabularyCutOff() {
        try {
            return Integer.parseInt(myVocabularyCutOff.getText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setVocabularyCutOff(int newText) {
        myVocabularyCutOff.setText(String.valueOf(newText));
    }

    public Integer getModelsLifetime() {
        try {
            return Integer.parseInt(myModelsLifetime.getText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setModelsLifetime(int newText) {
        myModelsLifetime.setText(String.valueOf(newText));
    }

    public ChronoUnit getModelsLifetimeUnit() {
        return chronoUnits.get(myModelsLifetimeUnit.getSelectedIndex());
    }

    public void setModelsLifetimeUnit(ChronoUnit newUnit) {
        myModelsLifetimeUnit.setSelectedIndex(chronoUnits.indexOf(newUnit));
    }
}
/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.structure.configurables.suggestions;

import com.android.tools.adtui.HtmlLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.DefaultCaret;

public abstract class SuggestionViewerUi {
  public static final String SUGGESTION_VIEWER_NAME = "SuggestionViewer";

  protected JPanel myPanel;
  protected HtmlLabel myText;
  protected JBOptionButton myUpdateButton;
  protected JPanel myButtonPanel;
  protected JPanel myTextPanel;
  protected JLabel myIconLabel;
  protected JPanel myIconPanel;

  public SuggestionViewerUi(boolean isLast) {
    setupUI();
    myPanel.setName(SUGGESTION_VIEWER_NAME);
    myPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    myPanel.setBackground(UIUtil.getTextFieldBackground());
    myButtonPanel.setBackground(UIUtil.getTextFieldBackground());
    myTextPanel.setBackground(UIUtil.getTextFieldBackground());
    myIconPanel.setBackground(UIUtil.getTextFieldBackground());
    myText.setBackground(UIUtil.getTextFieldBackground());
    HtmlLabel.setUpAsHtmlLabel(myText, myUpdateButton.getFont());
    // The last item does not need a separator line at the bottom.
    if (isLast) {
      myPanel.setBorder(JBUI.Borders.empty());
    }
  }

  private void createUIComponents() {
    myUpdateButton = new JBOptionButton(null, new Action[0]);
    myUpdateButton.setBackground(UIUtil.getTextFieldBackground());
    myText = new HtmlLabel();
    ((DefaultCaret)myText.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
  }

  /**
   * This code was originally generated by formc, so ignore inspections.
   * If it is modified manually, inspections should be reinstated and issues fixed.
   */
  @SuppressWarnings("HtmlPaneColors")
  private void setupUI() {
    createUIComponents();
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myButtonPanel = new JPanel();
    myButtonPanel.setLayout(new BorderLayout(0, 0));
    myPanel.add(myButtonPanel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
    myButtonPanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    myUpdateButton.setOpaque(false);
    myUpdateButton.setText("Update");
    myButtonPanel.add(myUpdateButton, BorderLayout.NORTH);
    myTextPanel = new JPanel();
    myTextPanel.setLayout(new BorderLayout(0, 0));
    myPanel.add(myTextPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                                 null, 0, false));
    myTextPanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    myText.setContentType("text/html");
    myText.setEditable(false);
    myText.setText("<html>\n  <head>\n    \n  </head>\n  <body>\n    test text test text\n  </body>\n</html>\n");
    myTextPanel.add(myText, BorderLayout.CENTER);
    myIconPanel = new JPanel();
    myIconPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.add(myIconPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                 null, 0, false));
    myIconPanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(8, 8, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    myIconLabel = new JLabel();
    myIconLabel.setText("");
    myIconPanel.add(myIconLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(14, 14), null, 0, false));
  }
}

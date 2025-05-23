/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.dynamicapp;

import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.collections.ObservableList;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.expressions.bool.AndExpression;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class ModuleDownloadConditions {
  private static final String myLinkUrl = AndroidWebHelpProvider.HELP_PREFIX + "r/studio-ui/dynamic-delivery/conditional-delivery";
  public JPanel myRootPanel;
  private JPanel myDeviceFeaturesContainer;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private LinkLabel<Void> myAddDeviceFeatureLinkLabel;
  private HyperlinkLabel myFeatureHelpLink;
  private ObservableList<DeviceFeatureModel> myModel;
  private ObservableValue<Boolean> myIsPanelActive;
  private Project myProject;
  private ValidatorPanel myValidatorPanel;

  public ModuleDownloadConditions() {
    setupUI();
    // Note: BoxLayout can't be set in the Forms designer
    myDeviceFeaturesContainer.setLayout(new BoxLayout(myDeviceFeaturesContainer, BoxLayout.Y_AXIS));

    myAddDeviceFeatureLinkLabel.setIcon(null); // Clear default icon

    // For UI testing
    myAddDeviceFeatureLinkLabel.setName("ModuleDownloadConditions.myAddDeviceFeatureLinkLabel");
    myDeviceFeaturesContainer.setName("ModuleDownloadConditions.myDeviceFeaturesContainer");

    // Handle the "+ device-feature" button
    myAddDeviceFeatureLinkLabel.setListener(new LinkListener<Void>() {
      @Override
      public void linkSelected(LinkLabel aSource, Void aLinkData) {
        addDeviceFeatureRow();
      }
    }, null);
    myFeatureHelpLink.setHyperlinkTarget(myLinkUrl);
    myFeatureHelpLink.setHtmlText("<html><a>Learn more</a> about supported conditions, such as device features and user country</html>");
  }

  public void init(@NotNull Project project,
                   @NotNull ValidatorPanel validatorPanel,
                   @NotNull ObservableValue<Boolean> isPanelActive) {
    myProject = project;
    myValidatorPanel = validatorPanel;
    myIsPanelActive = isPanelActive;
  }

  public void setModel(@NotNull ObservableList<DeviceFeatureModel> model) {
    myModel = model;
  }

  private void addDeviceFeatureRow() {
    if (myModel != null) {
      // Create model and form for new device feature
      DeviceFeatureModel deviceFeature = new DeviceFeatureModel();
      myModel.add(deviceFeature);

      BoolValueProperty isFeatureActive = new BoolValueProperty(true);
      BooleanExpression isFeatureActiveExpression = new AndExpression(isFeatureActive, myIsPanelActive);
      ModuleDownloadDeviceFeature deviceFeatureForm =
        new ModuleDownloadDeviceFeature(myProject, deviceFeature, isFeatureActiveExpression, myValidatorPanel);
      deviceFeatureForm.addListener(new ModuleDownloadDeviceFeatureListener() {
        @Override
        public void removeFeatureInvoked() {
          isFeatureActive.set(false);
          deviceFeature.deviceFeatureValue.clear();
          removeDeviceFeatureRow(deviceFeature);
        }
      });

      // Add new component at bottom of layout
      myDeviceFeaturesContainer.add(deviceFeatureForm.getComponent(), -1);
      myDeviceFeaturesContainer.revalidate();
      myDeviceFeaturesContainer.repaint();
    }
  }

  private void removeDeviceFeatureRow(@NotNull DeviceFeatureModel deviceFeatureModel) {
    int rowIndex = myModel.indexOf(deviceFeatureModel);
    if (rowIndex < 0) {
      //TODO: warning
      return;
    }

    // Remove from model
    myModel.remove(rowIndex);

    // Remove component at [rowIndex] from container
    myDeviceFeaturesContainer.remove(rowIndex);
    myDeviceFeaturesContainer.revalidate();
    myDeviceFeaturesContainer.repaint();
  }

  private void setupUI() {
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new GridLayoutManager(5, 4, new Insets(0, 0, 0, 0), -1, -1));
    myDeviceFeaturesContainer = new JPanel();
    myDeviceFeaturesContainer.setLayout(new GridBagLayout());
    myRootPanel.add(myDeviceFeaturesContainer, new GridConstraints(0, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_WANT_GROW,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myAddDeviceFeatureLinkLabel = new LinkLabel();
    myAddDeviceFeatureLinkLabel.setText("+ device-feature");
    myRootPanel.add(myAddDeviceFeatureLinkLabel, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myRootPanel.add(spacer1, new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myFeatureHelpLink = new HyperlinkLabel();
    myFeatureHelpLink.setText("");
    myRootPanel.add(myFeatureHelpLink, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    myRootPanel.add(spacer2, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 16), null, 0, false));
  }

  public JComponent getRootComponent() { return myRootPanel; }
}

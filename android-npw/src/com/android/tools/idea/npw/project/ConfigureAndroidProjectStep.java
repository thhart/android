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
package com.android.tools.idea.npw.project;

import static com.android.tools.adtui.validation.Validator.Result.OK;
import static com.android.tools.adtui.validation.Validator.Severity.ERROR;
import static com.android.tools.adtui.validation.Validator.Severity.WARNING;
import static com.android.tools.idea.npw.FormFactorUtilKt.toWizardFormFactor;
import static com.android.tools.idea.npw.model.NewProjectModel.nameToJavaPackage;
import static com.android.tools.idea.npw.module.AndroidApiLevelComboBoxKt.ensureDefaultApiLevelAtLeastRecommended;
import static com.android.tools.idea.npw.platform.AndroidVersionsInfoKt.getSdkManagerLocalPath;
import static com.android.tools.idea.wizard.template.TemplateDataKt.KOTLIN_DSL_LINK;
import static com.android.tools.idea.wizard.ui.WizardUtils.wrapWithVScroll;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor;
import static java.lang.String.format;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.ide.common.repository.AgpVersion;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.UpdatablePackage;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.plugin.AgpVersions;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.npw.model.AgpVersionSelector;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.model.NewProjectModuleModel;
import com.android.tools.idea.npw.module.ConfigureModuleStepKt;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem;
import com.android.tools.idea.npw.template.components.LanguageComboProvider;
import com.android.tools.idea.npw.validator.ProjectNameValidator;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.expressions.value.TransformOptionalExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep;
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.validation.validators.StringPathValidator;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject;
import com.android.tools.idea.wizard.template.FormFactor;
import com.android.tools.idea.wizard.template.Language;
import com.android.tools.idea.wizard.template.Template;
import com.android.tools.idea.wizard.template.TemplateConstraint;
import com.android.tools.idea.wizard.ui.StudioWizardLayout;
import com.android.tools.idea.wizard.ui.WizardUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.containers.ContainerUtil;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import javax.swing.DefaultComboBoxModel;
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * First page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidProjectStep extends ModelWizardStep<NewProjectModuleModel> {
  private final NewProjectModel myProjectModel;

  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final List<UpdatablePackage> myInstallRequests = new ArrayList<>();
  private final List<RemotePackage> myInstallLicenseRequests = new ArrayList<>();
  private final LicenseAgreementStep myLicenseAgreementStep;

  private final @NotNull JBScrollPane myRootPanel;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myProjectLocation;
  private JTextField myAppName;
  private JTextField myPackageName;
  private JComboBox<Language> myProjectLanguage;
  private JBLabel myProjectLanguageLabel;
  private JBCheckBox myWearCheck;
  private JBCheckBox myTvCheck;
  private JBLabel myTemplateTitle;
  private JBLabel myTemplateDetail;
  private HyperlinkLabel myDocumentationLink;
  private JPanel myFormFactorSdkControlsPanel;
  private JComboBox myMinSdkCombo;
  private JComboBox<BuildConfigurationLanguageForNewProject> myBuildConfigurationLanguageCombo;
  private ContextHelpLabel myBuildConfigurationLanguageLabel;

  private ContextHelpLabel myAndroidGradlePluginLabel;
  private JComboBox<AgpVersions.NewProjectWizardAgpVersion> myAndroidGradlePluginCombo;
  private FormFactorSdkControls myFormFactorSdkControls;

  public ConfigureAndroidProjectStep(@NotNull NewProjectModuleModel newProjectModuleModel, @NotNull NewProjectModel projectModel) {
    super(newProjectModuleModel, message("android.wizard.project.new.configure"));

    myProjectModel = projectModel;
    setupUI();
    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myRootPanel = wrapWithVScroll(myValidatorPanel);

    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);

    myLicenseAgreementStep = new LicenseAgreementStep(new LicenseAgreementModel(getSdkManagerLocalPath()), () -> myInstallLicenseRequests,
                                                      StudioWizardLayout.DEFAULT_BORDER_INSETS);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep<?>> createDependentSteps() {
    InstallSelectedPackagesStep installPackagesStep =
      new InstallSelectedPackagesStep(myInstallRequests, new HashSet<>(), () -> AndroidSdks.getInstance().tryToChooseSdkHandler(), false,
                                      StudioWizardLayout.DEFAULT_BORDER_INSETS);

    return Lists.newArrayList(myLicenseAgreementStep, installPackagesStep);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    ((GridLayoutManager)myPanel.getLayout()).setVGap(2);

    myBindings.bindTwoWay(new TextProperty(myAppName), myProjectModel.getApplicationName());

    String basePackage = NewProjectModel.getSuggestedProjectPackage();

    Expression<String> computedPackageName = myProjectModel.getApplicationName()
      .transform(appName -> format("%s.%s", basePackage, nameToJavaPackage(appName)));
    TextProperty packageNameText = new TextProperty(myPackageName);
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    myBindings.bind(myProjectModel.getPackageName(), packageNameText);

    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myListeners.listen(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    Expression<String> computedLocation = myProjectModel.getApplicationName().transform(ConfigureAndroidProjectStep::findProjectLocation);
    TextProperty locationText = new TextProperty(myProjectLocation.getTextField());
    BoolProperty isLocationSynced = new BoolValueProperty(true);
    myBindings.bind(locationText, computedLocation, isLocationSynced);
    myBindings.bind(myProjectModel.getProjectLocation(), locationText.trim());
    myListeners.listen(locationText, value -> isLocationSynced.set(value.equals(computedLocation.get())));

    OptionalProperty<VersionItem> androidSdkInfo = getModel().androidSdkInfo();
    myFormFactorSdkControls.init(androidSdkInfo, this);

    myBindings.bindTwoWay(new SelectedItemProperty<>(myProjectLanguage), myProjectModel.getLanguage());

    if (StudioFlags.NPW_SHOW_KTS_GRADLE_COMBO_BOX.get()) {
      myBuildConfigurationLanguageCombo.addItem(BuildConfigurationLanguageForNewProject.KTS);
      myBuildConfigurationLanguageCombo.addItem(BuildConfigurationLanguageForNewProject.Groovy);
      myBindings.bind(myProjectModel.getUseGradleKts(), new TransformOptionalExpression<BuildConfigurationLanguageForNewProject, Boolean>(true, new SelectedItemProperty<>(myBuildConfigurationLanguageCombo)) {
                        @NotNull
                        @Override
                        protected Boolean transform(@NotNull BuildConfigurationLanguageForNewProject value) {
                          return value.getUseKts();
                        }
                      });
    } else {
      myBuildConfigurationLanguageLabel.setVisible(false);
      myBuildConfigurationLanguageCombo.setVisible(false);
    }

    if (StudioFlags.NPW_SHOW_AGP_VERSION_COMBO_BOX.get() || (StudioFlags.NPW_SHOW_AGP_VERSION_COMBO_BOX_EXPERIMENTAL_SETTING.get() &&
                                                             GradleExperimentalSettings.getInstance().SHOW_ANDROID_GRADLE_PLUGIN_VERSION_COMBO_BOX_IN_NEW_PROJECT_WIZARD)) {
      AgpVersions.NewProjectWizardAgpVersion
        placeholderCurrentVersion = new AgpVersions.NewProjectWizardAgpVersion(
          /* Resolve as if IdeGoogleMavenRepository.getAgpVersions() is not available as a placeholder */
          myProjectModel.getAgpVersionSelector().get().resolveVersion(ImmutableSet::of),
          ImmutableList.of(),
          "");
      myAndroidGradlePluginCombo.addItem(placeholderCurrentVersion);
      myBindings.bind(myProjectModel.getAgpVersionSelector(), ObjectProperty.wrap(new SelectedItemProperty<>(myAndroidGradlePluginCombo)).transform(it -> new AgpVersionSelector.FixedVersion(((AgpVersions.NewProjectWizardAgpVersion)it).getVersion())));
      myBindings.bind(myProjectModel.getAdditionalMavenRepos(), ObjectProperty.wrap(new SelectedItemProperty<>(myAndroidGradlePluginCombo)).transform(it -> ((AgpVersions.NewProjectWizardAgpVersion)it).getAdditionalMavenRepositoryUrls()));

      BackgroundTaskUtil.executeOnPooledThread(this, () -> {
        List<AgpVersions.NewProjectWizardAgpVersion> suggestedAgpVersions = AgpVersions.INSTANCE.getNewProjectWizardVersions();
        AgpVersion newProjectDefault = myProjectModel.getAgpVersionSelector().get()
          // Use new project wizard versions to resolve here (rather than just AgpVersions.getAvailableVersions()) to avoid having to
          // consider the corner case of skew between getNewProjectWizardVersions() and getAvailableVersions().
          .resolveVersion(() -> suggestedAgpVersions.stream().map(AgpVersions.NewProjectWizardAgpVersion::getVersion).collect(Collectors.toSet()));

        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), () -> {
          boolean foundCurrent = false;
          for (AgpVersions.NewProjectWizardAgpVersion version : suggestedAgpVersions) {
            if (!foundCurrent && version.getVersion().equals(newProjectDefault) && version.getAdditionalMavenRepositoryUrls().isEmpty()) {
              foundCurrent = true;
              AgpVersions.NewProjectWizardAgpVersion newProjectWizardVersion = new AgpVersions.NewProjectWizardAgpVersion(version.getVersion(), ImmutableList.of(), "New project default");
              myAndroidGradlePluginCombo.addItem(newProjectWizardVersion);
              myAndroidGradlePluginCombo.setSelectedItem(newProjectWizardVersion);
              myAndroidGradlePluginCombo.removeItem(placeholderCurrentVersion);
            } else {
              myAndroidGradlePluginCombo.addItem(version);
            }
          }
        });
      });
      ConfigureModuleStepKt.registerKtsAgpVersionValidation(myValidatorPanel, myProjectModel);
    } else {
      myAndroidGradlePluginLabel.setVisible(false);
      myAndroidGradlePluginCombo.setVisible(false);
    }

    myValidatorPanel.registerValidator(myProjectModel.getApplicationName(), new ProjectNameValidator());

    myValidatorPanel.registerValidator(myProjectModel.getProjectLocation(),
                                       new StringPathValidator(PathValidator.createDefault("Save location")));

    myValidatorPanel.registerValidator(myProjectModel.getPackageName(),
                                       value -> Validator.Result.fromNullableMessage(AndroidUtils.validatePackageName(value)));

    myValidatorPanel.registerValidator(myProjectModel.getLanguage(), value ->
      value.isPresent() ? OK : new Validator.Result(ERROR, message("android.wizard.validate.select.language")));

    myValidatorPanel.registerValidator(androidSdkInfo, value ->
      value.isPresent() ? OK : new Validator.Result(ERROR, message("select.target.dialog.text")));

    myValidatorPanel.registerValidator(
      androidSdkInfo,
      value -> !value.isPresent() || hasValidSdkComposeVersion(value.get(), getModel().newRenderTemplate.getValueOrNull()) ? OK :
               new Validator.Result(WARNING, message("android.wizard.validate.select.compose.sdk")),
      getModel().newRenderTemplate
    );

    myProjectLocation.addBrowseFolderListener(null, createSingleFolderDescriptor());

    myListeners.listenAndFire(getModel().formFactor, () -> {
      FormFactor formFactor = getModel().formFactor.get();

      myFormFactorSdkControls.showStatsPanel(formFactor == FormFactor.Mobile);
      myWearCheck.setVisible(formFactor == FormFactor.Wear);
      myTvCheck.setVisible(formFactor == FormFactor.Tv);
    });
  }

  @Override
  protected void onEntering() {
    FormFactor formFactor = getModel().formFactor.get();
    Template newTemplate = getModel().newRenderTemplate.getValue();

    int minSdk = newTemplate.getMinSdk();

    ensureDefaultApiLevelAtLeastRecommended();
    myFormFactorSdkControls.startDataLoading(toWizardFormFactor(formFactor), minSdk);
    setTemplateThumbnail(newTemplate);
    boolean isKotlinOnly = newTemplate.getConstraints().contains(TemplateConstraint.Kotlin);

    myProjectLanguage.setVisible(!isKotlinOnly);
    myProjectLanguageLabel.setVisible(!isKotlinOnly);
    if (isKotlinOnly) {
      myProjectModel.getLanguage().setValue(Language.Kotlin);
    }
  }

  @Override
  protected void onProceeding() {
    getModel().hasCompanionApp.set(
      (myWearCheck.isVisible() && myWearCheck.isSelected()) ||
      (myTvCheck.isVisible() && myTvCheck.isSelected()) ||
      getModel().formFactor.get() == FormFactor.Automotive // Automotive projects include a mobile module for Android Auto by default
    );

    myInstallRequests.clear();
    myInstallLicenseRequests.clear();

    myInstallRequests.addAll(myFormFactorSdkControls.getSdkInstallPackageList());
    myInstallLicenseRequests.addAll(ContainerUtil.map(myInstallRequests, UpdatablePackage::getRemote));

    myLicenseAgreementStep.reload();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myAppName;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  private void setupUI() {
    createUIComponents();
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(21, 2, new Insets(0, 0, 0, 0), -1, -1));
    myAppName = new JTextField();
    myAppName.setToolTipText("The name that will be shown in the Android launcher for this application");
    myPanel.add(myAppName, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                               new Dimension(150, -1), null, 0, false));
    myPackageName = new JTextField();
    myPanel.add(myPackageName, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                   new Dimension(150, -1), null, 0, false));
    myProjectLocation = new TextFieldWithBrowseButton();
    myPanel.add(myProjectLocation, new GridConstraints(9, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                       new Dimension(150, -1), null, 0, false));
    myPanel.add(myProjectLanguage, new GridConstraints(11, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 14), null, 0, false));
    final Spacer spacer2 = new Spacer();
    myPanel.add(spacer2, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 14), null, 0, false));
    final Spacer spacer3 = new Spacer();
    myPanel.add(spacer3, new GridConstraints(10, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 14), null, 0, false));
    myPanel.add(myFormFactorSdkControlsPanel, new GridConstraints(14, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final Spacer spacer4 = new Spacer();
    myPanel.add(spacer4, new GridConstraints(15, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 14), null, 0, false));
    final Spacer spacer5 = new Spacer();
    myPanel.add(spacer5, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 20), null, 0, false));
    myWearCheck = new JBCheckBox();
    Font myWearCheckFont = getFont(null, Font.PLAIN, -1, myWearCheck.getFont());
    if (myWearCheckFont != null) myWearCheck.setFont(myWearCheckFont);
    myWearCheck.setLabel("Pair with Empty Phone app");
    myWearCheck.setText("Pair with Empty Phone app");
    myPanel.add(myWearCheck, new GridConstraints(16, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myTvCheck = new JBCheckBox();
    Font myTvCheckFont = getFont(null, Font.PLAIN, -1, myTvCheck.getFont());
    if (myTvCheckFont != null) myTvCheck.setFont(myTvCheckFont);
    myTvCheck.setLabel("Pair with companion Phone app");
    myTvCheck.setText("Pair with companion Phone app");
    myPanel.add(myTvCheck, new GridConstraints(17, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel1 = new JBLabel();
    Font jBLabel1Font = getFont(null, Font.PLAIN, -1, jBLabel1.getFont());
    if (jBLabel1Font != null) jBLabel1.setFont(jBLabel1Font);
    jBLabel1.setText("Name");
    jBLabel1.setDisplayedMnemonic('N');
    jBLabel1.setDisplayedMnemonicIndex(0);
    jBLabel1.setVerticalAlignment(0);
    myPanel.add(jBLabel1,
                new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 24), null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    Font jBLabel2Font = getFont(null, Font.PLAIN, -1, jBLabel2.getFont());
    if (jBLabel2Font != null) jBLabel2.setFont(jBLabel2Font);
    jBLabel2.setText("Package name");
    jBLabel2.setDisplayedMnemonic('P');
    jBLabel2.setDisplayedMnemonicIndex(0);
    jBLabel2.setVerticalAlignment(0);
    myPanel.add(jBLabel2,
                new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel3 = new JBLabel();
    Font jBLabel3Font = getFont(null, Font.PLAIN, -1, jBLabel3.getFont());
    if (jBLabel3Font != null) jBLabel3.setFont(jBLabel3Font);
    jBLabel3.setText("Save location");
    jBLabel3.setDisplayedMnemonic('S');
    jBLabel3.setDisplayedMnemonicIndex(0);
    jBLabel3.setVerticalAlignment(0);
    myPanel.add(jBLabel3,
                new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myProjectLanguageLabel = new JBLabel();
    Font myProjectLanguageLabelFont = getFont(null, Font.PLAIN, -1, myProjectLanguageLabel.getFont());
    if (myProjectLanguageLabelFont != null) myProjectLanguageLabel.setFont(myProjectLanguageLabelFont);
    myProjectLanguageLabel.setText("Language");
    myProjectLanguageLabel.setDisplayedMnemonic('L');
    myProjectLanguageLabel.setDisplayedMnemonicIndex(0);
    myProjectLanguageLabel.setVerticalAlignment(0);
    myPanel.add(myProjectLanguageLabel,
                new GridConstraints(11, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer6 = new Spacer();
    myPanel.add(spacer6, new GridConstraints(12, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 14), null, 0, false));
    myTemplateTitle = new JBLabel();
    Font myTemplateTitleFont = getFont(null, Font.BOLD, -1, myTemplateTitle.getFont());
    if (myTemplateTitleFont != null) myTemplateTitle.setFont(myTemplateTitleFont);
    myTemplateTitle.setText("Template title");
    myTemplateTitle.setVerticalAlignment(0);
    myPanel.add(myTemplateTitle,
                new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 24), null, 0, false));
    final Spacer spacer7 = new Spacer();
    myPanel.add(spacer7, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 14), null, 0, false));
    final Spacer spacer8 = new Spacer();
    myPanel.add(spacer8, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 14), null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.add(panel1, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                            0, false));
    myTemplateDetail = new JBLabel();
    Font myTemplateDetailFont = getFont(null, Font.PLAIN, -1, myTemplateDetail.getFont());
    if (myTemplateDetailFont != null) myTemplateDetail.setFont(myTemplateDetailFont);
    myTemplateDetail.setText("Template detail");
    myTemplateDetail.setVerticalAlignment(0);
    panel1.add(myTemplateDetail, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 24), null, 0, false));
    myDocumentationLink = new HyperlinkLabel();
    myDocumentationLink.setText("");
    panel1.add(myDocumentationLink, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel4 = new JBLabel();
    Font jBLabel4Font = getFont(null, Font.PLAIN, -1, jBLabel4.getFont());
    if (jBLabel4Font != null) jBLabel4.setFont(jBLabel4Font);
    jBLabel4.setText("Minimum SDK");
    jBLabel4.setVerticalAlignment(0);
    myPanel.add(jBLabel4,
                new GridConstraints(13, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myPanel.add(myMinSdkCombo, new GridConstraints(13, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
    myBuildConfigurationLanguageCombo = new JComboBox();
    final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
    myBuildConfigurationLanguageCombo.setModel(defaultComboBoxModel1);
    myBuildConfigurationLanguageCombo.setName("buildConfigurationLanguageCombo");
    myPanel.add(myBuildConfigurationLanguageCombo,
                new GridConstraints(18, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBuildConfigurationLanguageLabel.setText("Build configuration language");
    myPanel.add(myBuildConfigurationLanguageLabel,
                new GridConstraints(18, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myAndroidGradlePluginLabel.setText("Android Gradle plugin (internal option)");
    myPanel.add(myAndroidGradlePluginLabel,
                new GridConstraints(19, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myAndroidGradlePluginCombo = new JComboBox();
    myPanel.add(myAndroidGradlePluginCombo, new GridConstraints(19, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                null, null, null, 0, false));
    final Spacer spacer9 = new Spacer();
    myPanel.add(spacer9, new GridConstraints(20, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    jBLabel1.setLabelFor(myAppName);
    jBLabel2.setLabelFor(myPackageName);
    jBLabel3.setLabelFor(myProjectLocation);
    myProjectLanguageLabel.setLabelFor(myProjectLanguage);
    myTemplateTitle.setLabelFor(myAppName);
    myTemplateDetail.setLabelFor(myAppName);
    jBLabel4.setLabelFor(myProjectLanguage);
  }

  private Font getFont(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    }
    else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      }
      else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac
                            ? new Font(font.getFamily(), font.getStyle(), font.getSize())
                            : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }

  @NotNull
  private static String findProjectLocation(@NotNull String applicationName) {
    applicationName = NewProjectModel.sanitizeApplicationName(applicationName);
    File baseDirectory = WizardUtils.getProjectLocationParent();
    File projectDirectory = new File(baseDirectory, applicationName);

    // Try appName, appName2, appName3, ...
    int counter = 2;
    while (projectDirectory.exists()) {
      projectDirectory = new File(baseDirectory, format(Locale.US, "%s%d", applicationName, counter++));
    }

    return projectDirectory.getPath();
  }

  private void setTemplateThumbnail(@NotNull Template template) {
    myTemplateTitle.setText(template.getName());
    myTemplateDetail.setText("<html>" + template.getDescription() + "</html>");

    String documentationUrl = template.getDocumentationUrl();
    if (documentationUrl != null) {
      myDocumentationLink.setHyperlinkText(message("android.wizard.activity.add.cpp.docslinktext"));
      myDocumentationLink.setHyperlinkTarget(documentationUrl);
    }
    myDocumentationLink.setVisible(documentationUrl != null);
  }

  private static boolean hasValidSdkComposeVersion(VersionItem sdkItem, @Nullable Template renderTemplate) {
    return renderTemplate == null ||
           !renderTemplate.getConstraints().contains(TemplateConstraint.Compose) ||
           sdkItem.getCompileSdk().isAtLeast(AndroidVersion.VersionCodes.S);
  }

  private void createUIComponents() {
    myProjectLanguage = new LanguageComboProvider().createComponent();
    myFormFactorSdkControls = new FormFactorSdkControls();
    myFormFactorSdkControlsPanel = myFormFactorSdkControls.getRoot();
    myMinSdkCombo = myFormFactorSdkControls.getMinSdkComboBox();
    myBuildConfigurationLanguageLabel = ContextHelpLabel.createWithLink(
      null,
      AndroidBundle.message("android.wizard.project.help.buildconfigurationlanguage.description"),
      "Learn more",
      () -> BrowserUtil.browse(KOTLIN_DSL_LINK));
    myBuildConfigurationLanguageLabel.setHorizontalTextPosition(SwingConstants.LEFT);
    myAndroidGradlePluginLabel = ContextHelpLabel.create(
      "This is only shown for development builds of Android Studio",
      "It can be hidden while idea.is.internal=true by setting Studio flag " + StudioFlags.NPW_SHOW_AGP_VERSION_COMBO_BOX.getId() + " to false");
    myAndroidGradlePluginLabel.setHorizontalTextPosition(SwingConstants.LEFT);
  }
}
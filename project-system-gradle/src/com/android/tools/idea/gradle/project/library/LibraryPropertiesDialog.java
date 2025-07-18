/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.library;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryRootsComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY;
import static com.intellij.util.ui.JBUI.Borders.customLine;
import static com.intellij.util.ui.UIUtil.findComponentOfType;

public class LibraryPropertiesDialog extends DialogWrapper {
  @NotNull private final Project myProject;
  @NotNull private final Library myLibrary;

  private JPanel myMainPanel;
  private JPanel myTreePanel;
  private JBLabel myIconLabel;
  private JBLabel myNameLabel;

  private LibraryRootsComponent myLibraryEditorComponent;
  private SourcesAndDocsOnlyEditor myEditor;

  public LibraryPropertiesDialog(@NotNull Project project, @NotNull Library library) {
    super(project);
    setupUI();
    myProject = project;
    myLibrary = library;
    init();
    setTitle("Library Properties");
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myIconLabel.setIcon(AllIcons.Nodes.PpLib);
    myNameLabel.setText(myLibrary.getName());

    myEditor = new SourcesAndDocsOnlyEditor(myLibrary);
    myLibraryEditorComponent = new LibraryRootsComponent(myProject, myEditor) {
      @Override
      public void updatePropertiesLabel() {
        JComponent c = getComponent();
        if (c != null) {
          MultiLineLabel propertiesLabel = findComponentOfType(c, MultiLineLabel.class);
          if (propertiesLabel != null) {
            propertiesLabel.setText("Add or remove source/Javadoc attachments");
          }
        }
      }
    };
    myLibraryEditorComponent.updatePropertiesLabel();

    JComponent c = myLibraryEditorComponent.getComponent();

    MultiLineLabel propertiesLabel = findComponentOfType(c, MultiLineLabel.class);
    if (propertiesLabel != null) {
      propertiesLabel.setBorder(BorderFactory.createEmptyBorder(1, 1, 0, 1));
    }

    myTreePanel.add(c, BorderLayout.CENTER);
    myTreePanel.setBorder(customLine(OnePixelDivider.BACKGROUND, 1, 1, 1, 1));

    return myMainPanel;
  }

  @Override
  protected void dispose() {
    if (myLibraryEditorComponent != null) {
      Disposer.dispose(myLibraryEditorComponent);
    }
    super.dispose();
  }

  public void applyChanges() {
    if (myEditor != null) {
      executeProjectChanges(myProject, myEditor::commit);
    }
  }

  private static void executeProjectChanges(@NotNull Project project, @NotNull Runnable changes) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      if (!project.isDisposed()) {
        changes.run();
      }
      return;
    }
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      if (!project.isDisposed()) {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(changes);
      }
    }));
  }

  @VisibleForTesting
  LibraryRootsComponent getLibraryEditorComponent() {
    return myLibraryEditorComponent;
  }

  private void setupUI() {
    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    myTreePanel = new JPanel();
    myTreePanel.setLayout(new BorderLayout(0, 0));
    myMainPanel.add(myTreePanel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     new Dimension(600, 400), null, 0, false));
    myIconLabel = new JBLabel();
    myMainPanel.add(myIconLabel,
                    new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1, 1, null, null, null, 0,
                                        false));
    myNameLabel = new JBLabel();
    myMainPanel.add(myNameLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                                     null, 0, false));
  }

  private static class SourcesAndDocsOnlyEditor extends ExistingLibraryEditor {
    SourcesAndDocsOnlyEditor(@NotNull Library library) {
      super(library, null);
    }

    @NotNull
    @Override
    public String[] getUrls(@NotNull OrderRootType rootType) {
      if (isIgnored(rootType)) {
        return EMPTY_STRING_ARRAY;
      }
      return super.getUrls(rootType);
    }

    @Override
    public void addRoot(@NotNull VirtualFile file, @NotNull OrderRootType rootType) {
      if (!isIgnored(rootType)) {
        super.addRoot(file, rootType);
      }
    }

    @Override
    public void addRoot(@NotNull String url, @NotNull OrderRootType rootType) {
      if (!isIgnored(rootType)) {
        super.addRoot(url, rootType);
      }
    }

    @Override
    public void removeRoot(@NotNull String url, @NotNull OrderRootType rootType) {
      if (!isIgnored(rootType)) {
        super.removeRoot(url, rootType);
      }
    }

    private static boolean isIgnored(@NotNull OrderRootType rootType) {
      return rootType == CLASSES;
    }
  }
}

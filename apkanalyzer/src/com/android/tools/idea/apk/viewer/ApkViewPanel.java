/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer;

import static com.android.tools.idea.apk.viewer.pagealign.AlignmentFindingKt.findPageAlignWarningsPaths;
import static com.google.wireless.android.sdk.stats.ApkAnalyzerStats.ApkAnalyzerAlignNative16kbEventType.ALIGN_NATIVE_COMPLIANT_APK_ANALYZED;
import static com.google.wireless.android.sdk.stats.ApkAnalyzerStats.ApkAnalyzerAlignNative16kbEventType.ALIGN_NATIVE_NON_COMPLIANT_APK_ANALYZED;

import com.android.SdkConstants;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.util.HumanReadableUtil;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.apk.analyzer.AndroidApplicationInfo;
import com.android.tools.apk.analyzer.ArchiveEntry;
import com.android.tools.apk.analyzer.ArchiveErrorEntry;
import com.android.tools.apk.analyzer.ArchiveNode;
import com.android.tools.apk.analyzer.ArchiveTreeStructure;
import com.android.tools.apk.analyzer.Archives;
import com.android.tools.apk.analyzer.internal.ApkArchive;
import com.android.tools.apk.analyzer.internal.ArchiveTreeNode;
import com.android.tools.apk.analyzer.internal.InstantAppBundleArchive;
import static com.android.tools.idea.apk.viewer.pagealign.AlignmentFindingKt.IS_PAGE_ALIGN_ENABLED;

import com.android.tools.idea.apk.viewer.pagealign.AlignmentCellRenderer;
import com.android.tools.idea.stats.AnonymizerUtil;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.ApkAnalyzerStats;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.IconManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

public class ApkViewPanel implements TreeSelectionListener {
  private JPanel myContainer;
  @SuppressWarnings("unused") // added to the container in the form
  private JComponent myColumnTreePane;
  private SimpleColoredComponent myNameComponent;
  private SimpleColoredComponent mySizeComponent;
  private AnimatedIcon myNameAsyncIcon;
  private AnimatedIcon mySizeAsyncIcon;
  private JButton myCompareWithButton;
  private Tree myTree;

  private ApkTreeModel myTreeModel;
  private Listener myListener;
  @NotNull private final ApkParser myApkParser;
  private boolean myArchiveDisposed = false;

  private static final int TEXT_RENDERER_HORIZ_PADDING = 6;
  private static final int TEXT_RENDERER_VERT_PADDING = 4;

  private void setupUI() {
    createUIComponents();
    myContainer = new JPanel();
    myContainer.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    final Spacer spacer1 = new Spacer();
    myContainer.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myContainer.add(myColumnTreePane, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
    myContainer.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                false));
    myNameComponent = new SimpleColoredComponent();
    panel1.add(myNameComponent);
    panel1.add(myNameAsyncIcon);
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
    myContainer.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                false));
    mySizeComponent = new SimpleColoredComponent();
    panel2.add(mySizeComponent);
    panel2.add(mySizeAsyncIcon);
    myCompareWithButton = new JButton();
    myCompareWithButton.setText("Compare with previous APK...");
    myContainer.add(myCompareWithButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                             null, 0, false));
  }

  public JComponent getRootComponent() { return myContainer; }

  public interface Listener {
    void selectionChanged(ArchiveTreeNode @Nullable [] entry);

    void selectApkAndCompare();
  }

  public ApkViewPanel(
    @NotNull ApkParser apkParser,
    @NotNull String apkName,
    @NotNull AndroidApplicationInfoProvider applicationInfoProvider) {
    myApkParser = apkParser;
    // construct the main tree along with the uncompressed sizes
    setupUI();
    Futures.addCallback(apkParser.constructTreeStructure(), new FutureCallBackAdapter<>() {
      @Override
      public void onSuccess(ArchiveNode result) {
        if (myArchiveDisposed) {
          return;
        }
        setRootNode(result);
      }
    }, EdtExecutorService.getInstance());

    // kick off computation of the compressed archive, and once its available, refresh the tree
    Futures.addCallback(apkParser.updateTreeWithDownloadSizes(), new FutureCallBackAdapter<>() {
      @Override
      public void onSuccess(ArchiveNode result) {
        if (myArchiveDisposed) {
          return;
        }
        ArchiveTreeStructure
          .sort(result, (o1, o2) -> Longs.compare(o2.getData().getDownloadFileSize(), o1.getData().getDownloadFileSize()));
        try {
          refreshTree();
          if (IS_PAGE_ALIGN_ENABLED) {
            myTree.expandPaths(findPageAlignWarningsPaths(result, myTreeModel.getExtractNativeLibs()));
          }
        }
        catch (Exception e) {
          // Ignore exceptions if the archive was disposed (b/351919218)
          if (!myArchiveDisposed) {
            throw e;
          }
        }
      }
    }, EdtExecutorService.getInstance());

    myContainer.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    myCompareWithButton.addActionListener(e -> {
      if (myListener != null) {
        myListener.selectApkAndCompare();
      }
    });

    // identify and set the application name and version
    myNameAsyncIcon.setVisible(true);
    myNameComponent.append("Parsing Manifest");

    //find a suitable archive that has an AndroidManifest.xml file in the root ("/")
    //for APKs, this will always be the APK itself
    //for ZIP files (AIA bundles), this will be the first found APK using breadth-first search
    ListenableFuture<AndroidApplicationInfo> applicationInfo =
      Futures.transformAsync(
        apkParser.constructTreeStructure(),
        input -> {
          assert input != null;
          ArchiveEntry entry = Archives.getFirstManifestArchiveEntry(input);
          if (entry == null) {
            setToZipMode(apkName);
            return Futures.immediateFailedFuture(new Exception("Regular .zip, not valid .apk file."));
          }
          else {
            try {
              return applicationInfoProvider.getApplicationInfo(apkParser, entry);
            }
            catch (Exception e) {
              setToZipMode(apkName);
              Logger.getInstance(ApkViewPanel.class).warn(e);
              return Futures.immediateFailedFuture(e);
            }
          }
        },
        PooledThreadExecutor.INSTANCE);

    ListenableFuture<Long> uncompressedApkSize = apkParser.getUncompressedApkSize();
    ListenableFuture<Long> compressedFullApkSize = apkParser.getCompressedFullApkSize();
    ListenableFuture<ApkParser.Align16kbCompliance> align16kbCompliance = apkParser.getAlign16kbCompliance();
    Futures.addCallback(applicationInfo, new FutureCallBackAdapter<>() {
      @Override
      public void onSuccess(AndroidApplicationInfo result) {
        if (myArchiveDisposed) {
          return;
        }
        setAppInfo(result);
      }
    }, EdtExecutorService.getInstance());

    // obtain and set the download size
    mySizeAsyncIcon.setVisible(true);
    mySizeComponent.append("Estimating download size..");
    Futures.addCallback(Futures.successfulAsList(uncompressedApkSize, compressedFullApkSize),
                        new FutureCallBackAdapter<>() {
                          @Override
                          public void onSuccess(List<Long> result) {
                            if (myArchiveDisposed) {
                              return;
                            }
                            if (result != null) {
                              Long uncompressed = result.get(0);
                              Long compressed = result.get(1);
                              // successfulAsList() returns null items for failed futures.
                              setApkSizes(uncompressed == null ? 0 : uncompressed, compressed == null ? 0 : compressed);
                            }
                          }
                        }, EdtExecutorService.getInstance());

    Futures.FutureCombiner<Object> combiner = Futures.whenAllComplete(uncompressedApkSize, compressedFullApkSize, applicationInfo);
    combiner.call(() -> {
        String applicationId = applicationInfo.get().packageId;
      ApkAnalyzerStats.Builder stats = ApkAnalyzerStats.newBuilder()
          .setCompressedSize(compressedFullApkSize.get())
          .setUncompressedSize(uncompressedApkSize.get());
      if (align16kbCompliance.get() != ApkParser.Align16kbCompliance.NO_ELF_FILES) {
        stats.setAlign16Type(align16kbCompliance.get() == ApkParser.Align16kbCompliance.COMPLIANT
                             ? ALIGN_NATIVE_COMPLIANT_APK_ANALYZED
                             : ALIGN_NATIVE_NON_COMPLIANT_APK_ANALYZED);
      }
        UsageTracker.log(AndroidStudioEvent.newBuilder()
                           .setKind(AndroidStudioEvent.EventKind.APK_ANALYZER_STATS)
                           .setProjectId(AnonymizerUtil.anonymizeUtf8(applicationId))
                           .setRawProjectId(applicationId)
                         .setApkAnalyzerStats(stats));
        return null;
      }, MoreExecutors.directExecutor())
      .addListener(() -> {
      }, MoreExecutors.directExecutor());
    myContainer.setName("ApkViewPanel");
  }

  private void setToZipMode(@NotNull String fileName) {
    // Reset UI elements for .zip files (instead of .apk).
    myCompareWithButton.setEnabled(false);
    myCompareWithButton.setVisible(false);
    myNameAsyncIcon.setVisible(false);
    myNameComponent.clear();
    myNameComponent.append(fileName);
  }

  private void createUIComponents() {
    myNameAsyncIcon = new AsyncProcessIcon("aapt xmltree manifest");
    mySizeAsyncIcon = new AsyncProcessIcon("estimating apk size");

    myTreeModel = new ApkTreeModel(new LoadingNode());
    myTree = new Tree(myTreeModel);
    myTree.setName("nodeTree");
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(true); // show root node only when showing LoadingNode
    myTree.setPaintBusy(true);

    TreeSpeedSearch treeSpeedSearch = TreeSpeedSearch.installOn(myTree, true, path -> {
      Object lastPathComponent = path.getLastPathComponent();
      if (!(lastPathComponent instanceof ArchiveTreeNode)) {
        return null;
      }
      return ((ArchiveTreeNode)lastPathComponent).getData().getPath().toString();
    });

    // Provides the percentage of the node size to the total size of the APK
    PercentRenderer.PercentProvider percentProvider = (jTree, value, row) -> {
      if (!(value instanceof ArchiveTreeNode entry)) {
        return 0;
      }

      ArchiveTreeNode rootEntry = (ArchiveTreeNode)jTree.getModel().getRoot();

      if (entry.getData().getDownloadFileSize() < 0) {
        return 0;
      }
      else {
        return (double)entry.getData().getDownloadFileSize() / rootEntry.getData().getDownloadFileSize();
      }
    };
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("File")
                   .setPreferredWidth(JBUI.scale(270))
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new NameRenderer(myApkParser, treeSpeedSearch)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Size")
                   .setPreferredWidth(JBUI.scale(80))
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new SizeRenderer(false)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Download Size")
                   .setPreferredWidth(JBUI.scale(80))
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new SizeRenderer(true)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                    .setName("% of Download Size")
                    .setPreferredWidth(JBUI.scale(150))
                    .setHeaderAlignment(SwingConstants.LEADING)
                    .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                    .setRenderer(new PercentRenderer(percentProvider)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                    .setName("Compressed")
                    .setPreferredWidth(JBUI.scale(110))
                    .setHeaderAlignment(SwingConstants.LEADING)
                    .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                    .setRenderer(new CompressionRenderer()));

      if (IS_PAGE_ALIGN_ENABLED) {
        builder
          .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                     .setName("Alignment")
                     .setPreferredWidth(JBUI.scale(320))
                     .setHeaderAlignment(SwingConstants.LEADING)
                     .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                     .setRenderer(new AlignmentCellRenderer()));
      } else {
        builder
          .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                       .setName("Alignment")
                       .setPreferredWidth(JBUI.scale(200))
                       .setHeaderAlignment(SwingConstants.LEADING)
                       .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                       .setRenderer(new ZipAlignmentRenderer()));
      }

    myColumnTreePane = builder.build();
    myTree.addTreeSelectionListener(this);
  }

  public void setListener(@NotNull Listener listener) {
    myListener = listener;
  }

  public void clearArchive() {
    myArchiveDisposed = true;
    myApkParser.cancelAll();
    // The only other place that sets the root node is another queued runnable also on the EDT thread.
    // Therefore, there will be no interleaving of operations.
    ApplicationManager.getApplication().invokeLater(() -> setRootNode(null));
    Logger.getInstance(ApkViewPanel.class).info("Cleared Archive on ApkViewPanel: " + this);
  }

  private void setRootNode(@Nullable ArchiveNode root) {
    try {
      myTreeModel = new ApkTreeModel(root);
      if (root != null) {
        myTree.setPaintBusy(root.getData().getDownloadFileSize() < 0);
      }
      myTree.setModel(myTreeModel);
    }
    catch (Exception e) {
      // Ignore exceptions if the archive was disposed (b/351919218)
      if (!myArchiveDisposed) {
        throw e;
      }
    }
  }

  private void refreshTree() {
    myTree.setPaintBusy(false);
    myTree.removeTreeSelectionListener(this);
    TreePath[] selected = myTree.getSelectionPaths();
    myTreeModel.reload();
    myTree.setSelectionPaths(selected);
    myTree.addTreeSelectionListener(this);
  }

  private void setApkSizes(long uncompressed, long compressedFullApk) {
    mySizeComponent.clear();

    if (mySizeAsyncIcon != null) {
      mySizeAsyncIcon.setVisible(false);
      Disposer.dispose(mySizeAsyncIcon);
      mySizeAsyncIcon = null;
    }

    mySizeComponent.setIcon(AllIcons.General.BalloonInformation);
    if (myApkParser.getArchive() instanceof ApkArchive) {
      mySizeComponent.append("APK size: ");
      mySizeComponent.append(HumanReadableUtil.getHumanizedSize(uncompressed), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      mySizeComponent.append(", Download Size: ");
      mySizeComponent.setToolTipText(
        """
          1. The <b>APK size</b> reflects the actual size of the file, and is the minimum amount of space it will consume on the disk after \
          installation.
          2. The <b>download size</b> is the estimated size of the file for new installations (Google Play serves a highly compressed \
          version of the file).
          For application updates, Google Play serves patches that are typically much smaller.
          The installation size may be higher than the APK size depending on various other factors.""");
    }
    else if (myApkParser.getArchive() instanceof InstantAppBundleArchive) {
      mySizeComponent.append("Zip file size: ");
      mySizeComponent.setToolTipText("The <b>zip file size</b> reflects the actual size of the zip file on disk.\n");
    }
    else {
      mySizeComponent.append("Raw File Size: ");
    }
    mySizeComponent.append(HumanReadableUtil.getHumanizedSize(compressedFullApk), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  private void setAppInfo(@NotNull AndroidApplicationInfo appInfo) {
    myNameComponent.clear();

    if (myNameAsyncIcon != null) {
      myNameAsyncIcon.setVisible(false);
      Disposer.dispose(myNameAsyncIcon);
      myNameAsyncIcon = null;
    }

    myNameComponent.append(appInfo.packageId, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    myNameComponent.append(" (Version Name: ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    myNameComponent.append(appInfo.versionName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myNameComponent.append(", Version Code: ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    myNameComponent.append(String.valueOf(appInfo.versionCode), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myNameComponent.append(")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    myTreeModel.setExtractNativeLibs(appInfo.extractNativeLibs);
  }

  @NotNull
  public ApkTreeModel getTreeModel() { return myTreeModel; }

  @NotNull
  public JComponent getContainer() {
    return myContainer;
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @Override
  public void valueChanged(TreeSelectionEvent e) {
    if (myListener != null) {
      TreePath[] paths = ((Tree)e.getSource()).getSelectionPaths();
      ArchiveTreeNode[] components;
      if (paths == null) {
        components = null;
      }
      else {
        components = new ArchiveTreeNode[paths.length];
        for (int i = 0; i < paths.length; i++) {
          if (!(paths[i].getLastPathComponent() instanceof ArchiveTreeNode)) {
            myListener.selectionChanged(null);
            return;
          }
          components[i] = (ArchiveTreeNode)paths[i].getLastPathComponent();
        }
      }
      myListener.selectionChanged(components);
    }
  }

  public static class ApkTreeModel extends DefaultTreeModel {
    private Boolean myExtractNativeLibs = null;
    private boolean myExtractNativeLibsSet = false;

    public ApkTreeModel(TreeNode root) {
      super(root);
    }

    public void setExtractNativeLibs(Boolean extractNativeLibs) {
      myExtractNativeLibs = extractNativeLibs;
      myExtractNativeLibsSet = true;
    }

    public Boolean getExtractNativeLibs() {
      // If myExtractNativeLibs hasn't been set yet then the APK is still loading and we don't
      // yet know if it will be true or false.
      // In this case, return 'true' so that we don't prematurely show page alignment warnings.
      if (!myExtractNativeLibsSet) return true;
      return myExtractNativeLibs;
    }
  }

  public static class FutureCallBackAdapter<V> implements FutureCallback<V> {
    @Override
    public void onSuccess(V result) {
    }

    @Override
    public void onFailure(@NotNull Throwable t) {
    }
  }

  public static class NameRenderer extends ColoredTreeCellRenderer {
    private final ApkParser myApkParser;
    private final TreeSpeedSearch mySpeedSearch;

    public NameRenderer(@NotNull ApkParser apkParser, @NotNull TreeSpeedSearch speedSearch) {
      myApkParser = apkParser;
      mySpeedSearch = speedSearch;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      try {
        if (!(value instanceof ArchiveNode)) {
          append(value.toString());
          return;
        }

        ArchiveEntry entry = ((ArchiveNode)value).getData();
        setIcon(getIconFor(entry));
        String name = entry.getNodeDisplayString();
        SimpleTextAttributes attr = entry instanceof ArchiveErrorEntry ? SimpleTextAttributes.ERROR_ATTRIBUTES
                                                                       : SimpleTextAttributes.REGULAR_ATTRIBUTES;
        SearchUtil.appendFragments(mySpeedSearch.getEnteredPrefix(), name, attr.getStyle(), attr.getFgColor(),
                                   attr.getBgColor(), this);
      }
      catch (Exception e) {
        // Ignore exceptions if the file doesn't exist (b/351919218)
        if (Files.exists(myApkParser.getArchive().getPath())) {
          throw e;
        }
      }
    }

    @NotNull
    private static Icon getIconFor(@NotNull ArchiveEntry entry) {
      if (entry instanceof ArchiveErrorEntry) {
        return StudioIcons.Common.WARNING;
      }
      Path path = entry.getPath();
      Path base = path.getFileName();
      String fileName = base == null ? "" : base.toString();

      if (!Files.isDirectory(path)) {
        if (fileName.equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
          return StudioIcons.Shell.Filetree.MANIFEST_FILE;
        }
        else if (fileName.endsWith(SdkConstants.DOT_DEX)) {
          return AllIcons.FileTypes.JavaClass;
        }
        else if (fileName.equals("baseline.prof") || fileName.equals("baseline.profm")) {
          // TODO: Use dedicated icon for this.
          return AllIcons.FileTypes.Hprof;
        } else if (entry.getIsElf()) {
          return AllIcons.FileTypes.BinaryData;
        }

        FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
        Icon ftIcon = fileType.getIcon();
        return ftIcon == null ? AllIcons.FileTypes.Any_type : ftIcon;
      }
      else {
        fileName = StringUtil.trimEnd(fileName, "/");
        if (fileName.equals(SdkConstants.FD_RES)) {
          return AllIcons.Modules.ResourcesRoot;
        } else if (path.toString().equals("/lib")) {
          return AllIcons.Nodes.NativeLibrariesFolder;
        }
        //noinspection UnstableApiUsage
        return IconManager.getInstance().getPlatformIcon(PlatformIcons.Package);
      }
    }
  }

  private static class SizeRenderer extends ColoredTreeCellRenderer {
    private final boolean myUseDownloadSize;

    public SizeRenderer(boolean useDownloadSize) {
      myUseDownloadSize = useDownloadSize;
      setTextAlign(SwingConstants.RIGHT);
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (!(value instanceof ArchiveTreeNode)) {
        return;
      }

      ArchiveEntry data = ((ArchiveTreeNode)value).getData();
      long size = myUseDownloadSize ? data.getDownloadFileSize() : data.getRawFileSize();
      if (size > 0) {
        append(HumanReadableUtil.getHumanizedSize(size));
      }
    }
  }

  /**
   * Render information about whether the .so file is aligned at a boundary (4 KB, 16 KB, etc) within the APK.
   */
  private static class ZipAlignmentRenderer extends ColoredTreeCellRenderer {
    ZipAlignmentRenderer() {
      setTextAlign(SwingConstants.LEFT);
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (!(value instanceof ArchiveTreeNode)) {
        return;
      }

      ArchiveEntry data = ((ArchiveTreeNode)value).getData();
      append(data.getFileAlignment().text);
    }
  }

  private static class CompressionRenderer extends ColoredTreeCellRenderer {
    public CompressionRenderer() {
      setTextAlign(SwingConstants.LEFT);
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (!(value instanceof ArchiveTreeNode)) {
        return;
      }

      ArchiveEntry data = ((ArchiveTreeNode)value).getData();
      try {
        if (!Files.isDirectory(data.getPath())) {
          // For page alignment, use "Yes" and "No" to give more horizontal space for the alignment message.
          if (data.isFileCompressed()) {
            append("Yes");
          }
          else {
            append("No");
          }
        }
      } catch (ClosedFileSystemException e) {
        // When the APK tab is closed, the APK file gets closed in another thread but the
        // UI still tries to render it (b/402589243).
      }
    }
  }
}

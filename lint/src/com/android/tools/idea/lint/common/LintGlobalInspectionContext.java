/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import static com.android.tools.idea.lint.common.AndroidLintInspectionBase.LINT_INSPECTION_PREFIX;
import static com.android.tools.lint.detector.api.Lint.describeCounts;

import com.android.tools.idea.lint.common.LintExternalAnnotator.Companion.InspectionProfileIssues;
import com.android.tools.lint.client.api.JarFileIssueRegistry;
import com.android.tools.lint.client.api.LintBaseline;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LintGlobalInspectionContext implements GlobalInspectionContextExtension<LintGlobalInspectionContext> {
  public static final Key<LintGlobalInspectionContext> ID = Key.create("LintGlobalInspectionContext");
  private Map<Issue, Map<File, List<LintProblemData>>> myResults;
  private LintBaseline myBaseline;

  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.lint.common.LintGlobalInspectionContext");

  @NotNull
  @Override
  public Key<LintGlobalInspectionContext> getID() {
    return ID;
  }

  @Override
  public void performPreRunActivities(@NotNull List<Tools> globalTools,
                                      @NotNull List<Tools> localTools,
                                      @NotNull final GlobalInspectionContext context) {
    try {
      doAnalyze(globalTools, localTools, context);
    }
    catch (ProcessCanceledException | IndexNotReadyException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  @SuppressWarnings("PatternVariableCanBeUsed")
  private void doAnalyze(@NotNull List<Tools> globalTools,
                         @NotNull List<Tools> localTools,
                         @NotNull final GlobalInspectionContext context) {
    ThreadingAssertions.assertBackgroundThread();
    final Project project = context.getProject();
    LintIdeSupport ideSupport = LintIdeSupport.get();
    if (!ideSupport.canAnalyze(project)) {
      return;
    }

    // If none of the active inspections are Lint checks, then do not run Lint.
    // TODO: In theory, this is not ideal, as we don't discover third party Lint checks until Lint
    //  runs for the first time, and the user might be trying to only run third party checks (all
    //  built-in checks disabled). But in practice, this is very unlikely.
    if (globalTools.stream().noneMatch(it -> it.getShortName().startsWith(LINT_INSPECTION_PREFIX))) {
      return;
    }

    if (!(context instanceof GlobalInspectionContextBase)) {
      LOG.error("Reached doAnalyze with a context that is not a GlobalInspectionContextBase: " + context.getClass());
      return;
    }
    GlobalInspectionContextBase contextImpl = (GlobalInspectionContextBase)context;

    // A previous version of doAnalyze was (at various points, sometimes via other functions) using
    // the project's "current" inspection profile, but this is a bad idea, as this can be different
    // from the global inspection context's inspection profile. See b/380216450.
    InspectionProfileImpl profile = contextImpl.getCurrentProfile();

    Set<Issue> enabledIssues;
    Set<Issue> disabledIssues;
    {
      InspectionProfileIssues issuesFromProfile = LintExternalAnnotator.Companion.getIssuesFromInspectionProfile(profile);
      enabledIssues = issuesFromProfile.getEnabledIssues();
      disabledIssues = issuesFromProfile.getDisabledIssues();
    }

    if (enabledIssues.isEmpty()) {
      return;
    }

    long startTime = System.currentTimeMillis();

    final Map<Issue, Map<File, List<LintProblemData>>> problemMap = new HashMap<>();
    AnalysisScope scope = contextImpl.getCurrentScope();
    if (scope == null) {
      scope = context.getRefManager().getScope();
    }
    if (scope == null) {
      return;
    }

    if (globalTools.size() == 1 && enabledIssues.size() == 1) {
      // The user is probably running a single Lint inspection by name. Setting disabledIssues to
      // null indicates that we ONLY want the enabledIssues to be enabled (even if disabled by the
      // Gradle config) and we don't want all third party issues to be implicitly enabled.
      disabledIssues = null;
    }

    LintBatchResult lintResult = new LintBatchResult(project, problemMap, scope, enabledIssues, disabledIssues);
    final LintIdeClient client = ideSupport.createBatchClient(lintResult);

    EnumSet<Scope> lintScope;
    if (!LintIdeClient.SUPPORT_CLASS_FILES) {
      lintScope = EnumSet.copyOf(Scope.ALL);
      // Can't run class file based checks
      lintScope.remove(Scope.CLASS_FILE);
      lintScope.remove(Scope.ALL_CLASS_FILES);
      lintScope.remove(Scope.JAVA_LIBRARIES);
    }
    else {
      lintScope = Scope.ALL;
    }

    List<VirtualFile> files = null;
    final List<Module> modules = new ArrayList<>();

    int scopeType = scope.getScopeType();
    switch (scopeType) {
      case AnalysisScope.MODULE: {
        SearchScope searchScope = ReadAction.compute(scope::toSearchScope);
        if (searchScope instanceof ModuleWithDependenciesScope) {
          ModuleWithDependenciesScope s = (ModuleWithDependenciesScope)searchScope;
          if (!s.isSearchInLibraries()) {
            modules.add(s.getModule());
          }
        }
        break;
      }
      case AnalysisScope.FILE:
      case AnalysisScope.VIRTUAL_FILES:
      case AnalysisScope.UNCOMMITTED_FILES: {
        files = new ArrayList<>();
        SearchScope searchScope = ReadAction.compute(scope::toSearchScope);
        if (searchScope instanceof LocalSearchScope) {
          final LocalSearchScope localSearchScope = (LocalSearchScope)searchScope;
          final PsiElement[] elements = localSearchScope.getScope();
          final List<VirtualFile> finalFiles = files;

          ApplicationManager.getApplication().runReadAction(() -> {
            for (PsiElement element : elements) {
              if (element instanceof PsiFile) { // should be the case since scope type is FILE
                Module module = ModuleUtilCore.findModuleForPsiElement(element);
                if (module != null && !modules.contains(module)) {
                  modules.add(module);
                }
                VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
                if (virtualFile != null) {
                  if (!(virtualFile instanceof LightVirtualFile)) { // such as translations editor
                    finalFiles.add(virtualFile);
                  }
                }
              }
            }
          });
        }
        else {
          final List<VirtualFile> finalList = files;
          scope.accept(new PsiElementVisitor() {
            @Override
            public void visitFile(PsiFile file) {
              VirtualFile virtualFile = file.getVirtualFile();
              if (virtualFile != null) {
                finalList.add(virtualFile);
              }
            }
          });
        }
        if (files.isEmpty()) {
          files = null;
        }
        else {
          // Lint will compute it lazily based on actual files in the request
          lintScope = null;
        }
        break;
      }
      case AnalysisScope.PROJECT: {
        modules.addAll(Arrays.asList(ModuleManager.getInstance(project).getModules()));
        break;
      }
      case AnalysisScope.CUSTOM:
      case AnalysisScope.MODULES:
      case AnalysisScope.DIRECTORY: {
        // Handled by the getNarrowedComplementaryScope case below
        break;
      }

      case AnalysisScope.INVALID:
        break;
      default:
        LOG.warn("Unexpected inspection scope " + scope + ", " + scopeType);
    }

    if (modules.isEmpty()) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        AnalysisScope currentScope = scope;
        ReadAction.run(() -> {
          if (currentScope.containsModule(module)) {
            modules.add(module);
          }
        });
      }

      if (modules.isEmpty() && files != null) {
        for (VirtualFile file : files) {
          Module module = ModuleUtilCore.findModuleForFile(file, project);
          if (module != null && !modules.contains(module)) {
            modules.add(module);
          }
        }
      }

      if (modules.isEmpty()) {
        AnalysisScope scopeRef = scope; // Need effectively final reference to permit capture by lambda.
        AnalysisScope narrowed = ReadAction.compute(() -> scopeRef.getNarrowedComplementaryScope(project));
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          if (narrowed.containsModule(module)) {
            modules.add(module);
          }
        }
      }
    }

    LintRequest request = new LintIdeRequest(client, project, files, modules, files != null && files.size() == 1);
    request.setScope(lintScope);
    final LintDriver lint = client.createDriver(request);

    // Baseline analysis?
    myBaseline = null;
    Module severityModule = null;
    for (Module module : modules) {
      if (severityModule == null) {
        if (ideSupport.getSeverityOverrides(module) != null) {
          severityModule = module;
        }
      }
      File baselineFile = ideSupport.getBaselineFile(client, module);
      if (baselineFile != null && !AbstractBaselineInspection.ourSkipBaselineNextRun) {
        if (!baselineFile.isAbsolute()) {
          String path = module.getProject().getBasePath();
          if (path != null) {
            baselineFile = new File(FileUtil.toSystemDependentName(path), baselineFile.getPath());
          }
        }
        myBaseline = new LintBaseline(client, baselineFile);
        lint.setBaseline(myBaseline);
        if (!baselineFile.isFile()) {
          myBaseline.setWriteOnClose(true);
        }
        else if (AbstractBaselineInspection.ourUpdateBaselineNextRun) {
          myBaseline.setRemoveFixed(true);
          myBaseline.setWriteOnClose(true);
        }
        break;
      }
    }

    lint.analyze();

    // In analyze(), LintDriver's registry is updated to include all discovered third party issues.
    // We can now access these issues. Below, we make sure they are registered.
    List<Issue> thirdPartyIssues = lint.getRegistry().getIssues().stream().filter(issue -> issue.getRegistry() instanceof JarFileIssueRegistry).toList();

    // Ensure all third party issues are registered in the global inspection context's profile.
    AndroidLintInspectionBase.ensureInspectionsRegistered(project, thirdPartyIssues, profile);

    // Some or all third party issues may still not have a corresponding tool in globalTools
    // (probably just the issues that were newly registered above, but to be safe, we check
    // regardless, otherwise there could be missing results). We add those that are missing,
    // otherwise the inspection results won't include these issues until the next time "inspect
    // code" is run with the same inspection profile.

    // Note: Only add tools that come from (are registered with) the global inspection context's
    // profile, otherwise there will be exceptions when viewing the results (b/380216450).

    // Third party inspection names that we might need to add.
    Set<String> thirdPartyInspectionsToAdd = new LinkedHashSet<>(thirdPartyIssues.size());
    for (Issue issue : thirdPartyIssues) {
      String inspectionShortName = LINT_INSPECTION_PREFIX + issue.getId();
      thirdPartyInspectionsToAdd.add(inspectionShortName);
    }

    // Remove those that are already present in globalTools.
    for (Tools tool : globalTools) {
      thirdPartyInspectionsToAdd.remove(tool.getShortName());
    }

    // Add the missing tools.
    for (String inspectionShortName : thirdPartyInspectionsToAdd) {
      ToolsImpl tool = profile.getToolsOrNull(inspectionShortName, project);
      if (tool == null) continue;
      globalTools.add(tool);
    }

    AbstractBaselineInspection.clearNextRunState();
    lint.setAnalysisStartTime(startTime);
    ideSupport.logSession(lint, severityModule, lintResult);
    myResults = problemMap;
  }

  @Nullable
  public Map<Issue, Map<File, List<LintProblemData>>> getResults() {
    return myResults;
  }

  @Override
  public void performPostRunActivities(@NotNull List<InspectionToolWrapper<?, ?>> inspections, @NotNull final GlobalInspectionContext context) {
    if (myBaseline != null) {
      // Close the baseline; we need to hold a read lock such that line numbers can be computed from PSI file contents
      if (myBaseline.getWriteOnClose()) {
        ApplicationManager.getApplication().runReadAction(() -> myBaseline.close());
      }

      // If we wrote a baseline file, post a notification
      if (myBaseline.getWriteOnClose()) {
        String message;
        if (myBaseline.getRemoveFixed()) {
          message = String
            .format(Locale.US, "Updated baseline file %1$s<br>Removed %2$d issues<br>%3$s remaining", myBaseline.getFile().getName(),
                    myBaseline.getFixedCount(),
                    describeCounts(myBaseline.getFoundErrorCount(),
                                   myBaseline.getFoundWarningCount(),
                                   myBaseline.getFoundHintCount(),
                                   false,
                                   true,
                                   false));
        }
        else {
          message = String
            .format(Locale.US, "Created baseline file %1$s<br>%2$d issues will be filtered out", myBaseline.getFile().getName(),
                    myBaseline.getTotalCount());
        }

        NotificationGroupManager.getInstance().getNotificationGroup("Wrote Baseline")
          .createNotification(message, NotificationType.INFORMATION)
          .notify(context.getProject());
      }
    }
  }

  @Override
  public void cleanup() {
    myResults = null;
    myBaseline = null;
  }
}

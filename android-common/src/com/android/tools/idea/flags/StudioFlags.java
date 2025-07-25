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
package com.android.tools.idea.flags;

import static com.android.tools.idea.IdeChannel.Channel.CANARY;
import static com.android.tools.idea.IdeChannel.Channel.DEV;
import static com.intellij.util.PlatformUtils.getPlatformPrefix;
import static com.android.tools.idea.IdeChannel.Channel.NIGHTLY;
import static com.android.tools.idea.IdeChannel.Channel.STABLE;
import static com.android.tools.idea.flags.ChannelDefault.enabledUpTo;
import static com.intellij.util.PlatformUtils.getPlatformPrefix;

import com.android.flags.BooleanFlag;
import com.android.flags.EnumFlag;
import com.android.flags.Flag;
import com.android.flags.FlagGroup;
import com.android.flags.FlagOverrides;
import com.android.flags.Flags;
import com.android.flags.IntFlag;
import com.android.flags.LongFlag;
import com.android.flags.StringFlag;
import com.android.flags.overrides.DefaultFlagOverrides;
import com.android.flags.overrides.PropertyOverrides;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode;
import com.android.tools.idea.flags.overrides.MendelOverrides;
import com.android.tools.idea.flags.overrides.ServerFlagOverrides;
import com.android.tools.idea.util.StudioPathManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.Cancellation;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A collection of all feature flags used by Android Studio. These flags can be used to gate
 * features entirely or branch internal logic of features, e.g. for experimentation or easy
 * rollback.
 * <p>
 * For information on how to add your own flags, see the README.md file under
 * "//tools/base/flags".
 */
public final class StudioFlags {
  private static final Flags FLAGS = createFlags();

  @NotNull
  private static Flags createFlags() {
    FlagOverrides userOverrides;
    if (isUnitTestMode()) {
      userOverrides = new DefaultFlagOverrides();
    }
    else {
      userOverrides = new LazyStudioFlagSettings();
    }
    return new Flags(userOverrides, new PropertyOverrides(), new MendelOverrides(), new ServerFlagOverrides());
  }

  // This class is a workaround for b/355292387: IntelliJ 2024.2 does not allow services to be instantiated inside static initializers.
  private static class LazyStudioFlagSettings implements FlagOverrides {
    @Override
    public void clear() {
      StudioFlagSettings.getInstance().clear();
    }

    @Override
    public void put(@NotNull Flag<?> flag, @NotNull String value) {
      StudioFlagSettings.getInstance().put(flag, value);
    }

    @Override
    public void remove(@NotNull Flag<?> flag) {
      StudioFlagSettings.getInstance().remove(flag);
    }

    @Nullable
    @Override
    public String get(@NotNull Flag<?> flag) {
      return StudioFlagSettings.getInstance().get(flag);
    }
  }

  @TestOnly
  public static void validate() {
    FLAGS.validate();
  }

  //region New Project Wizard
  private static final FlagGroup NPW = new FlagGroup(FLAGS, "npw", "New Project Wizard");

  public static final Flag<Boolean> NPW_FIRST_RUN_SHOW = new BooleanFlag(
    NPW, "first.run.wizard.show", "Show Welcome Wizard always",
    "Show the Welcome Wizard when Studio starts",
    false);

  public static final Flag<Boolean> NPW_OFFLINE = new BooleanFlag(
    NPW, "first.run.offline", "Start Welcome Wizard Offline",
    "Start the welcome wizard without internet connection",
    false);

  public static final Flag<Boolean> NPW_ACCEPT_ALL_LICENSES = new BooleanFlag(
    NPW, "first.run.accept.sdk.license", "Auto Accepts SDK license",
    "Auto Accepts SDK license for testing",
    false);

  public static final Flag<String> NPW_CUSTOM_LOCAL_APP_DATA = new StringFlag(
    NPW, "first.run.local.app.data", "Set custom local app data",
    "Sets custom location for sdk install directory",
    "");

  public static final Flag<Boolean> NPW_SHOW_FRAGMENT_GALLERY = new BooleanFlag(
    NPW, "show.fragment.gallery", "Show fragment gallery",
    "Show fragment gallery which contains fragment based templates",
    true);

  public static final Flag<Boolean> NPW_SHOW_KTS_GRADLE_COMBO_BOX = new BooleanFlag(
    NPW, "show.kts.gradle.combobox", "Show KTS/Gradle Combobox",
    "Show KTS/Gradle Combobox to which build script is used for the generated code",
    true);

  public static final Flag<Boolean> NPW_PICK_LATEST_PATCH_AGP = new BooleanFlag(
    NPW, "use.patch.releases", "Use the latest patch release of AGP",
    "When enabled Studio will pick future patch releases of AGP for new projects.",
    false);

  public static final Flag<Boolean> NPW_SHOW_AGP_VERSION_COMBO_BOX = new BooleanFlag(
    NPW, "show.agp.version.combobox", "Show AGP version combobox",
    "Show a combobox to select the version of Android Gradle plugin used for the new project",
    IdeaIsInternalDefault.INSTANCE);

  public static final Flag<Boolean> NPW_SHOW_AGP_VERSION_COMBO_BOX_EXPERIMENTAL_SETTING = new BooleanFlag(
    NPW, "show.agp.version.combobox.experimental.option", "Show experimental setting allowing enabling AGP version combobox",
    "Show a checkbox in experimental settings, which when enabled shows a combobox to select the version of Android Gradle plugin used for the new project",
    enabledUpTo(DEV));

  public static final Flag<Boolean> NPW_INCLUDE_ALL_COMPATIBLE_ANDROID_GRADLE_PLUGIN_VERSIONS = new BooleanFlag(
    NPW, "show.agp.version.combobox.all.versions", "List all previous versions of AGP",
    "List all versions of AGP in the new project wizard combo box. " +
    "When disabled the combo box will only the two newest stable major-minor series of AGP versions.",
    enabledUpTo(DEV));

  public static final Flag<Boolean> NPW_NEW_NATIVE_MODULE = new BooleanFlag(
    NPW, "new.native.module", "New Android Native Module",
    "Show template to create a new Android Native module in the new module wizard.",
    true);

  public static final Flag<Boolean> NPW_NEW_MACRO_BENCHMARK_MODULE = new BooleanFlag(
    NPW, "new.macro.benchmark.module", "New Macro Benchmark Module",
    "Show template to create a new Macro Benchmark module in the new module wizard.",
    true);

  public static final Flag<Boolean> NPW_NEW_BASELINE_PROFILES_MODULE = new BooleanFlag(
    NPW, "new.baseline.profiles.module", "New Baseline Profile Module",
    "Show template to create a new Baseline Profile module in the new module wizard.",
    true);
  public static final Flag<Boolean> NPW_ENABLE_GRADLE_VERSION_CATALOG = new BooleanFlag(
    NPW, "enable.version.catalog", "Enable Gradle Version Catalog",
    "Use Gradle Version Catalogs for dependencies added in the new project/module wizard. (when existing project already uses Version Catalogs for new modules)",
    true);

  public static final Flag<Boolean> NPW_ENABLE_GENAI_TEMPLATE = new BooleanFlag(
    NPW, "genai.template",
    "Enable GenAI template",
    "Allows the GenAI template to be used.",
    true);

  public static final Flag<Boolean> NPW_ENABLE_XR_TEMPLATE = new BooleanFlag(
    NPW, "xr.template",
    "Enable XR template",
    "Allows the XR template to be used.",
    true);

  public static final Flag<Boolean> NPW_ENABLE_NAVIGATION_UI_TEMPLATE = new BooleanFlag(
    NPW, "navigationui.template",
    "Enable Navigation UI template",
    "Allows the Navigation UI template to be used.",
    enabledUpTo(CANARY));

  public static final Flag<Boolean> NPW_NEW_KOTLIN_MULTIPLATFORM_MODULE = new BooleanFlag(
    NPW, "new.kotlin.multiplatform.module", "New Kotlin Multiplatform Module",
    "Show template to create a new Kotlin Multiplatform module in the new module wizard.",
    true);

  public static final Flag<Integer> NPW_COMPILE_SDK_VERSION = new IntFlag(
    NPW, "new.project.compile.sdk", "New project Compile SDK version",
    "SDK version to be used for compileSdk for newly created project.",
    36);
  //endregion

  //region Memory Usage Reporting
  private static final FlagGroup MEMORY_USAGE_REPORTING = new FlagGroup(FLAGS, "memory.usage.reporting", "Memory Usage Reporting");

  public static final Flag<Boolean> USE_DISPOSER_TREE_REFERENCES = new BooleanFlag(
    MEMORY_USAGE_REPORTING, "use.disposer.tree.references", "Memory report collection traversal will use disposer tree reference.",
    "If enabled, the memory report collecting traversal will consider disposer tree references as an object graph edges.",
    false);
  //endregion

  //region Transport
  private static final FlagGroup TRANSPORT = new FlagGroup(FLAGS, "transport", "Transport");

  public static final Flag<Boolean> TRANSPORT_CONSERVATIVE_COPY = new BooleanFlag(
    TRANSPORT, "conservative.copy", "Conservative copy",
    "Copy transport and agent files only if they have changed since the latest push to the device",
    true
  );
  //endregion

  //region Profiler
  private static final FlagGroup PROFILER = new FlagGroup(FLAGS, "profiler", "Android Profiler");

  public static final Flag<Boolean> PROFILER_ENERGY_PROFILER_ENABLED = new BooleanFlag(
    PROFILER, "energy", "Enable Energy profiling",
    "Enable the new energy profiler. It monitors battery usage of the selected app.", true);

  public static final Flag<Boolean> PROFILER_MEMORY_CSV_EXPORT = new BooleanFlag(
    PROFILER, "memory.csv", "Allow exporting entries in memory profiler",
    "Allow exporting entries in the views for heap dump and native/JVM recordings in CSV format.",
    false);

  public static final Flag<Boolean> PROFILER_PERFORMANCE_MONITORING = new BooleanFlag(
    PROFILER, "performance.monitoring", "Enable Profiler Performance Monitoring Options",
    "Toggles if profiler performance metrics options are enabled.",
    false
  );

  public static final Flag<Boolean> PROFILER_TESTING_MODE = new BooleanFlag(
    PROFILER, "testing.mode", "Enable the testing mode in Profiler",
    "Toggles the testing mode for more logging and Actions to facilitate automatic testing.",
    false
  );

  public static final Flag<Boolean> PROFILER_JANK_DETECTION_UI = new BooleanFlag(
    PROFILER, "jank.ui", "Enable jank detection UI",
    "Add a track in the display group showing frame janks.",
    true
  );

  public static final Flag<Boolean> PROFILER_CUSTOM_EVENT_VISUALIZATION = new BooleanFlag(
    PROFILER, "custom.event.visualization", "Enable Profiler Custom Event Visualization",
    "When enabled, profiler will track and display events defined through developer APIs",
    false);

  public static final Flag<PowerProfilerDisplayMode> PROFILER_SYSTEM_TRACE_POWER_PROFILER_DISPLAY_MODE = new EnumFlag<>(
    PROFILER, "power.tracks", "Set display mode of power rails and battery counters in system trace UI",
    "Allows users to customize whether the power rail and battery counter tracks are shown in the system trace UI, " +
    "and if shown, which type of graph displays the tracks. " +
    "When set to HIDE, hides power and battery data track groups in the system trace. " +
    "When set to CUMULATIVE, shows power rails and battery counters in their raw view (cumulative counters). " +
    "When set to DELTA, shows the power rails in a delta view and battery counters in their raw view (cumulative counters).",
    PowerProfilerDisplayMode.DELTA);

  // TODO(b/211154220): Pending user's feedback, either completely remove the keyboard event functionality in
  //                    Event Timeline or find a proper way to support it for Android S and newer.
  public static final Flag<Boolean> PROFILER_KEYBOARD_EVENT = new BooleanFlag(
    PROFILER, "keyboard.event", "Enable keyboard event",
    "Enable the keyboard event functionality in Event Timeline",
    false);

  public static final Flag<Boolean> PERFETTO_SDK_TRACING = new BooleanFlag(
    PROFILER, "perfetto.sdk.tracing", "Automatically instrument perfetto sdk builds",
    "A cpu trace intercept command is added that will enable perfetto instrumentation for apps" +
    " that use the perfetto SDK",
    true);

  public static final Flag<Boolean> COMPOSE_TRACING_NAVIGATE_TO_SOURCE = new BooleanFlag(
    PROFILER, "perfetto.sdk.tracing.compose.navigation", "Navigate-to-source action for Compose Tracing",
    "Enables navigate-to-source action in Profiler for Compose Tracing slices",
    true);

  public static final Flag<Boolean> PROFILER_TASK_BASED_UX = new BooleanFlag(PROFILER, "task.based.ux", "Task-based UX",
                                                                             "Enables a simpler profilers UX, with tabs for specific tasks which an app developer usually performs (e.g. Reduce jank)",
                                                                             true);

  public static final Flag<Boolean> PROFILER_TASK_TITLE_V2 = new BooleanFlag(PROFILER, "task.title.v2", "Task Title V2",
                                                                             "Enables more distinguishable descriptions for profiler tasks",
                                                                             enabledUpTo(CANARY));

  public static final Flag<Boolean> PROFILER_LEAKCANARY = new BooleanFlag(PROFILER, "leakcanary", "LeakCanary",
                                                                          "Enables the integration of leakCanary and display of leaks",
                                                                          false);

  public static final Flag<Boolean> PROFILER_TRACEBOX =
    new BooleanFlag(PROFILER, "tracebox", "Tracebox", "Tracebox for versions M,N,O,P of Android", false);
  //endregion

  //region Design Tools
  private static final FlagGroup DESIGN_TOOLS = new FlagGroup(FLAGS, "design.tools", "Design Tools");

  public static final Flag<Long> PROJECT_SYSTEM_CLASS_LOADER_CACHE_LIMIT = new LongFlag(
    DESIGN_TOOLS,
    "project.system.class.loader.cache.max.size",
    "Configure the max size of the cache used by ProjectSystemClassLoader",
    "Allow configuring the maximum size (in bytes) of the cache used by the ProjectSystemClassLoader to load classes from JAR files. " +
    "Files larger than the cache limit will cause a file miss and the file will need to be read again.",
    20_000_000L
  );

  public static final Flag<Long> GRADLE_CLASS_FINDER_CACHE_LIMIT = new LongFlag(
    DESIGN_TOOLS,
    "gradle.class.finder.cache.max.size",
    "Configure the max size of the cache used by GradleClassFileFinder",
    "Allow configuring the maximum number of file references to be kept.",
    150L
  );
  //endregion

  //region Layout Editor
  private static final FlagGroup NELE = new FlagGroup(FLAGS, "nele", "Layout Editor");

  public static final Flag<Boolean> NELE_RENDER_DIAGNOSTICS = new BooleanFlag(
    NELE, "diagnostics", "Enable rendering on-screen stats",
    "If enabled, the surface displays some debug information to diagnose performance",
    false);

  public static final Flag<Boolean> NELE_LOG_ANDROID_FRAMEWORK = new BooleanFlag(
    NELE, "log.android.framework", "Log messages coming from Layoutlib Native.",
    "Log in the IDEA log the messages coming from Java and native code of Layoutlib Native.",
    false);

  public static final Flag<Boolean> NELE_CLASS_PRELOADING_DIAGNOSTICS = new BooleanFlag(
    NELE, "preview.class.preloading.diagnostics", "Enable class preloading overlay",
    "If enabled, the surface displays background class preloading progress",
    false);

  public static final Flag<Boolean> NELE_XML_TO_COMPOSE = new BooleanFlag(
    NELE, "xml.to.compose", "Enable XML to Compose conversion",
    "Enable an action that converts XML layouts to Compose using the Gemini backend",
    false);

  public static final Flag<Boolean> PREVIEW_ZOOM_ANIMATION = new BooleanFlag(
    NELE, "preview.zoom.animation", "Enable animation while zooming",
    "If enabled, Zoom change will show up an animation.",
    false);

  public static final Flag<Boolean> DETACHABLE_ATTACHED_TOOLWINDOWS = new BooleanFlag(
    NELE, "detached.attached.toolwindows", "Allow floating attached tool windows",
    "Allows floating attached tool windows (partly broken).",
    false);

  public static final Flag<Boolean> NELE_BACKGROUND_DISPLAY_LIST = new BooleanFlag(
    NELE, "background.displaylist", "Enable Display List background creation",
    "When enabled, the scene display list is created in the background.",
    enabledUpTo(CANARY));

  public static final Flag<Boolean> FORCE_MONOCHROME_ADAPTIVE_ICON = new BooleanFlag(
    NELE, "force.monochrome.adaptive.icon", "Display monochrome preview of adaptive icon when none provided",
    "When enabled, the adaptive icon preview will automatically create a monochrome version if none is provided.",
    true);

  public static final Flag<Boolean> USE_BYTECODE_R_CLASS_PARSING = new BooleanFlag(
    NELE, "use.bytecode.r.class.loading", "Uses bytecode R class parsing instead of reflection",
    "When enabled, the parsing of R classes will use bytecode parsing instead of reflection.",
    true);

  public static final Flag<Boolean> LAYOUTLIB_NATIVE_MEMORY_CLEAN = new BooleanFlag(
    NELE, "layoutlib.native.memory.clean", "Enable cleaning of Layoutlib native memory.",
    "When it is detected that Layoutlib uses too much native memory, attempts are made to clear it.",
    true);
  //endregion

  //region Resource Repository
  private static final FlagGroup RESOURCE_REPOSITORY = new FlagGroup(FLAGS, "resource.repository", "Resource Repository");
  public static final Flag<Integer> RESOURCE_REPOSITORY_TRACE_SIZE = new IntFlag(
    RESOURCE_REPOSITORY, "trace.size", "Maximum Size of Resource Repository Update Trace",
    "Size of the in-memory cyclic buffer used for tracing of resource repository updates",
    10000);
  //endregion

  //region Run/Debug
  private static final FlagGroup RUNDEBUG = new FlagGroup(FLAGS, "rundebug", "Run/Debug");
  public static final Flag<Boolean> RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED = new BooleanFlag(
    RUNDEBUG, "console.output.enabled", "Show logcat process output in Run/Debug console window",
    "When running or debugging an Android process, output the logcat output of the process in the console window.",
    false);

  public static final Flag<Boolean> GENERATE_BASELINE_PROFILE_GUTTER_ICON = new BooleanFlag(
    RUNDEBUG,
    "baselineprofile.guttericon.enabled",
    "Enables generating baseline profiles from gutter icon",
    "When opening a UI test with applied BaselineProfileRule, an option to generate baseline profiles is shown in the gutter popup.",
    true);

  public static final Flag<Boolean> DELTA_INSTALL = new BooleanFlag(
    RUNDEBUG,
    "deltainstall",
    "Delta install",
    "Upon installing, if application is already on device, only send parts of the apks which have changed (the delta).",
    true);

  public static final Flag<Integer> DELTA_INSTALL_CUSTOM_MAX_PATCH_SIZE = new IntFlag(
    RUNDEBUG,
    "deltainstall.custom.max.patch.size",
    "Delta install Max Patch Size",
    "The upper limit of number of bytes a delta install patch set can be before bailing out to a full install.",
    -1); // Negative to use the PatchSetGenerator's default value of 40MB.

  public static final Flag<Boolean> INSTALL_WITH_ADBLIB = new BooleanFlag(
    RUNDEBUG,
    "installwithadblib",
    "Install apks with adblib instead of ddmlib",
    "Studio can communicate with adb server via two backend, ddmlib and adblib. This option decides which backend to use.",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_OPTIMISTIC_SWAP = new BooleanFlag(
    RUNDEBUG,
    "applychanges.optimisticswap",
    "Use the 'Apply Changes 2.0' deployment pipeline",
    "Supports Install-without-Install, Speculative Diff and Structural Redefinition",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_OPTIMISTIC_RESOURCE_SWAP = new BooleanFlag(
    RUNDEBUG,
    "applychanges.optimisticresourceswap",
    "Use the 'Apply Changes 2.0' deployment pipeline for full Apply Changes",
    "Requires applychanges.optimisticswap to be true.",
    true);

  public static final Flag<Boolean> INSTALL_USE_PM_TERMINATE = new BooleanFlag(
    RUNDEBUG,
    "install.use.pm.terminate",
    "When installing via the Package Manager, do not use the --dont-kill flag and skip process termination for API33+",
    "We assume there are no race conditions with the package manager and give full control to it.",
    false);

  public static final Flag<Boolean> SUPPORT_CUSTOM_ARTIFACTS = new BooleanFlag(
    RUNDEBUG,
    "support.custom.artifacts",
    "Support custom build artifacts in deployment.",
    "Enable support and UI element for Run configuration that deploys a custom artifact",
    false);

  // This should be used by AndroidX team. See b/388473186
  public static final Flag<Boolean> COMPOSE_CLASS_NAME_CALCULATOR_CANONICAL_FILE_CACHE = new BooleanFlag(
    RUNDEBUG,
    "compose.class.name.calculator.canonical.file.cache",
    "Enable canonical filename cache in ComposeClassNameCalculator.",
    "Turns on a canonical file cache for projects that have multiple versions of the same file as dependancies",
    false);

  /**
   * The level of APK change that will be supported by the deployment pipeline's optimistic
   * "deploy-without-installing" path. Deploying changes that exceed the level of support
   * configured here will cause the deployment to install via the package manager.
   */
  public enum OptimisticInstallSupportLevel {
    /**
     * Always fall back to a package manager installation.
     */
    DISABLED,
    /**
     * Support deploying changes to dex files only.
     */
    DEX,
    /**
     * Support deploying changes to dex files and native libraries only.
     */
    DEX_AND_NATIVE,
    /**
     * Support deploying changes to dex files, native libraries, and resources.
     */
    DEX_AND_NATIVE_AND_RESOURCES,
  }

  public static final Flag<OptimisticInstallSupportLevel> OPTIMISTIC_INSTALL_SUPPORT_LEVEL = new EnumFlag<>(
    RUNDEBUG,
    "optimisticinstall.supportlevel",
    "The amount of support for using the 'Apply Changes 2.0' pipeline on Run.",
    "This can be \"DISABLED\" to always use a package manager installation; \"DEX\" to use the pipeline for dex-only changes;" +
    " \"DEX_AND_NATIVE\" to use the pipeline for dex and native library-only changes;" +
    " or \"DEX_AND_NATIVE_AND_RESOURCES\" to use the pipeline for changes to dex, native libraries, and/or resource/asset files." +
    " Deploying changes that exceed the level of support configured here will cause the deployment to install via the package manager.",
    OptimisticInstallSupportLevel.DEX);

  public static final Flag<Boolean> INSTALL_WITH_ASSUME_VERIFIED = new BooleanFlag(
    RUNDEBUG,
    "install.with.assume.verified",
    "Enabled ART assume-verified compiler filter for API 35+ deployment.",
    "When deploying to API 35+ device for debuggable deployment, the deployment pipeline will leverage the assume-verified" +
    " compiler filter in ART to avoid bytecode verification when possible. This would speed up development cycles. Note that all release" +
    " build are still verified by ART regardless of this flag.",
    enabledUpTo(CANARY));


  public static final Flag<Boolean> INSTALL_WITH_ASSUME_VERIFIED_ON_DEFAULT = new BooleanFlag(
    RUNDEBUG,
    "install.with.assume.verified.on.default",
    "Turn on ART assume-verified compiler filter for API 35+ deployment by default in all run configurations",
    "When deploying to API 35+ device for debuggable deployment, the deployment pipeline will leverage the assume-verified" +
    " compiler filter in ART to avoid bytecode verification when possible. This would speed up development cycles. Note that all release" +
    " build are still verified by ART regardless of this flag. This flag turns on this feature for all run configurations.",
    false);

  public static final Flag<Boolean> APPLY_CHANGES_STRUCTURAL_DEFINITION = new BooleanFlag(
    RUNDEBUG,
    "applychanges.structuralredefinition",
    "Use ART's new structural redefinition extension for Apply Changes.",
    "Requires applychanges.optimisticswap to be true.",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_VARIABLE_REINITIALIZATION = new BooleanFlag(
    RUNDEBUG,
    "applychanges.variablereinitialization",
    "Use ART's new variable reinitializaiton extension for Apply Changes.",
    "Requires applychanges.structuralredefinition to be true.",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_KEEP_CONNECTION_ALIVE = new BooleanFlag(
    RUNDEBUG,
    "applychanges.connection.keepalive",
    "Keep connection to device alive.",
    "Eliminate the cost of opening a connection and spawning a process when using Apply Changes.",
    true);

  public static final Flag<Boolean> ADB_CONNECTION_STATUS_WIDGET_ENABLED = new BooleanFlag(
    RUNDEBUG,
    "adb.connection.status.widget.enabled",
    "Enable and Show ADB Connection Widget",
    "Enables and shows the ADB connection status widget in the status bar",
    false);

  public static final Flag<Boolean> ALERT_UPON_DEVICE_SUBOPTIMAL_SPEED = new BooleanFlag(
    RUNDEBUG,
    "device.connect.detect.speed",
    "Alert when USB device negotiated speed is below maximum",
    "Poor USB cables can drop USB negotiated speed below maximum capable speed. Alert user when this is the case.",
    true);

  public static final Flag<Boolean> DEVICE_EXPLORER_PROCESSES_PACKAGE_FILTER = new BooleanFlag(
    RUNDEBUG,
    "adb.device.explorer.package.filter.enable",
    "Enable package filtering for the \"Device Explorer\" tool window",
    "Enable package filtering for the \"Device Explorer\" tool window, which allows users to filter processes by app package ids.\n" +
    "Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_DDMLIB_CLIENT_MANAGER = new BooleanFlag(
    RUNDEBUG,
    "adblib.migration.ddmlib.clientmanager",
    "Use adblib to track device processes (Client)",
    "Use adblib instead of ddmlib to track processes (Client) on devices and handle debug sessions. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_DDMLIB_IDEVICE_MANAGER = new BooleanFlag(
    RUNDEBUG,
    "adblib.migration.ddmlib.idevicemanager",
    "Use adblib to track devices (IDevice)",
    "Use adblib instead of ddmlib to track and implement `IDevice` instances. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_DDMLIB_ADB_DELEGATE = new BooleanFlag(
    RUNDEBUG,
    "adblib.migration.ddmlib.androiddebugbridgedelegate",
    "Use adblib version of `AndroidDebugBridgeDelegate`",
    "Use adblib version of `AndroidDebugBridgeDelegate` in `AndroidDebugBridge` class. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    enabledUpTo(DEV));

  public static final Flag<Boolean> ADBLIB_MIGRATION_DDMLIB_ADB_DELEGATE_USAGE_TRACKER = new BooleanFlag(
    RUNDEBUG,
    "adblib.migration.ddmlib.androiddebugbridgedelegateusagetracker",
    "Track usage stats for `AndroidDebugBridgeDelegate`",
    "Track `AndroidDebugBridgeDelegate` method calls and success rates. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    enabledUpTo(CANARY));

  public static final Flag<Boolean> ADBLIB_MIGRATION_DDMLIB_IDEVICE_USAGE_TRACKER = new BooleanFlag(
    RUNDEBUG,
    "adblib.migration.ddmlib.ideviceusage.tracker",
    "Enable Android Studio usage stats for IDevice methods",
    "Track IDevice method calls and success rates. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    false);

  public static final Flag<Boolean> ADBLIB_USE_PROCESS_INVENTORY_SERVER = new BooleanFlag(
    RUNDEBUG,
    "adblib.use.process.inventory.server",
    "Use local tcp server for discovering JDWP processes",
    "Start and/or use a local TCP server for discovering and publishing JDWP processes. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    false);

  public static final Flag<Boolean> ADBLIB_USE_APP_INFO_IF_AVAILABLE = new BooleanFlag(
    RUNDEBUG,
    "adblib.use.app.info.if.available",
    "Use the `app_info` feature if available on the device for discovering processes",
    "Check the `app_info` feature for connected devices, and use it to track processes if available. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    false);

  public static final Flag<Boolean> JDWP_TRACER = new BooleanFlag(
    RUNDEBUG,
    "adb.jdwp.tracer.enabled",
    "Enable JDWP Traces",
    "Enables capture of JDWP traffic and generate a perfetto report",
    false);

  public static final Flag<Boolean> JDWP_SCACHE = new BooleanFlag(
    RUNDEBUG,
    "adb.jdwp.scache.enabled",
    "Enable JDWP SCache",
    "Enables JDWP Speculative Cache (SCache)",
    true);

  public static final Flag<Boolean> JDWP_SCACHE_REMOTE_ONLY = new BooleanFlag(
    RUNDEBUG,
    "adb.jdwp.scache.remote.only.enabled",
    "Enable JDWP SCache for remote devices only",
    "Enables JDWP Speculative Cache (SCache) for remote devices only",
    true);

  public static final Flag<Boolean> SUPPORT_FEATURE_ON_FEATURE_DEPS = new BooleanFlag(
    RUNDEBUG,
    "feature.on.feature",
    "Enable feature-on-feature dependencies",
    "Enables Studio to understand feature-on-feature dependencies when launching dynamic apps.",
    false
  );

  public static final Flag<Boolean> COROUTINE_DEBUGGER_ENABLE = new BooleanFlag(
    RUNDEBUG,
    "coroutine.debugger.enable",
    "Enable Coroutine Debugger",
    "Enables the Coroutine Debugger, that shows up as a panel in the debugger when debugging an app that uses coroutines",
    false
  );

  public static final Flag<Boolean> DDMLIB_ABB_EXEC_INSTALL_ENABLE = new BooleanFlag(
    RUNDEBUG,
    "ddmlib.abb.exec.install.enable",
    "Allow DDMLib to use ABB_EXEC on install when device supports it.",
    "Allow DDMLib to use ABB_EXEC on install instead of the 'legacy' EXEC/CMD or EXEC/PM combos. This only occurs if device and adb support abb_exec",
    true
  );

  public static final Flag<Boolean> DEBUG_ATTEMPT_SUSPENDED_START = new BooleanFlag(
    RUNDEBUG,
    "debug.app.suspend.upon.start.enable",
    "Start activity suspended when debugging.",
    "Start activity suspended when debugging. This reduce the amount of time 'Waiting for Debugger' panel is shown on device",
    true
  );

  public static final Flag<Boolean> ATTACH_ON_WAIT_FOR_DEBUGGER = new BooleanFlag(
    RUNDEBUG,
    "debug.app.auto.attach.wait.for.debugger",
    "Auto attach debugger on Debug.waitForDebugger().",
    "If the user has Debug.waitForDebugger() calls within the app code, this will allow the debugger to automatically attach to the app.",
    true
  );

  public static final Flag<Boolean> USE_BITMAP_POPUP_EVALUATOR_V2 = new BooleanFlag(
    RUNDEBUG,
    "use.bitmap.popup.evaluator.v2",
    "Use the new BitmapPopupEvaluatorV2",
    "BitmapPopupEvaluatorV2 uses Bitmap.getPixels() instead of Bitmap.copyPixelsToBuffer() which it makes platform independent",
    true
  );

  public static final Flag<Boolean> EMIT_CONSOLE_OUTPUT_TO_LOGCAT = new BooleanFlag(
    RUNDEBUG,
    "emit.console.output.to.logcat",
    "Emit console output to Logcat",
    "Emit console output, specifically breakpoint log expressions, to Logcat.",
    true
  );

  public static final Flag<Boolean> RISC_V = new BooleanFlag(
    RUNDEBUG,
    "riscv.support",
    "Support for RISC V",
    "Allow support for RISC V architecture and targeted architecture selection.",
    true
  );

  //endregion

  //region Logcat
  private static final FlagGroup LOGCAT = new FlagGroup(FLAGS, "logcat", "Logcat");

  public static final Flag<Boolean> LOGCAT_CLICK_TO_ADD_FILTER = new BooleanFlag(
    LOGCAT,
    "click.to.add.filter",
    "Enable Logcat click to add/remove filter feature",
    "Enable Logcat click to add/remove filter feature",
    true
  );

  public static final Flag<Boolean> LOGCAT_IS_FILTER = new BooleanFlag(
    LOGCAT,
    "is.filter",
    "Enable Logcat 'is:...' filter",
    "Enables a Logcat filter using the 'is' keyword for example 'is:stacktrace'is:crash' etc",
    true
  );

  public static final Flag<Integer> LOGCAT_MAX_MESSAGES_PER_BATCH = new IntFlag(
    LOGCAT,
    "max.messages.per.batch",
    "Set the max number of messages that are appended to the UI component",
    "Set the max number of messages that are appended to the UI component",
    1000
  );

  public static final Flag<Boolean> LOGCAT_PANEL_MEMORY_SAVER = new BooleanFlag(
    LOGCAT,
    "panel.memory.saver",
    "Enable Logcat Panel memory saving feature",
    "Reduces memory usage of Logcat tool by writing data to a file when the panel is not visible",
    false
  );

  public static final Flag<Boolean> LOGCAT_TERMINATE_APP_ACTIONS_ENABLED = new BooleanFlag(
    LOGCAT,
    "terminate.app.actions.enable",
    "Enable right-click actions for terminating the application",
    "Enable right-click actions for terminating the application. " +
    "Note that this feature is only enabled if the flag ADBLIB_MIGRATION_DDMLIB_CLIENT_MANAGER is also true. " +
    "Changing the value of this flag requires restarting Android Studio.",
    true
  );

  public static final Flag<Boolean> LOGCAT_IGNORE_STUDIO_TAGS = new BooleanFlag(
    LOGCAT,
    "ignore.studio.tags",
    "Ignore tags that Studio itself is responsible for",
    "Ignore tags that Studio itself is responsible for",
    true
  );

  public static final Flag<Boolean> LOGCAT_EXPORT_IMPORT_ENABLED = new BooleanFlag(
    LOGCAT,
    "export.import.enable",
    "Enable Export/Import feature",
    "Enable Export/Import feature",
    true
  );

  // Disabled due to b/351811546
  public static final Flag<Boolean> LOGCAT_PROTOBUF_ENABLED = new BooleanFlag(
    LOGCAT,
    "protobuf.enable",
    "Enable Logcat Protobuf format",
    "Enable Logcat Protobuf format",
    true
  );

  public static final Flag<Long> LOGCAT_FILE_RELOAD_DELAY_MS = new LongFlag(
    LOGCAT,
    "file.reload.delay.ms",
    "Delay before reloading Logcat from file after filter change",
    "Delay before reloading Logcat from file after filter change. If `<= 0`, file will not be reloaded",
    TimeUnit.SECONDS.toMillis(1)
  );

  public static final Flag<Boolean> LOGCAT_DEOBFUSCATE = new BooleanFlag(
    LOGCAT,
    "deobfuscate",
    "Enable stack trace deobfuscation using R8 Retrace",
    "Enable stack trace deobfuscation using R8 Retrace",
    true
  );
  //endregion

  //region Project System
  //region Gradle Project System
  private static final FlagGroup GRADLE_IDE = new FlagGroup(FLAGS, "gradle.ide", "Gradle Project System");

  public static final Flag<Boolean> ANDROID_SDK_AND_IDE_COMPATIBILITY_RULES = new BooleanFlag(
    GRADLE_IDE, "android.sdk.ide.compatibility.rules",
    "Enable compatibility rules support between IDE version and compile SDK version",
    "Enable compatibility rules support between IDE version and compile SDK version",
    true
  );

  public static final Flag<Boolean> API_OPTIMIZATION_ENABLE = new BooleanFlag(
    GRADLE_IDE, "build.injection.device.api.enabled",
    "Enable injection of device api level optimization from IDE",
    "Enable injection of device api level optimization from IDE",
    true
  );

  public static final Flag<Boolean> INJECT_DEVICE_SERIAL_ENABLED = new BooleanFlag(
    GRADLE_IDE, "internal.build.injection.device.serial.number",
    "For internal use only. Enables injection of device serial from the IDE into Gradle build.",
    "For internal use only. Enables injection of device serial from the IDE into Gradle build.",
    false
  );

  public static final Flag<Boolean> INCLUDE_ANDROIDX_DEV_ANDROID_GRADLE_PLUGIN_SNAPSHOTS = new BooleanFlag(
    GRADLE_IDE, "agp.snapshot.repo", "Enable AGP snapshot repository",
    "Also consults the androidx.dev snapshot repository for available versions of AGP.",
    enabledUpTo(DEV));

  public static final Flag<Boolean> USE_DEVELOPMENT_OFFLINE_REPOS = new BooleanFlag(
    GRADLE_IDE, "development.offline.repos", "Enable development offline repositories",
    "Uses the development offline repositories " +
    "(which can come from STUDIO_CUSTOM_REPO or from a local build of AGP when running studio from IDEA) " +
    "in the new project templates and for determining which versions of AGP are available for the upgrade assistant.\n" +
    "Note: repositories set in gradle.ide.development.offline.repo.location are always respected, even if this flag is disabled.",
    isAndroidStudio() && StudioPathManager.isRunningFromSources());

  public static final Flag<String> DEVELOPMENT_OFFLINE_REPO_LOCATION = new StringFlag(
    GRADLE_IDE, "development.offline.repo.location", "Development offline repository location",
    "Set a location for additional injected development maven repositories to use for projects.\n" +
    "Multiple repositories can be separated by the path separator char " + File.pathSeparator,
    ""
  );

  public static final Flag<Boolean> INJECT_EXTRA_GRADLE_REPOSITORIES_WITH_INIT_SCRIPT = new BooleanFlag(
    GRADLE_IDE, "inject.repos.with.init.script",
    "Inject repositories using a Gradle init script",
    "Also inject any development offline repos (if gradle.ide.development.offline.repos is set) " +
    "and the customised GMAVEN_TEST_BASE_URL if set using a Gradle init script at every build and sync invocation. " +
    "Note this this is disabled by default as it can break projects that would otherwise sync and build correctly with " +
    "published versions of AGP, including the relatively common case of projects that depend on AGP in buildSrc.",
    false);

  public static final Flag<Boolean> BUILD_ANALYZER_JETIFIER_ENABLED = new BooleanFlag(
    GRADLE_IDE, "build.analyzer.jetifier.warning", "Enable Jetifier usage analyzis",
    "Enable Jetifier usage analyzis is Build Analyzer.", true);
  public static final Flag<Boolean> BUILD_ANALYZER_DOWNLOADS_ANALYSIS = new BooleanFlag(
    GRADLE_IDE, "build.analyzer.downloads.analysis", "Enable Downloads analysis",
    "Enable Downloads analysis in Build Analyzer.", true);

  public static final Flag<Boolean> BUILD_ANALYZER_HISTORY = new BooleanFlag(
    GRADLE_IDE, "build.analyzer.history", "Enable access to historic build analysis",
    "Enable access to historic build analysis in Build Analyzer.", false);
  public static final Flag<Boolean> BUILD_ANALYZER_CATEGORY_ANALYSIS = new BooleanFlag(
    GRADLE_IDE, "build.analyzer.category.analysis", "Enable 'Group by Task Category' category task analysis",
    "Enable 'Group by Task Category' category task analysis in Build Analyzer.", true);

  /**
   * @see #isBuildOutputShowsDownloadInfo
   */
  public static final Flag<Boolean> BUILD_OUTPUT_DOWNLOADS_INFORMATION = new BooleanFlag(
    GRADLE_IDE, "build.output.downloads.information", "Enable downloads information in Build/Sync View",
    "Show separate node with downloads information in Build and Sync views.", true);

  public static final Flag<Boolean> DISABLE_FORCED_UPGRADES = new BooleanFlag(
    GRADLE_IDE, "forced.agp.update", "Disable forced Android Gradle plugin upgrades",
    "This option is only respected when running Android Studio internally.", false);

  public static final Flag<Boolean> RECOMMEND_AGP_PATCH_RELEASES = new BooleanFlag(
    GRADLE_IDE, "recommend.patch.releases", "Recommend upgrading to the latest patch release of AGP",
    "While stable versions of Android Studio support importing projects of newer patch releases of the same major-minor series " +
    "unless this is enabled, the upgrade assistant will not recommend those updates.",
    false);

  public static final Flag<Boolean> SUPPORT_FUTURE_AGP_VERSIONS = new BooleanFlag(
    GRADLE_IDE, "support.future.agp.versions", "Support opening projects that use future AGPs",
    "Respect the Android Gradle plugin's minimum model consumer version (i.e. minimum required Studio version), " +
    "if present in AGP, superseding the hardcoded maximum supported version of AGP. " +
    "This opens the possibility for Studio to open versions of AGP released after it was, if that version of AGP declares " +
    "that it is compatible.", false);

  public static final Flag<Boolean> GRADLE_SYNC_PARALLEL_SYNC_ENABLED = new BooleanFlag(
    GRADLE_IDE, "gradle.sync.parallel.sync.enabled", "Enables parallel sync",
    "This allows the IDE to fetch models in parallel (if supported by Gradle and enabled via org.gradle.parallel=true).", true);

  public static final Flag<Boolean> GRADLE_SYNC_PARALLEL_SYNC_PREFETCH_VARIANTS = new BooleanFlag(
    GRADLE_IDE, "gradle.sync.parallel.sync.prefetch.variants", "Enables speculative syncing of current variants",
    "This allows the IDE to pre-fetch models for the currently selected variants in parallel before resolving the " +
    "new variant selection (which is less parallelizable process).", false);

  public static final Flag<Boolean> GRADLE_SYNC_FETCH_KOTLIN_MODELS_IN_PARALLEL = new BooleanFlag(
    GRADLE_IDE, "gradle.sync.fetch.kotlin.models.in.parallel", "Enables parallel fetching of Kotlin models",
    "This allows the IDE to fetch Kotlin models in parallel", true);


  public static final Flag<String> SYNC_STATS_OUTPUT_DIRECTORY = new StringFlag(
    GRADLE_IDE, "sync.stats.output.directory", "Enables printing sync stats to a file",
    "If not empty, sync execution stats for models requested by Android Studio are printed to a file in the given directory when" +
    "sync completes.", "");

  public static final Flag<Boolean> GRADLE_SYNC_ENABLE_CACHED_VARIANTS = new BooleanFlag(
    GRADLE_IDE, "gradle.sync.enable.cached.variants", "Enables caching of build variants",
    "Enables caching of build variant data so that the IDE does not always run Gradle when switching between build variants. " +
    "While faster this mode may be incompatible with some plugins.", true);

  public static final Flag<Boolean> GRADLE_SYNC_USE_V2_MODEL = new BooleanFlag(
    GRADLE_IDE, "gradle.sync.use.v2", "Use V2 Builder models", "Enable fetching V2 builder models from AGP when syncing.", true);

  public static final Flag<Boolean> GRADLE_SYNC_RECREATE_JDK = new BooleanFlag(
    GRADLE_IDE, "gradle.sync.recreate.jdk", "Recreate JDK on sync", "Recreate Gradle JDK when syncing if there are changed roots.", true);

  public static final Flag<Boolean> GRADLE_USES_LOCAL_JAVA_HOME_FOR_NEW_CREATED_PROJECTS = new BooleanFlag(
    GRADLE_IDE, "gradle.uses.local.java.home.for.new.created.projects",
    "Gradle uses local java.home for new created projects",
    "When creating new projects the gradleJvm will be configured with #GRADLE_LOCAL_JAVA_HOME macro, using the java.home value " +
    "specified under .gradle/config.properties to trigger Gradle sync.", true);

  public static final Flag<Boolean> MIGRATE_PROJECT_TO_GRADLE_LOCAL_JAVA_HOME = new BooleanFlag(
    GRADLE_IDE, "migrate.project.to.gradle.local.java.home",
    "Migrate project to Gradle local java.home",
    "Suggest migrating current project JDK configuration to .gradle/config.properties where gradleJvm uses the " +
    "#GRADLE_LOCAL_JAVA_HOME macro and the java.home stores the JDK path to trigger Gradle sync.", true);

  public static final Flag<Boolean> RESTORE_INVALID_GRADLE_JDK_CONFIGURATION = new BooleanFlag(
    GRADLE_IDE, "restore.invalid.gradle.jdk.configuration", "Restore invalid Gradle JDK configuration",
    "Restore project from invalid Gradle JDK configuration during opening.", !isUnitTestMode());

  public static final Flag<Boolean> GRADLE_SAVE_LOG_TO_FILE = new BooleanFlag(
    GRADLE_IDE, "save.log.to.file", "Save log to file", "Appends the build log to the given file", false);

  public static final Flag<Boolean> SHOW_GRADLE_AUTO_SYNC_SETTING_UI =
    new BooleanFlag(GRADLE_IDE, "gradle.sync.control.enabled", "Allow disabling of Auto Sync", "Allow opting-out from Gradle Auto Syncing.",
                    true);

  public static final Flag<Boolean> SHOW_GRADLE_AUTO_SYNC_SETTING_IN_NON_EXPERIMENTAL_UI =
    new BooleanFlag(GRADLE_IDE, "gradle.sync.control.enabled.stable", "Allow disabling of Auto Sync via non-experimental settings",
                    "Allow opting-out from Gradle Auto Syncing via non-experimental part of settings.",
                    enabledUpTo(CANARY));
  /**
   * Don't read this directly, use AgpVersions.agpVersionStudioFlagOverride which handles the 'stable' alias
   */
  public static final Flag<String> AGP_VERSION_TO_USE = new StringFlag(
    GRADLE_IDE, "agp.version.to.use", "Version of AGP to use",
    "The AGP version to use when making a new project, e.g. \"8.0.0-dev\". To use the latest stable version of AGP, set the value" +
    "to \"stable\". When set, a compatible Gradle version will also be " +
    "selected. If unset, the latest AGP version and the latest Gradle version will be used.",
    ""
  );

  public static final Flag<Boolean> USE_STABLE_AGP_VERSION_FOR_NEW_PROJECTS = new BooleanFlag(
    GRADLE_IDE, "use.stable.agp.version.for.new.projects",
    "Use the stable AGP version for new projects",
    "Default to using the stable version of the Android Gradle plugin in new projects, rather than the " +
    "latest that this version of Android Studio knows about. " +
    "This is enabled by default in nightly versions as the corresponding -dev version of AGP is not published, " +
    "outside of snapshot builds. " +
    "This does not affect the behavior when running from sources from the tools/adt/idea idea project.",
    enabledUpTo(NIGHTLY));


  public static final Flag<Boolean> GRADLE_SKIP_RUNTIME_CLASSPATH_FOR_LIBRARIES = new BooleanFlag(
    GRADLE_IDE,
    "gradle.skip.runtime.classpath.for.libraries",
    "Enable the Gradle experimental setting to skip runtime classpath resolution for libraries",
    "Enables the Gradle experimental setting to skip the runtime classpath resolution for libraries," +
    " instead obtain the information from the applications dependency graph.",
    true
  );

  public static final Flag<Boolean> GRADLE_BUILD_RUNTIME_CLASSPATH_FOR_LIBRARY_UNIT_TESTS = new BooleanFlag(
    GRADLE_IDE,
    "gradle.build.runtime.classpath.for.library.unit.tests",
    "Controls whether runtime classpath is fetched for library unit tests",
    "Controls whether runtime classpath is fetched for library unit tests. " +
    "Requires gradle.ide.gradle.skip.runtime.classpath.for.libraries to be on to take effect",
    true
  );

  public static final Flag<Boolean> GRADLE_BUILD_RUNTIME_CLASSPATH_FOR_LIBRARY_SCREENSHOT_TESTS = new BooleanFlag(
    GRADLE_IDE,
    "gradle.build.runtime.classpath.for.library.screenshot.tests",
    "Controls whether runtime classpath is fetched for library screenshot tests",
    "Controls whether runtime classpath is fetched for library screenshot tests. " +
    "Requires gradle.ide.gradle.skip.runtime.classpath.for.libraries to be on to take effect",
    true
  );


  public static final Flag<String> GRADLE_LOCAL_DISTRIBUTION_URL = new StringFlag(
    GRADLE_IDE, "local.distribution.url", "Local override for distributionUrl",
    "When creating a project, Gradle updates the distributionUrl to point to a server accessible via the internet. When internet egress " +
    "is unavailable, this flag can be used to override the server destination to be a local URI.",
    ""
  );

  public static final Flag<String> GRADLE_HPROF_OUTPUT_DIRECTORY = new StringFlag(
    GRADLE_IDE,
    "gradle.hprof.output.directory",
    "Gradle sync HPROF output directory",
    "If set, HPROF snapshots will be created at certain points during project sync and saved in the directory",
    ""
  );

  public static final Flag<String> GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY = new StringFlag(
    GRADLE_IDE,
    "gradle.heap.analysis.output.directory",
    "Gradle heap analysis output directory",
    "If set, files with information about heap usage such as total live objects size and the strongly reachable objects size, will be dumped" +
    "to a file at certain points during project sync.",
    ""
  );

  public static final Flag<Boolean> GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE = new BooleanFlag(
    GRADLE_IDE,
    "gradle.heap.analysis.lightweight.mode",
    "Gradle heap analysis lightweight mode",
    "If set, the analysis will just get a histogram using standard JVM APIs. It's suggested to use -XX:SoftRefLRUPolicyMSPerMB=0 in gradle " +
    "jvm args to reduce the variance in these readings.",
    false
  );

  public static final Flag<Boolean> GRADLE_MULTI_VARIANT_ADDITIONAL_ARTIFACT_SUPPORT = new BooleanFlag(
    GRADLE_IDE,
    "gradle.multi.variant.additional.artifact.support",
    "Gradle multi variant additional artifact support",
    "Enable an option in the Gradle experimental settings to switch to building additional artifacts (javadocs/srcs/samples) " +
    "inside Gradle rather than an injected model builder. This allows us to support variant specific artifacts and prevents the IDE from" +
    " having to match by Gradle coordinate. This flag will have no effect if used with a version of AGP before 8.1.0-alpha8.",
    true
  );

  public static final Flag<Boolean> USE_FLAT_DEPENDENCY_GRAPH_MODEL = new BooleanFlag(
    GRADLE_IDE,
    "use.flat.dependency.graph.model",
    "Switches to a flat representation of the dependency model to improve performance",
    "Switches to a flat representation of the dependency model to improve performance. The behaviour is guarded behind a flag" +
    "until we can decide to enable it. This currently reduces some functionality around views / analyses regarding dependency structure",
    false
  );

  public static final Flag<Boolean> MULTIPLE_DEVICE_SPECS_ENABLED = new BooleanFlag(
    GRADLE_IDE,
    "enable.multiple.device.specs",
    "Multiple Device Specs",
    "Allows Studio to pass multiple device spec files separately to AGP along with target device spec.",
    false
  );

  //endregion
  //region Gradle Phased Sync
  private static final FlagGroup PHASED_SYNC = new FlagGroup(FLAGS, "phased.sync", "Gradle Phased Sync");

  public static final Flag<Boolean> PHASED_SYNC_ENABLED = new BooleanFlag(
    PHASED_SYNC,
    "enabled",
    "Enables phased sync",
    "Enables the new sync mode where the models are streamed back to IDE as they become available in phases. These APIs also" +
    " allow direct interaction with the workspace model via new APIs",
    enabledUpTo(CANARY)
  );

  public static final Flag<Boolean> PHASED_SYNC_BRIDGE_DATA_SERVICE_DISABLED = new BooleanFlag(
    PHASED_SYNC,
    "disable.bridge.data.service",
    "Disables bridge data service for phased sync",
    "Entities set up by phased sync are not completely trusted by the IntelliJ platform and is later being replaced by what's " +
    "populated by the data services. To enable this a 'bridge data service' is used to completely remove entities set up by phased sync. " +
    "However we've done extensive feasibility work to make sure we don't actually need this replacement behaviour, meaning we can disable " +
    "this behaviour completely. This flag is a fail-safe to make sure we can switch this behaviour back to platform's default, if needed.",
    enabledUpTo(CANARY)
  );
  //endregion

  //region Apk Project System
  private static final FlagGroup APK_IDE = new FlagGroup(FLAGS, "apk.ide", "APK Project System");

  public static final Flag<Boolean> ENABLE_APK_PROJECT_SYSTEM =
    new BooleanFlag(APK_IDE, "enable.apk.project.system", "Use a dedicated APK project system for debugging or profiling APKs",
                    "If enabled, use the in-development APK project system for project-related services.",
                    enabledUpTo(STABLE));
  //endregion
  //endregion

  //region Layout Inspector
  private static final FlagGroup LAYOUT_INSPECTOR = new FlagGroup(FLAGS, "layout.inspector", "Layout Inspector");
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_USE_DEVBUILD_SKIA_SERVER = new BooleanFlag(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.devbuild.skia", "Use the locally-built skia rendering server",
    "If enabled and this is a locally-built studio instance, use the locally-built skia server instead of one from the SDK.", false);

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED = new BooleanFlag(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.enable.running.devices", "Enable Layout Inspector in Running Devices",
    "When this flag is enabled, LayoutInspector be integrated in the Running Devices tool window, instead of in its own tool window.",
    true);

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_THROW_UNEXPECTED_ERROR = new BooleanFlag(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.enable.throw.unexpected.error", "Throw exception when encountering an unexpected error",
    "When this flag is enabled, LayoutInspector will throw an exception when an unexpected error is being logged to the metrics.",
    isAndroidStudio() && StudioPathManager.isRunningFromSources());

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_IGNORE_RECOMPOSITIONS_IN_FRAMEWORK = new BooleanFlag(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.ignore.framework.recompositions", "Ignore recompositions in compose framework",
    "When this flag is enabled, LayoutInspector will disregard all recomposition counts for framework composables, " +
    "such that the user can concentrate on their own code.",
    true);

  public static final Flag<String> DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_DEVELOPMENT_FOLDER = new StringFlag(
    LAYOUT_INSPECTOR, "dev.jar.location", "Location of prebuilt compose app inspection jar for development",
    "If APP_INSPECTION_USE_DEV_JAR is enabled use this location to load the inspector jar in development.",
    "prebuilts/tools/common/app-inspection/androidx/compose/ui/"
  );

  public static final Flag<String> DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_RELEASE_FOLDER = new StringFlag(
    LAYOUT_INSPECTOR, "rel.jar.location", "Location of prebuilt compose app inspection jar for releases",
    "If APP_INSPECTION_USE_DEV_JAR is enabled use this location to load the inspector jar in releases.",
    ""
  );

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_EXTRA_LOGGING = new BooleanFlag(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.extra.logging", "Add extra logging for problem detection",
    "When this flag is enabled, LayoutInspector will add extra logging for detection of various problems.",
    false);

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_RECOMPOSITION_COUNTS_DEFAULT = new BooleanFlag(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.recomposition.counts.default", "Enable or disable recomposition counts by default",
    "When this flag is enabled, recomposition counts will be enabled by default.",
    true);

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_RECOMPOSITION_PARENT_COUNTS = new BooleanFlag(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.recomposition.parent.counts", "Enable or disable recomposition parent counts",
    "When this flag is enabled, the max recomposition count among the children of a node is displayed in a separate column.",
    true);

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_XR_INSPECTION = new BooleanFlag(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.xr.inspection", "Enable or disable support for XR inspection",
    "When this flag is enabled, xr inspection is enabled.",
    enabledUpTo(STABLE));

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_ON_DEVICE_RENDERING = new BooleanFlag(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.on.device.rendering", "Always use on-device rendering",
    "Force using on-device rendering, even when the device is not XR. Used for development only.",
    false);

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_HORIZONTAL_SCROLLABLE_COMPONENT_TREE = new BooleanFlag(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.horizontal.scrollable.component.tree",
    "Horizontal scroll for layout inspector component tree",
    "When this flag is enabled, we enable horizontal scrolling for the Layout Inspector's component tree.",
    enabledUpTo(STABLE));
  //endregion

  //region Embedded Emulator
  private static final FlagGroup EMBEDDED_EMULATOR = new FlagGroup(FLAGS, "embedded.emulator", "Embedded Emulator");
  public static final Flag<Boolean> EMBEDDED_EMULATOR_SCREENSHOT_STATISTICS = new BooleanFlag(
    EMBEDDED_EMULATOR, "screenshot.statistics", "Enable Collection of Screenshot Statistics",
    "Captures statistics of received Emulator screenshots",
    false);
  public static final Flag<Integer> EMBEDDED_EMULATOR_STATISTICS_INTERVAL_SECONDS = new IntFlag(
    EMBEDDED_EMULATOR, "screenshot.statistics.interval", "Aggregation Interval for Screenshot Statistics",
    "Aggregation interval in seconds for statistics of received Emulator screenshots",
    120);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_GRPC_CALLS = new BooleanFlag(
    EMBEDDED_EMULATOR, "trace.grpc.calls", "Enable Emulator gRPC Tracing",
    "Enables tracing of most Emulator gRPC calls",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS = new BooleanFlag(
    EMBEDDED_EMULATOR, "trace.high.volume.grpc.calls", "Enable High Volume Emulator gRPC Tracing",
    "Enables tracing of high volume Emulator gRPC calls",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_SCREENSHOTS = new BooleanFlag(
    EMBEDDED_EMULATOR, "trace.screenshots", "Enable Emulator Screenshot Tracing",
    "Enables tracing of received Emulator screenshots",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS = new BooleanFlag(
    EMBEDDED_EMULATOR, "trace.notifications", "Enable Emulator Notification Tracing",
    "Enables tracing of received Emulator notifications",
    true);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_DISCOVERY = new BooleanFlag(
    EMBEDDED_EMULATOR, "trace.discovery", "Enable Tracing of Emulator Discovery",
    "Enables tracing of Emulator discovery",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_ALLOW_XR_AVD = new BooleanFlag(
    EMBEDDED_EMULATOR, "allow.xr", "Allow XR AVD to run embedded",
    "Enables running an XR AVD in the Running Devices tool window",
    enabledUpTo(CANARY));
  public static final Flag<Boolean> EMBEDDED_EMULATOR_XR_HAND_TRACKING = new BooleanFlag(
    EMBEDDED_EMULATOR, "xr.hand.tracking", "Enable hand tracking input mode for XR AVDs",
    "Enables hand tracking input mode for XR AVDs",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_XR_EYE_TRACKING = new BooleanFlag(
    EMBEDDED_EMULATOR, "xr.eye.tracking", "Enable eye tracking input mode for XR AVDs",
    "Enables eye tracking input mode for XR AVDs",
    false);
  public static final Flag<Boolean> RUNNING_DEVICES_HIDE_TOOL_WINDOW_NAME = new BooleanFlag(
    EMBEDDED_EMULATOR, "hide.tool.window.name", "Hide Tool Window Name",
    "Hides the name of the Running Devices window when it contains any device tabs",
    true);
  public static final Flag<Boolean> RUNNING_DEVICES_WRAP_TOOLBAR = new BooleanFlag(
    EMBEDDED_EMULATOR, "wrap.toolbar", "Enable Toolbar Wrapping",
    "Wraps the toolbar when all buttons don't fit into the available width",
    true);
  public static final Flag<Boolean> RUNNING_DEVICES_CONTEXT_MENU = new BooleanFlag(
    EMBEDDED_EMULATOR, "context.menu", "Enable Context Menu",
    "Enables context menu in the Running Devices tool window",
    false);
  //endregion

  //region Device Mirroring
  private static final FlagGroup DEVICE_MIRRORING = new FlagGroup(FLAGS, "device.mirroring", "Device Mirroring");
  public static final Flag<Boolean> DEVICE_MIRRORING_STANDALONE_EMULATORS = new BooleanFlag(
    DEVICE_MIRRORING, "allow.standalone.emulators", "Allow Mirroring of Standalone Emulators",
    "Treats standalone emulators the same as physical devices for the purpose of display mirroring;" +
    " not intended for production use due to slowness of video encoding in emulated mode",
    false);
  public static final Flag<Boolean> DEVICE_MIRRORING_REMOTE_EMULATORS = new BooleanFlag(
    DEVICE_MIRRORING, "allow.remote.emulators", "Allow Mirroring of Remote Emulators",
    "Treats remote emulators the same as physical devices for the purpose of display mirroring",
    false);
  public static final Flag<String> DEVICE_MIRRORING_AGENT_LOG_LEVEL = new StringFlag(
    DEVICE_MIRRORING, "agent.log.level", "On Device Logging Level for Mirroring",
    "The log level used by the screen sharing agent, one of \"verbose\", \"debug\", \"info\", \"warn\" or \"error\";" +
    " the default is \"info\"",
    "");
  public static final Flag<Integer> DEVICE_MIRRORING_CONNECTION_TIMEOUT_MILLIS = new IntFlag(
    DEVICE_MIRRORING, "connection.timeout", "Connection Timeout for Mirroring",
    "Connection timeout for mirroring in milliseconds",
    10_000);
  public static final Flag<Integer> DEVICE_MIRRORING_MAX_BIT_RATE = new IntFlag(
    DEVICE_MIRRORING, "max.bit.rate", "Maximum Bit Rate for Mirroring of Physical Devices",
    "The maximum bit rate of video stream, zero means no limit",
    0);
  public static final Flag<String> DEVICE_MIRRORING_VIDEO_CODEC = new StringFlag(
    DEVICE_MIRRORING, "video.codec", "Video Codec Used for Mirroring of Physical Devices",
    "The name of a video codec, e.g. \"vp8\" or \"vp9\"; the default is \"vp8\"",
    "");
  public static final Flag<Boolean> DEVICE_MIRRORING_USE_UINPUT = new BooleanFlag(
    DEVICE_MIRRORING, "use.uinput", "Use uinput module (https://kernel.org/doc/html/v4.12/input/uinput.html)",
    "Use uinput module ((https://kernel.org/doc/html/v4.12/input/uinput.html) for injecting input events",
    false);
  public static final Flag<Boolean> DEVICE_MIRRORING_UNICODE_TYPING = new BooleanFlag(
    DEVICE_MIRRORING, "unicode.typing", "Enable Unicode Typing",
    "Enable typing of arbitrary Unicode characters",
    false);
  //endregion

  //region Screenshot and Screen Recording
  private static final FlagGroup SCREENSHOT = new FlagGroup(FLAGS, "screenshot", "Screenshot and Screen Recording");
  public static final Flag<Boolean> SCREENSHOT_STREAMLINED_SAVING = new BooleanFlag(
    SCREENSHOT, "streamlined.saving", "Save Screenshots and Screen Recordings without Asking User",
    "Save screenshots and screen recordings without asking user",
    true);
  public static final Flag<Boolean> SCREENSHOT_RESIZING = new BooleanFlag(
    SCREENSHOT, "resizing", "Allow Screenshots to Be Resized",
    "Allow screenshots to be resized",
    true);
  public static final Flag<Boolean> MULTI_DISPLAY_SCREENSHOTS = new BooleanFlag(
    SCREENSHOT, "multi.display", "Take Screenshots of All Displays",
    "Take screenshots of all device displays",
    false);
  //endregion

  // region Device Definition Download Service
  private static final FlagGroup DEVICE_DEFINITION_DOWNLOAD_SERVICE =
    new FlagGroup(FLAGS,
                  "device.definition.download.service",
                  "Device Definition Download Service");

  @NotNull
  public static final Flag<String> DEVICE_DEFINITION_DOWNLOAD_SERVICE_URL =
    new StringFlag(DEVICE_DEFINITION_DOWNLOAD_SERVICE,
                   "url",
                   "URL",
                   "The URL to download the device definitions from",
                   "");
  // endregion

  //region Refactorings
  private static final FlagGroup REFACTORINGS = new FlagGroup(FLAGS, "refactor", "Refactor menu");

  public static final Flag<Boolean> MIGRATE_TO_RESOURCE_NAMESPACES_REFACTORING_ENABLED = new BooleanFlag(
    REFACTORINGS, "migrateto.resourcenamespaces.enabled", "Enable the Migrate to Resource Namespaces refactoring",
    "If enabled, show the action in the refactoring menu", false);

  public static final Flag<Boolean> MIGRATE_TO_NON_TRANSITIVE_R_CLASSES_REFACTORING_ENABLED = new BooleanFlag(
    REFACTORINGS, "migrateto.nontransitiverclasses.enabled", "Enable the Migrate to non-transitive R classes refactoring",
    "If enabled, show the action in the refactoring menu", true);

  public static final Flag<Boolean> INFER_ANNOTATIONS_REFACTORING_ENABLED = new BooleanFlag(
    REFACTORINGS, "infer.annotations.enabled", "Enable the Infer Annotations refactoring",
    "If enabled, show the action in the refactoring menu", false);

  public static final Flag<Boolean> ENABLE_GMAVEN_REPOSITORY_V2 = new BooleanFlag(
    REFACTORINGS,
    "gmaven.repository.v2.enabled",
    "Switches to GMaven Repository V2",
    "If enabled, uses GMaven Repository V2 to pull data related to packages, artifacts, versions and their dependencies",
    false
  );

  //endregion

  //region NDK
  private static final FlagGroup NDK = new FlagGroup(FLAGS, "ndk", "Native code features");

  public static final Flag<Boolean> APK_DEBUG_BUILD_ID_CHECK = new BooleanFlag(
    NDK, "apkdebugbuildidcheck", "Enable build ID check in APK debugging",
    "If enabled, the build ID of user-provided symbol files are compared against the binaries inside the APK.", true);

  public static final Flag<Boolean> APK_DEBUG_RELOAD = new BooleanFlag(
    NDK, "apkdebugreload", "Enable APK reloading feature",
    "If enabled, the user will be provided with an option to reload the APK inside an APK debugging project", true);

  private static final FlagGroup NDK_SIDE_BY_SIDE = new FlagGroup(FLAGS, "ndk.sxs", "NDK Side by Side");
  public static final Flag<Boolean> NDK_SIDE_BY_SIDE_ENABLED = new BooleanFlag(
    NDK_SIDE_BY_SIDE, "ndk.sxs.enabled", "Enable side by side NDK support",
    "If enabled, C/C++ projects will have NDK side by side support",
    true);

  public static final Flag<Boolean> ENABLE_SHOW_FILES_UNKNOWN_TO_CMAKE = new BooleanFlag(
    NDK, "ndk.projectview.showfilessunknowntocmake", "Enable option to show files unknown to CMake",
    "If enabled, for projects using CMake, Android project view menu would show an option to `Show Files Unknown To CMake`.",
    true
  );

  // b/202709703: Disable jb_formatters (which is used to pull Natvis) temporarily, because
  // the latest changes in cidr-debugger cause the jb_formatters to conflict with the
  // built-in lldb formatters.
  public static final Flag<Boolean> ENABLE_LLDB_NATVIS = new BooleanFlag(
    NDK, "lldb.natvis", "Use NatVis visualizers in native debugger",
    "If enabled, native debugger formats variables using NatVis files found in the project.",
    false
  );
  //endregion

  //region Editor
  private static final FlagGroup EDITOR = new FlagGroup(FLAGS, "editor", "Editor features");

  public static final Flag<Boolean> TRANSLATIONS_EDITOR_SYNCHRONIZATION = new BooleanFlag(
    EDITOR, "translations.editor.synchronization",
    "Synchronize translations editor with resource file updates",
    "If enabled, causes the translations editor to reload data when resource files are edited",
    false
  );

  public static final Flag<Boolean> COMPOSE_STATE_READ_INLAY_HINTS_ENABLED = new BooleanFlag(
    EDITOR, "compose.state.read.inlay.hints.enabled",
    "Enable inlay hints for State reads in @Composable functions",
    "If enabled, calls out reads of variables of type State inside @Composable functions.",
    enabledUpTo(CANARY));

  public static final Flag<Boolean> REMOTE_SDK_DOCUMENTATION_FETCH_VIA_CONTENT_SERVING_API_ENABLED = new BooleanFlag(
    EDITOR, "remote.sdk.documentation.fetch.via.content.serving.api.enabled",
    "Enable use of the ContentServing API for fetching Android SDK documentation.",
    "If enabled, calls a OnePlatform HTTP API instead of the developers.android.com web server for documentation.",
    enabledUpTo(CANARY));

  public static final Flag<Boolean> RESTRICT_TO_COMPLETION_WEIGHER = new BooleanFlag(
    EDITOR, "restrict.to.completion.weigher",
    "Enable use of the weigher that demotes elements annotated with @RestrictTo.",
    "If enabled, the APIs that are annotated with @RestrictTo will have lower priority in the completion list.",
    enabledUpTo(CANARY));

  //endregion

  //region Unified App Bundle
  private static final FlagGroup UAB = new FlagGroup(FLAGS, "uab", "Unified App Bundle");

  public static final Flag<Boolean> UAB_ENABLE_NEW_INSTANT_APP_RUN_CONFIGURATIONS = new BooleanFlag(
    UAB, "enable.ia.run.configs", "Enable new instant app run configuration options",
    "If enabled, shows the new instant app deploy checkbox in the run configuration dialog and allows new instant app deploy workflow.",
    true
  );
  //endregion

  //region Testing
  private static final FlagGroup TESTING = new FlagGroup(FLAGS, "testing", "Testing support");

  public static final Flag<Boolean> PRINT_INSTRUMENTATION_STATUS = new BooleanFlag(
    TESTING, "print.instrumentation.status", "Print instrumentation status information when testing",
    "If enabled, instrumentation output keys (from calling Instrumentation#sendStatus) that begin with 'android.studio.display.' "
    + "will have their values printed after a test has finished running.",
    true
  );

  public static final Flag<Boolean> ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS = new BooleanFlag(
    TESTING, "additional.testing.gradle.options", "Show additional Gradle Options in Gradle RunConfiguration editor",
    "If enabled, Gradle RunConfiguration shows an additional Android Studio specific options to customize Gradle task execution," +
    "  such as showing test results in the test matrix, or use the device selector view to choose the target device.",
    enabledUpTo(CANARY)
  );

  public static final Flag<Boolean> ENABLE_SCREENSHOT_TESTING = new BooleanFlag(
    TESTING, "screenshot.testing", "Run screenshot tests",
    "If enabled, preview screenshot tests can be run from Studio and test results will be displayed in the test matrix",
    enabledUpTo(DEV)
  );

  public static final Flag<Boolean> ENABLE_BACKUP_TESTING = new BooleanFlag(
    TESTING, "backup.testing", "Run backup and restore tests",
    "If enabled, backup and restore tests can be run from Studio and test results will be displayed in the test matrix",
    false
  );

  public static final Flag<Integer> ANDROID_PLATFORM_TO_AUTOCREATE = new IntFlag(
    TESTING,
    "android.platform.to.autocreate",
    "Android platform to auto-create",
    "Automatically sets up the JDK table at initialization time and points to the specified API level of the Android SDK " +
    "(rather than always pointing to the latest). This is largely intended for use by tests where Android Studio can't be easily " +
    "configured ahead of time. If this value is 0, then this flag is considered to be off and no platform will be automatically created. " +
    "If this value is -1, then the platform will be automatically created with the latest version.",
    0
  );
  //endregion

  //region System Health
  private static final FlagGroup SYSTEM_HEALTH = new FlagGroup(FLAGS, "system.health", "System Health");
  public static final Flag<Boolean> WINDOWS_UCRT_CHECK_ENABLED = new BooleanFlag(
    SYSTEM_HEALTH, "windows.ucrt.check.enabled", "Enable Universal C Runtime system health check",
    "If enabled, a notification will be shown if the Universal C Runtime in Windows is not installed",
    true);

  public static final Flag<Boolean> ANTIVIRUS_NOTIFICATION_ENABLED = new BooleanFlag(
    SYSTEM_HEALTH, "antivirus.notification.enabled", "Enable antivirus system health check",
    "If enabled, a notification will be shown if antivirus realtime scanning is enabled and directories relevant to build performance aren't excluded",
    true);

  public static final Flag<Boolean> ANTIVIRUS_METRICS_ENABLED = new BooleanFlag(
    SYSTEM_HEALTH, "antivirus.metrics.enabled", "Enable antivirus metrics collection",
    "If enabled, metrics about the status of antivirus realtime scanning and excluded directories will be collected",
    true);
  //endregion

  // region Preview Common
  private static final FlagGroup PREVIEW_COMMON = new FlagGroup(FLAGS, "preview", "Preview");

  public static final Flag<Boolean> PREVIEW_RENDER_QUALITY = new BooleanFlag(
    PREVIEW_COMMON, "render.quality", "Enable the usage of a render quality management mechanism for Preview tools",
    "If enabled, different Previews will be rendered with different qualities according to zoom level, layout and scroll position",
    true);

  public static final Flag<Long> PREVIEW_RENDER_QUALITY_DEBOUNCE_TIME = new LongFlag(
    PREVIEW_COMMON, "render.quality.debounce.time", "Render quality debounce time",
    "Milliseconds to wait before adjusting the quality of Previews, after a scroll or zoom change happens",
    100L);

  public static final Flag<Integer> PREVIEW_RENDER_QUALITY_VISIBILITY_THRESHOLD = new IntFlag(
    PREVIEW_COMMON, "render.quality.visibility.threshold", "Render quality zoom visibility threshold",
    "When the zoom level is lower than this value, all previews will be rendered at low quality",
    20);

  public static final Flag<Boolean> PREVIEW_ESSENTIALS_MODE = new BooleanFlag(
    PREVIEW_COMMON, "essentials.mode", "Enable Preview Essentials Mode",
    "If enabled, Preview Essentials Mode will be enabled.",
    enabledUpTo(CANARY));

  public static final Flag<Boolean> VIEW_IN_FOCUS_MODE = new BooleanFlag(
    PREVIEW_COMMON, "view.preview.in.focus", "View preview in Focus mode",
    "If enabled, shows a menu item to open the selected preview in Focus mode.",
    enabledUpTo(CANARY));

  public static final Flag<Boolean> ADD_PREVIEW_IMAGE_TO_AI_REQUEST_FOR_CODE_GENERATION = new BooleanFlag(
    PREVIEW_COMMON, "add.image.to.ai.request.for.preview",
    "Add preview image to AI request for code generation",
    "If enabled, adds current preview image to an AI request for code generation.",
    true);

  public static final Flag<Boolean> FIND_PREVIEWS_FROM_PREVIEW_SOURCESET = new BooleanFlag(
    PREVIEW_COMMON, "find.previews.from.sourceset",
    "Find previews from a file and also from preview-sourceset",
    "If enabled, the process to find previews for a file will also search in its associated files from the preview-sourceset",
    false
  );

  public static final Flag<Boolean> PREVIEW_SOURCESET_UI = new BooleanFlag(
    PREVIEW_COMMON, "preview.sourceset.ui",
    "Enable UI for preview-sourseset",
    "If enabled, the UI specific to preview-sourceset will be enabled.",
    false
  );

  public static final Flag<Boolean> PREVIEW_FILTER = new BooleanFlag(
    PREVIEW_COMMON, "filter",
    "Support filtering the previews",
    "If enabled, the user can find the filter actions to filter the visible previews",
    false
  );

  public static final Flag<Boolean> PREVIEW_PAGINATION = new BooleanFlag(
    PREVIEW_COMMON, "pagination",
    "Support paginating the previews",
    "If enabled, the previews shown in a file will be paginated",
    false
  );
  //endregion

  //region Compose
  private static final FlagGroup COMPOSE = new FlagGroup(FLAGS, "compose", "Compose");

  public enum ClosureScheme {CLASS, INDY}

  public static final Flag<ClosureScheme> CLOSURE_SCHEME = new EnumFlag<>(
    COMPOSE,
    "deploy.codegen.closure.scheme",
    "Lambda / SAM code generation scheme",
    "Implementation of lambda used by Kotlin / Compose code generation within Studio",
    ClosureScheme.CLASS);

  public static final Flag<Boolean> COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE = new BooleanFlag(
    COMPOSE, "preview.scroll.on.caret.move", "Enable the Compose Preview scrolling when the caret moves",
    "If enabled, when moving the caret in the text editor, the Preview will show the preview currently under the cursor.",
    false);

  public static final Flag<Boolean> LIVE_EDIT_ENABLE_BY_DEFAULT = new BooleanFlag(
    COMPOSE, "deploy.live.edit.deploy.enable.default",
    "Enable live edit by default",
    "If enabled, live edit will be enabled by default",
    true
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_ADVANCED_SETTINGS_MENU = new BooleanFlag(
    COMPOSE, "deploy.live.edit.deploy.advanced.settings",
    "Enable live edit deploy settings menu",
    "If enabled, advanced Live Edit settings menu will be visible",
    false
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_CONFINED_ANALYSIS = new BooleanFlag(
    COMPOSE, "deploy.live.edit.deploy.confined.analysis",
    "LiveEdit: Limit compilation error analysis to only the current file",
    "If enabled, Live Edit will aggressively live update even if there are analysis errors " +
    "provided that the current file is error-free.",
    false
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_R8_DESUGAR = new BooleanFlag(
    COMPOSE, "deploy.live.edit.deploy.desugar.r8",
    "LiveEdit: Desugar kotlinc outputs with R8",
    "If enabled, the outputs of kotlinc are desugared before being sent to LiveEdit engine. This improves " +
    "the odds of matching what was produced by the Build system",
    true
  );

  public static final Flag<String> COMPOSE_DEPLOY_LIVE_EDIT_COMPILER_FLAGS = new StringFlag(
    COMPOSE, "deploy.live.edit.deploy.compiler.flags",
    "LiveEdit: Set custom kotlin compiler flags",
    "If enabled, the flags passed to the Kotlin compiler in Live Edit will be replaced with the list of flags provided",
    ""
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_ALLOW_MULTIPLE_MIN_API_DEX_MARKERS_IN_APK = new BooleanFlag(
    COMPOSE, "deploy.live.edit.allow.multiple.min.api.dex.markers.in.apk",
    "LiveEdit: Allow multiple min api dex markers in apk",
    "If enabled, apk may contain multiple min api dex markers and LiveEdit picks the lowest among them",
    false
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_BUILD_SYSTEM_MIN_SDK_VERSION_FOR_DEXING = new BooleanFlag(
    COMPOSE, "deploy.live.edit.build.system.min.sdk.version.for.dexing",
    "LiveEdit: Use Min SDK for Dexing from the build system",
    "If enabled, Live Edit uses the Min SDK information from the build system. Otherwise, use the information from the DEX marker",
    false
  );

  public static final Flag<Boolean> LIVE_EDIT_COMPACT_STATUS_BUTTON = new BooleanFlag(
    COMPOSE, "deploy.live.edit.compact.status.button",
    "LiveEdit: Use a Single Button to Display Live Edit Status in the Toolbar of the Running Devices Window",
    "If enabled, no status text will be displayed in the toolbar of the Running Devices window",
    enabledUpTo(CANARY)
  );

  public static final Flag<Boolean> COMPOSE_DEBUG_BOUNDS = new BooleanFlag(
    COMPOSE, "preview.debug.bounds",
    "Enable the debug bounds switch controls",
    "If enabled, the user can enable/disable the painting of debug bounds",
    false
  );

  public static final Flag<Boolean> COMPOSE_PREVIEW_RESIZING = new BooleanFlag(
    COMPOSE, "preview.resizing",
    "Enable resizing for Compose Preview",
    "If enabled, the user can resize the Compose Preview",
    enabledUpTo(CANARY)
  );

  public static final Flag<Integer> COMPOSE_INTERACTIVE_FPS_LIMIT = new IntFlag(
    COMPOSE, "preview.interactive.fps.limit",
    "Interactive Preview FPS limit",
    "Controls the maximum number of frames per second in Compose Interactive Preview",
    30
  );

  public static final Flag<Boolean> COMPOSE_PROJECT_USES_COMPOSE_OVERRIDE = new BooleanFlag(
    COMPOSE, "project.uses.compose.override", "Forces the Compose project detection",
    "If enabled, the project will be treated as a Compose project, showing Previews if available and enhancing the Compose editing",
    false);

  public static final Flag<Boolean> COMPOSE_ALLOCATION_LIMITER = new BooleanFlag(
    COMPOSE, "allocation.limiter", "If enabled, limits allocations per render",
    "If enabled, limits the number of allocations that user code can do in a single render action",
    true);

  public static final Flag<Boolean> COMPOSE_INVALIDATE_ON_RESOURCE_CHANGE = new BooleanFlag(
    COMPOSE, "preview.invalidate.on.resource.change", "When a resource changes, invalidate the current preview",
    "Invalidates the preview is there is a resource change",
    true);

  public static final Flag<Boolean> COMPOSE_GENERATE_SAMPLE_DATA = new BooleanFlag(
    COMPOSE, "generate.sample.data", "Enable sample data generation for Compose",
    "Enable a Gemini context-menu action that generates sample data for a given Composable function",
    false);

  public static final Flag<Boolean> COMPOSE_PREVIEW_GENERATE_PREVIEW = new BooleanFlag(
    COMPOSE, "preview.generate.preview.action", "Enable editor action for generating Compose Previews",
    "Enable context-menu actions that can generate a Compose Preview corresponding to the selected @Composable",
    enabledUpTo(CANARY));

  public static final Flag<Boolean> COMPOSE_PREVIEW_GENERATE_EXTRA_PARAMETER_CONTEXT = new BooleanFlag(
    COMPOSE, "preview.generate.extra.parameter.context", "Enable additional parameter context when generating Compose Previews",
    "Enables an experiment of adding extra context when generating Compose Previews. The extra context will include information that should help instantiate parameters required by the Composable method used in the preview.",
    enabledUpTo(CANARY));

  public static final Flag<Boolean> COMPOSE_UI_CHECK_FOR_WEAR = new BooleanFlag(
    COMPOSE, "ui.check.mode.wear", "Enable UI Check mode for Compose preview for Wear OS",
    "Enable UI Check mode in Compose preview for running ATF checks and Visual Linting on Wear OS devices.",
    true);

  public static final Flag<Boolean> COMPOSE_UI_CHECK_AI_QUICK_FIX = new BooleanFlag(
    COMPOSE, "ui.check.mode.ai.quickfix", "Enable AI-powered quick fix action for UI Check",
    "Enable an AI-powered quick fix action for UI Check issues.",
    enabledUpTo(DEV));

  public static final Flag<Boolean> COMPOSE_PREVIEW_TRANSFORM_UI_WITH_AI = new BooleanFlag(
    COMPOSE, "transform.ui.with.ai", "Enable action to transform UI with Gemini",
    "Enables a context-menu action to transform UI with Gemini.",
    enabledUpTo(CANARY));

  public static final Flag<Boolean> COMPOSE_PREVIEW_CODE_TO_PREVIEW_NAVIGATION = new BooleanFlag(
    COMPOSE, "preview.code.to.preview.navigation", "Enable the highlighting of preview components when clicking on code",
    "If a user moves their caret to a element present in a preview, we highlight those elements",
    true);
  //endregion

  // region Wear surfaces
  private static final FlagGroup WEAR_SURFACES = new FlagGroup(FLAGS, "wear.surfaces", "Wear Surfaces");

  public static final Flag<Boolean> GLANCE_APP_WIDGET_PREVIEW = new BooleanFlag(
    WEAR_SURFACES, "glance.preview.appwidget.enabled", "Enable Glance AppWidget preview",
    "If enabled, a preview for annotated glance app widget composable functions is displayed",
    true);

  public static final Flag<Boolean> WEAR_TILE_PREVIEW = new BooleanFlag(
    WEAR_SURFACES, "wear.tile.preview.enabled", "Enable Wear Tile preview",
    "If enabled, a preview for functions annotated with @Preview and returning TilePreviewData is displayed",
    true);

  public static final Flag<Boolean> WEAR_TILE_ANIMATION_INSPECTOR = new BooleanFlag(
    WEAR_SURFACES, "wear.tile.preview.animation.inspector.enabled", "Enable Wear Tile Preview Animation Inspector",
    "If enabled, a Wear Tile Animation Inspector functionality is available in Preview",
    true);
  // endregion

  // region Wear Health Services

  private static final FlagGroup WEAR_HEALTH_SERVICES = new FlagGroup(FLAGS, "wear.health.services", "Wear Health Services");

  public static final Flag<Boolean> WEAR_HEALTH_SERVICES_PANEL = new BooleanFlag(
    WEAR_HEALTH_SERVICES, "enable.panel", "Enable Wear Health Services panel",
    "If enabled, a button to display panel for modifying emulator sensors will appear",
    true
  );

  public static final Flag<Long> WEAR_HEALTH_SERVICES_POLLING_INTERVAL_MS = new LongFlag(
    WEAR_HEALTH_SERVICES, "polling.interval", "Wear Health Services polling interval",
    "The polling interval in milliseconds to be used when querying Wear Health Services for updates",
    TimeUnit.SECONDS.toMillis(1)
  );
  // endregion

  // region Wear Declarative Watch Face
  private static final FlagGroup WEAR_DECLARATIVE_WATCH_FACE = new FlagGroup(FLAGS, "wear.dwf", "Declarative Watch Face");

  public static final Flag<Boolean> WEAR_DECLARATIVE_WATCH_FACE_RUN_CONFIGURATION = new BooleanFlag(
    WEAR_DECLARATIVE_WATCH_FACE, "run.configuration.enabled", "Enable run configuration for Declarative Watch Faces",
    "If enabled, the Declarative Watch Face run configuration type will be available. Changing the value of this flag requires restarting Android Studio.",
    enabledUpTo(CANARY)
  );

  public static final Flag<Boolean> WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT = new BooleanFlag(
    WEAR_DECLARATIVE_WATCH_FACE, "xml.editor.support.enabled", "Enable XML editor support for Declarative Watch Faces",
    "If enabled, the editor will support Watch Face Format in XML files",
    enabledUpTo(CANARY)
  );

  public static final Flag<Boolean> WATCH_FACE_STUDIO_FILE_IMPORT = new BooleanFlag(
    WEAR_DECLARATIVE_WATCH_FACE, "wfs.import.enabled", "Enable support for importing Watch Face Studio files (.wfs)",
    "If enabled, it will be possible to import Watch Face Studio files.",
    enabledUpTo(DEV)
  );
  // endregion

  // region App Inspection
  private static final FlagGroup APP_INSPECTION = new FlagGroup(FLAGS, "appinspection", "App Inspection");
  public static final Flag<Boolean> ENABLE_APP_INSPECTION_TOOL_WINDOW = new BooleanFlag(
    APP_INSPECTION, "enable.tool.window", "Enable App Inspection Tool Window",
    "Enables the top-level App Inspection tool window, which will contain tabs to various feature inspectors",
    true
  );

  public static final Flag<Boolean> APP_INSPECTION_USE_DEV_JAR = new BooleanFlag(
    APP_INSPECTION, "use.dev.jar", "Use a precompiled, prebuilt inspector jar",
    "If enabled, grab inspector jars from prebuilt locations, skipping over version checking and dynamic resolving of " +
    "inspector artifacts from maven. This is useful for devs who want to load locally built inspectors.",
    false
  );

  public static final Flag<Boolean> APP_INSPECTION_USE_SNAPSHOT_JAR = new BooleanFlag(
    APP_INSPECTION, "use.snapshot.jar", "Always extract latest inspector jar from library",
    "If enabled, override normal inspector resolution logic, instead searching the IDE cache directly. This allows finding " +
    "inspectors bundled in local, snapshot builds of Android libraries, as opposed to those released through the normal process on maven.",
    false
  );

  public static final Flag<Boolean> APP_INSPECTION_USE_EXPERIMENTAL_DATABASE_INSPECTOR = new BooleanFlag(
    APP_INSPECTION, "use.experimental.database.inspector", "Use experimental Database Inspector",
    "Use experimental Database Inspector",
    enabledUpTo(CANARY)
  );
  // endregion

  // region Network Inspector
  private static final FlagGroup NETWORK_INSPECTOR = new FlagGroup(FLAGS, "network.inspector", "Network Inspector");
  public static final Flag<Boolean> ENABLE_NETWORK_MANAGER_INSPECTOR_TAB = new BooleanFlag(
    NETWORK_INSPECTOR, "enable.network.inspector.tab", "Enable Network Inspector Tab",
    "Enables a Network Inspector Tab in the App Inspection tool window",
    true
  );
  public static final Flag<Boolean> ENABLE_NETWORK_INTERCEPTION = new BooleanFlag(
    NETWORK_INSPECTOR, "enable.network.interception", "Enable Network Interception",
    "Enables interceptions on network requests and responses",
    true
  );
  public static final Flag<Boolean> NETWORK_INSPECTOR_STATIC_TIMELINE = new BooleanFlag(
    NETWORK_INSPECTOR, "static.timeline", "Use static timeline in Network Inspector",
    "Use static timeline in Network Inspector",
    true
  );
  public static final Flag<Boolean> NETWORK_INSPECTOR_GRPC = new BooleanFlag(
    NETWORK_INSPECTOR, "grpc", "Track gRPC Connections",
    "Track gRPC Connections",
    true
  );
  public static final Flag<Boolean> NETWORK_INSPECTOR_COPY_AS_CURL = new BooleanFlag(
    NETWORK_INSPECTOR, "copy.as.curl",
    "Copy as a cURL command",
    "Copy as a cURL command",
    true
  );
  public static final Flag<Boolean> NETWORK_INSPECTOR_RULE_VARIABLES = new BooleanFlag(
    NETWORK_INSPECTOR, "rule.variables",
    "Enable Rule Variables",
    "Enable Rule Variables",
    true
  );
  // endregion

  // region BackgroundTask Inspector
  private static final FlagGroup BACKGROUND_TASK_INSPECTOR =
    new FlagGroup(FLAGS, "backgroundtask.inspector", "BackgroundTask Inspector");
  public static final Flag<Boolean> ENABLE_BACKGROUND_TASK_INSPECTOR_TAB = new BooleanFlag(
    BACKGROUND_TASK_INSPECTOR, "enable.backgroundtask.inspector.tab", "Enable BackgroundTask Inspector Tab",
    "Enables a BackgroundTask Inspector Tab in the App Inspection tool window",
    true
  );
  // endregion

  //region Device Manager
  private static final FlagGroup DEVICE_MANAGER = new FlagGroup(FLAGS, "device.manager", "Device Manager");

  public static final Flag<Boolean> POST_MVP_VIRTUAL_DEVICE_DIALOG_FEATURES_ENABLED = new BooleanFlag(
    DEVICE_MANAGER,
    "post.mvp.virtual.device.dialog.features.enabled",
    "Post MVP Virtual Device Dialog Features Enabled",
    "Enable miscellaneous Add/Edit Device dialog features for post MVP",
    false);

  public static final Flag<Boolean> XR_DEVICE_SUPPORT_ENABLED = new BooleanFlag(
    DEVICE_MANAGER,
    "xr.device.support.enabled",
    "XR Device Support Enabled",
    "Enable the support of XR device in the device manager",
    true);
  // endregion

  //region DDMLIB
  private static final FlagGroup DDMLIB = new FlagGroup(FLAGS, "ddmlib", "DDMLIB");
  public static final Flag<Boolean> ENABLE_JDWP_PROXY_SERVICE = new BooleanFlag(
    DDMLIB, "enable.jdwp.proxy.service", "Enable jdwp proxy service",
    "Creates a proxy service within DDMLIB to allow shared device client connections.",
    false
  );
  public static final Flag<Boolean> ENABLE_DDMLIB_COMMAND_SERVICE = new BooleanFlag(
    DDMLIB, "enable.ddmlib.command.service", "Enable ddmlib command service",
    "Creates a service within DDMLIB to allow external processes to issue commands to ddmlib.",
    false
  );
  // endregion DDMLIB

  // region Play Policy Insights
  private static final FlagGroup PLAY_POLICY_INSIGHTS = new FlagGroup(FLAGS, "playpolicyinsights", "Play Policy Insights");

  public static final Flag<Boolean> ENABLE_PLAY_POLICY_INSIGHTS =
    new BooleanFlag(
      PLAY_POLICY_INSIGHTS,
      "play.policy.insights",
      "Play Policy Insights",
      "Enable Play Policy Insights",
      enabledUpTo(CANARY));

  public static final Flag<Boolean> PLAY_POLICY_INSIGHTS_AUTO_UPDATE =
    new BooleanFlag(
      PLAY_POLICY_INSIGHTS,
      "play.policy.insights.auto.update",
      "Play Policy Insights Auto Update",
      "Update Play Policy lint rule library to the latest",
      enabledUpTo(CANARY));
  // endregion Play Policy Insights

  // region Firebase Test Lab
  private static final FlagGroup FIREBASE_TEST_LAB = new FlagGroup(FLAGS, "firebasetestlab", "Firebase Test Lab");

  // TODO(b/304622231) deprecate StudioFlags.DIRECT_ACCESS
  public static final Flag<Boolean> DIRECT_ACCESS =
    new BooleanFlag(
      FIREBASE_TEST_LAB,
      "direct.access",
      "Direct Access",
      "Enable FTL DirectAccess",
      true);

  public static final Flag<Boolean> DIRECT_ACCESS_CREATE_PROJECT =
    new BooleanFlag(
      FIREBASE_TEST_LAB,
      "direct.access.create.project",
      "Direct Access Create Project",
      "Create a cloud project on logging in and authenticating Firebase",
      true);

  public static final Flag<Boolean> DIRECT_ACCESS_SETTINGS_PAGE =
    new BooleanFlag(
      FIREBASE_TEST_LAB,
      "direct.access.settings.page",
      "Device Streaming Settings Page",
      "Show Device Streaming Settings Page",
      false);

  public static final Flag<String> DIRECT_ACCESS_ENDPOINT =
    new StringFlag(
      FIREBASE_TEST_LAB,
      "direct.access.endpoint",
      "FTL Direct Access endpoint",
      "The URL for FTL Direct Access to connect to, in host:port form (with no protocol specified).",
      "testing.googleapis.com"
    );

  public static final Flag<String> DEVICE_STREAMING_ENDPOINT =
    new StringFlag(
      FIREBASE_TEST_LAB,
      "direct.access.new.endpoint",
      "New Device Streaming endpoint",
      "The new URL for Direct Access to connect to, in host:port form (with no protocol specified).",
      "devicestreaming.googleapis.com"
    );

  public static final Flag<String> DIRECT_ACCESS_MONITORING_ENDPOINT =
    new StringFlag(
      FIREBASE_TEST_LAB,
      "direct.access.monitoring.endpoint",
      "FTL Direct Access Monitoring endpoint",
      "The URL for FTL Direct Access to monitor quota usage and limit.",
      "monitoring.googleapis.com"
    );

  public static final Flag<Boolean> SHOW_OEM_LAB_DEVICES =
    new BooleanFlag(
      FIREBASE_TEST_LAB,
      "direct.access.show.oem.lab.devices",
      "Show OEM lab devices",
      "OEM lab devices are available to users.",
      enabledUpTo(CANARY));

  // endregion Firebase Test Lab

  // region App Insights
  private static final FlagGroup APP_INSIGHTS = new FlagGroup(FLAGS, "appinsights", "App Insights");

  public static final Flag<String> APP_INSIGHTS_AI_INSIGHT_ENDPOINT =
    new StringFlag(
      APP_INSIGHTS,
      "app.insights.ai.insight.endpoint",
      "App insights AI insight endpoint",
      "Endpoint for getting AI insight",
      "cloudaicompanion.googleapis.com"
    );

  public static final Flag<Boolean> GEMINI_FETCH_REAL_INSIGHT =
    new BooleanFlag(
      APP_INSIGHTS,
      "gemini.fetch.real.insight",
      "Fetch real insights",
      "Fetch actual insights from AiInsightClient",
      true
    );

  public static final Flag<Boolean> GEMINI_ASSISTED_CONTEXT_FETCH =
    new BooleanFlag(
      APP_INSIGHTS,
      "gemini.assisted.context.fetch",
      "Ask Gemini for context files",
      "Ask Gemini for the context files it needs to generate an insight.",
      enabledUpTo(CANARY)
    );

  public static final Flag<Boolean> SUGGEST_A_FIX = new BooleanFlag(
    APP_INSIGHTS,
    "suggest.a.fix",
    "Enables suggest a fix button in insights panel",
    "Allows AQI to provide suggested fix based on the generated insight.",
    enabledUpTo(CANARY)
  );

  public static final Flag<String> CRASHLYTICS_GRPC_SERVER =
    new StringFlag(
      APP_INSIGHTS,
      "crashlytics.grpc.server",
      "Set Crashlytics gRpc server address",
      "Set Crashlytics gRpc server address, mainly used for testing purposes.",
      "firebasecrashlytics.googleapis.com");

  public static final Flag<Boolean> CRASHLYTICS_INTEGRATION_TEST_MODE =
    new BooleanFlag(
      APP_INSIGHTS,
      "crashlytics.integration.test.mode",
      "Crashlytics Integration Test Mode",
      "Set Crashlytics to be in integration test mode.",
      false);

  public static final Flag<Boolean> CRASHLYTICS_INSIGHT_IN_TOOLWINDOW =
    new BooleanFlag(
      APP_INSIGHTS,
      "crashlytics.show.insight.tool.window",
      "Show insight toolwindow in Crashlytics",
      "Show AI generated insights for Crashlytics issue in insight toolwindow",
      true
    );

  public static final Flag<String> PLAY_VITALS_GRPC_SERVER =
    new StringFlag(
      APP_INSIGHTS,
      "play.vitals.grpc.server",
      "Set Play Vitals gRpc server address",
      "Set Play Vitals gRpc server address, mainly used for testing purposes.",
      "playdeveloperreporting.googleapis.com");

  public static final Flag<Boolean> PLAY_VITALS_GRPC_USE_TRANSPORT_SECURITY =
    new BooleanFlag(
      APP_INSIGHTS,
      "play.vitals.grpc.use.transport.security",
      "Use transport security",
      "Set Play Vitals gRpc channel to use transport security",
      true);

  public static final Flag<Boolean> PLAY_VITALS_VCS_INTEGRATION_ENABLED =
    new BooleanFlag(
      APP_INSIGHTS,
      "play.vitals.vcs.integration",
      "Enable VCS integration for Play Vitals.",
      "Enhance code navigation in the Play Vitals tab to aid crash investigation with the recorded VCS info",
      true);

  public static final Flag<Boolean> PLAY_VITALS_INSIGHT_IN_TOOLWINDOW =
    new BooleanFlag(
      APP_INSIGHTS,
      "play.vitals.show.insight.tool.window",
      "Show insight toolwindow in Play Vitals",
      "Show AI generated insights for Play Vitals issue in insight toolwindow",
      true
    );
  // endregion App Insights

  // region App Links Assistant
  private static final FlagGroup APP_LINKS_ASSISTANT = new FlagGroup(FLAGS, "app.links.assistant", "App Links Assistant");
  public static final Flag<Boolean> WEBSITE_ASSOCIATION_GENERATOR_V2 =
    new BooleanFlag(APP_LINKS_ASSISTANT, "website.association.generator.v2", "Website Association Generator V2",
                    "Improvements to Website Association Generator.", enabledUpTo(CANARY));
  public static final Flag<String> DEEPLINKS_GRPC_SERVER =
    new StringFlag(APP_LINKS_ASSISTANT, "deeplinks.grpc.server", "Deep links gRPC server address",
                   "Deep links gRPC server address. Use a non-default value for testing purposes.",
                   "deeplinkassistant-pa.googleapis.com");
  public static final Flag<Boolean> CREATE_APP_LINKS_V2 =
    new BooleanFlag(APP_LINKS_ASSISTANT, "create.app.links.v2", "Create App Links V2",
                    "Improvements to the Create App Links functionalities.", false);
  public static final Flag<Boolean> IMPACT_TRACKING =
    new BooleanFlag(APP_LINKS_ASSISTANT, "app.links.assistant.impact.tracking", "App Links Assistant impact tracking",
                    "Impact tracking for the App Links Assistant", false);
  public static final Flag<Boolean> DOMAIN_ISSUES_INSPECTION =
    new BooleanFlag(APP_LINKS_ASSISTANT, "app.links.assistant.domain.issues.inspection", "App Links Assistant domain issues inspection",
                    "Domain issues inspection that opens relevant App Links Assistant content", false);
  // endregion App Links Assistant

  // region NEW_COLLECT_LOGS_DIALOG
  private static final FlagGroup NEW_COLLECT_LOGS_DIALOG = new FlagGroup(FLAGS, "new.collect.logs", "New Collect Logs Dialog");
  // endregion NEW_COLLECT_LOGS_DIALOG

  // region TargetSDKVersion Upgrade Assistant
  private static final FlagGroup TSDKVUA = new FlagGroup(FLAGS, "tsdkvua", "Android SDK Upgrade Assistant");
  public static final Flag<Boolean> TSDKVUA_FILTERS_ONSTART =
    new BooleanFlag(TSDKVUA, "filters.onstart", "Run filters on assistant startup", "Run filters on assistant startup", true);
  public static final Flag<Boolean> TSDKVUA_FILTERS_ONSTART_RESET =
    new BooleanFlag(TSDKVUA, "filters.onstart.reset", "Reset the results cache before running filters on startup",
                    "Reset the results cache before running filters on startup", true);
  public static final Flag<Boolean> TSDKVUA_FILTERS_WIP =
    new BooleanFlag(TSDKVUA, "filters.wip", "Enable WIP relevance filters", "Enable WIP relevance filters", false);
  public static final Flag<Boolean> TSDKVUA_API_35 =
    new BooleanFlag(TSDKVUA, "api35", "Enable support for API 35", "Enable support for API 35", true);
  public static final Flag<Boolean> TSDKVUA_OMG_76167 = new BooleanFlag(TSDKVUA, "omg76167", "Do NOT mitigate omg/76167",
                                                                        "Mitigating omg/76167 requires hiding part of API 35's 'secured background activity launches' step",
                                                                        false);
  // endregion TargetSDKVersion Upgrade Assistant

  // region PROCESS_NAME_MONITOR
  private static final FlagGroup PROCESS_NAME_MONITOR = new FlagGroup(FLAGS, "processnamemonitor", "Process Name Monitor");
  public static final Flag<Integer> PROCESS_NAME_MONITOR_MAX_RETENTION = new IntFlag(
    PROCESS_NAME_MONITOR, "processnamemonitor.max.retention", "Set max process retention",
    "Maximum number of processes to retain after they are terminated. Changing the value of this flag requires restarting Android Studio.",
    100
  );
  public static final Flag<Boolean> PROCESS_NAME_TRACKER_AGENT_ENABLE = new BooleanFlag(
    PROCESS_NAME_MONITOR, "processnamemonitor.tracker.agent.enable", "Enable process tracking agent",
    "Enable process tracking using an agent deployed to the device. Changing the value of this flag requires restarting Android Studio.",
    true
  );
  public static final Flag<Integer> PROCESS_NAME_TRACKER_AGENT_INTERVAL_MS = new IntFlag(
    PROCESS_NAME_MONITOR, "processnamemonitor.tracker.agent.interval", "Process tracking agent polling interval",
    "Process tracking agent polling interval in milliseconds. Changing the value of this flag requires restarting Android Studio.",
    1000
  );
  public static final Flag<Boolean> PROCESS_NAME_MONITOR_ADBLIB_ENABLED = new BooleanFlag(
    PROCESS_NAME_MONITOR, "processnamemonitor.adblib.enable", "Enable Adblib monitor",
    "Enable the Adblib version of the process name monitor. " +
    "Note that adblib process tracking can not work concurrently with ddmlib process tracking because only one concurrent JDWP " +
    "session can be open per process per device. Therefore, this feature is only enabled if the flag " +
    "ADBLIB_MIGRATION_DDMLIB_CLIENT_MANAGER is also true. " +
    "Changing the value of this flag requires restarting Android Studio.",
    true
  );
  // endregion NEW_SEND_FEEDBACK_DIALOG

  // region AVD Command Line Options
  private static final FlagGroup
    AVD_COMMAND_LINE_OPTIONS = new FlagGroup(FLAGS, "avd.command.line.options", "AVD Command-Line Options");
  public static final Flag<Boolean> AVD_COMMAND_LINE_OPTIONS_ENABLED = new BooleanFlag(
    AVD_COMMAND_LINE_OPTIONS, "enable", "Enable the AVD Command-Line Options setting",
    "Enable the AVD Command-Line Options setting in the AVD advanced settings panel.",
    false
  );
  // endregion

  // region PRIVACY_SANDBOX_SDK
  private static final FlagGroup PRIVACY_SANDBOX_SDK = new FlagGroup(FLAGS, "privacysandboxsdk", "Privacy Sandbox SDK");
  public static final Flag<Boolean> LAUNCH_SANDBOX_SDK_PROCESS_WITH_DEBUGGER_ATTACHED_ON_DEBUG = new BooleanFlag(
    PRIVACY_SANDBOX_SDK, "launch.process.with.debugger.attached.on.debug", "Launch sandbox SDK process with debugger attached on debug",
    "Whether or not sandbox SDK should launch a process with the debugger attached on debug action.",
    false);
  // endregion PRIVACY_SANDBOX_SDK

  // region STUDIO_BOT
  private static final FlagGroup STUDIOBOT = new FlagGroup(FLAGS, "studiobot", "Gemini");
  public static final Flag<Boolean> STUDIOBOT_ENABLED =
    new BooleanFlag(STUDIOBOT, "enabled", "Enable Gemini", "Enable Gemini Tool Window", false);

  public static final Flag<Boolean> STUDIOBOT_INLINE_CODE_COMPLETION_CES_TELEMETRY_ENABLED =
    new BooleanFlag(STUDIOBOT, "inline.code.completion.ces.telemetry.enabled",
                    "Enable sending inline code completion metrics to the AIDA CES service",
                    "When enabled, metrics related to inline code completion suggestions will be sent to the CES service for AIDA.", false);

  public static final Flag<Boolean> STUDIOBOT_INLINE_CODE_COMPLETION_FILE_CONTEXT_ENABLED =
    new BooleanFlag(STUDIOBOT, "inline.code.completion.file.context.enabled",
                    "Enable sending additional file context with completion requests",
                    "When enabled, additional file context (eg, currently open files) are included in inline code completion requests.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_INLINE_CODE_COMPLETION_INCLUDES_LAST_ACTION =
    new BooleanFlag(STUDIOBOT, "inline.code.completion.include.last.action",
                    "Include the last user action in the code completion request",
                    "When enabled, the type of the last user action is included in inline code completion requests.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_INLINE_CODE_COMPLETION_SHORTCUT_HINT_ENABLED =
    new BooleanFlag(STUDIOBOT, "inline.code.completion.shortcut.hint.enabled",
                    "Enable the inline completion shortcut key hint.",
                    "When enabled, a custom inlay displaying 'TAB to complete' or similar text will be shown alongside inline completions.",
                    enabledUpTo(STABLE));

  public static final Flag<Boolean> STUDIOBOT_INLINE_CODE_COMPLETION_DEFERRED_MULTILINE_SUGGESTIONS_ENABLED =
    new BooleanFlag(STUDIOBOT, "inline.code.completion.deferred.multiline.suggestions.enabled",
                    "Enable deferred multiline suggestions.",
                    "When enabled, any part of a multi-line suggestion hidden behind the autosuggest popup will be removed, and " +
                    "offered later to the user if they accept the first line of the completion.",
                    enabledUpTo(STABLE));

  public static final Flag<Boolean> STUDIOBOT_INLINE_CODE_COMPLETION_SYNTAX_HIGHLIGHTING_ENABLED =
    new BooleanFlag(STUDIOBOT, "inline.code.completion.syntax.highlighting.enabled",
                    "Enable syntax highlighting for inline suggestions.",
                    "When inline completions will use lexical syntax highlighting colors.",
                    enabledUpTo(STABLE));

  public static final Flag<Boolean> STUDIOBOT_COMPILER_ERROR_CONTEXT_ENABLED =
    new BooleanFlag(STUDIOBOT, "compiler.error.context.enabled",
                    "Enable sending context with compiler error queries.",
                    "When enabled, compiler queries will attach context (e.g. error location, full trace), from the project.",
                    enabledUpTo(DEV));

  public static final Flag<Boolean> STUDIOBOT_PROJECT_FACTS_CONTEXT_ENABLED =
    new BooleanFlag(STUDIOBOT, "project.facts.context.enabled",
                    "Enable sending project facts with chat queries.",
                    "When enabled, chat queries will attach summarized facts about the project.",
                    enabledUpTo(DEV));

  public static final Flag<Boolean> STUDIOBOT_GRADLE_ERROR_CONTEXT_ENABLED =
    new BooleanFlag(STUDIOBOT, "gradle.error.context.enabled",
                    "Enable sending contents of Gradle build files with applicable sync/build error queries.",
                    "When enabled, applicable sync/build error queries will attach context (e.g. build file contents), from the project.",
                    enabledUpTo(DEV));

  public static final Flag<Boolean> STUDIOBOT_EDITOR_ACTION_CONTEXT_ENABLED =
    new BooleanFlag(STUDIOBOT, "editor.action.context.enabled",
                    "Enable sending context with editor actions.",
                    "When enabled, queries sent by editor actions, like Explain Code, will attach context (e.g. resolved references) from the project.",
                    enabledUpTo(DEV));

  public static final Flag<Boolean> STUDIOBOT_TRANSFORMS_ENABLED =
    new BooleanFlag(STUDIOBOT, "editor.ai.transforms.enabled",
                    "Enable the transform actions.",
                    "When enabled, the transform actions (document, comment, the custom transform action, etc.) are enabled.",
                    true);

  public static final Flag<Boolean> STUDIOBOT_TRANSFORM_HISTORY_ENABLED =
    new BooleanFlag(STUDIOBOT, "editor.ai.transform.history.enabled",
                    "Enable the transform history in the transform diff.",
                    "When enabled, allows the user to navigate transform history in the diff view.",
                    true);

  public static final Flag<Boolean> STUDIOBOT_SHOW_TRANSFORM_HISTORY_FORWARD_BACK =
    new BooleanFlag(STUDIOBOT, "editor.ai.transform.show.history.forward.back",
                    "Enable the transform history forward/back buttons in the transform diff.",
                    "When enabled, allows the user to navigate forward and back in the transform history in the diff view.",
                    false);

  public static final Flag<Boolean> STUDIOBOT_MULTIFILE_TRANSFORM_OUTPUT_ENABLED =
    new BooleanFlag(STUDIOBOT, "editor.ai.multifile.transform.output.enabled",
                    "Enable the transform to be able to output multiple files.",
                    "When enabled, returns all files modified by models.",
                    enabledUpTo(DEV));

  public static final Flag<Boolean> STUDIOBOT_TRANSFORM_SESSION_DIFF_EDITOR_VIEWER_ENABLED =
    new BooleanFlag(STUDIOBOT, "editor.ai.transform.session.diff.editor.viewer.enabled",
                    "Enable the new DiffEditorViewer UI that can show multiple-file diffs.",
                    "When enabled, uses the new DiffEditorViewer UI.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_ALLOW_TRANSFORMS_WITH_CITATIONS =
    new BooleanFlag(STUDIOBOT, "editor.ai.transform.allow.transforms.with.citations",
                    "Show transform results that have citations.",
                    "When enabled, will show transform results with citations instead of blocking them.",
                    false);

  public static final Flag<Boolean> STUDIOBOT_EXPERIMENTAL_SLASH_COMMANDS_ENABLED =
    new BooleanFlag(STUDIOBOT, "editor.ai.experimental.slash.commands.enabled",
                    "Enable experimental slash comments.",
                    "When enabled, experimental slash commands will be enabled.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_USE_COMPOSE_TOOLWINDOW_UI =
    new BooleanFlag(STUDIOBOT, "chat.use.compose.for.ui",
                    "Use the Compose for Desktop/Jewel-based UI for the Chat toolwindow.",
                    "When enabled, the Chat toolwindow will use the Jewel-based UI, implemented in Compose for Desktop.",
                    true);

  public static final Flag<Boolean> STUDIOBOT_CONTEXT_ATTACHMENT_ENABLED =
    new BooleanFlag(STUDIOBOT, "chat.enable.context.attachment",
                    "Enable @file attachment and the context drawer.",
                    "When enabled, @file can be used to attach text files as context. Also enables the context drawer for context management.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_FOLDER_CONTEXT_SELECTION_ENABLED =
    new BooleanFlag(STUDIOBOT, "chat.enable.folder.context.selection",
                    "Enable @folder attachment.",
                    "When enabled, @folder can be used to attach folders as context.",
                    enabledUpTo(DEV));

  public static final Flag<Long> STUDIOBOT_FOLDER_CONTEXT_MAX_INCLUDED_FILES =
    new LongFlag(STUDIOBOT, "chat.folder.context.max.included.files",
                 "The max number of files included by a @folder attachment.",
                 "Specifies the max number of files included by a @folder attachment.",
                 100L
    );

  public static final Flag<Boolean> STUDIOBOT_DEPENDENCY_SUGGESTION_ENABLED =
    new BooleanFlag(STUDIOBOT, "chat.suggest.dependencies.on.insert",
                    "Suggest missing dependencies when inserting/pasting code snippets",
                    "When enabled, a dependency suggestion dialog will appear when inserting/pasting code snippets that might require missing dependencies.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_HALLUCINATION_DETECTOR_ENABLED =
    new BooleanFlag(STUDIOBOT, "hallucination.detector.enabled",
                    "Run hallucination analysis on generated code.",
                    "When enabled, a hallucination detection utility will run on generated code snippets, and emit metrics when hallucinations are detected.",
                    false);

  public static final Flag<Boolean> STUDIOBOT_CURRENT_FILE_CONTEXT =
    new BooleanFlag(STUDIOBOT, "current.file.context",
                    "Enable the Current File macro in the context drawer",
                    "This macro attaches the current file's path, contents, and selection with chat queries.",
                    enabledUpTo(STABLE));

  public static final Flag<Boolean> STUDIOBOT_RECENT_FILES_CONTEXT =
    new BooleanFlag(STUDIOBOT, "open.files.context",
                    "Enable the Recent Files macro in the context drawer",
                    "This macro attaches the most recently opened files' (but not including the currently open one's) paths and contents with chat queries.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_ASK_GEMINI_INCLUDE_BUILD_FILES_IN_CONTEXT =
    new BooleanFlag(STUDIOBOT, "askgemini.include.build.files.in.context",
                    "Allow build files in 'Ask Gemini' context",
                    "Flag to guard whether to include build configuration files in context of Ask Gemini queries",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_PROMPT_LIBRARY_ENABLED =
    new BooleanFlag(STUDIOBOT, "prompt.library",
                    "Enable Prompt Library",
                    "When enabled, add prompt library settings screen.",
                    true);

  public static final Flag<Boolean> STUDIOBOT_PROMPT_LIBRARY_RULES_ENABLED =
    new BooleanFlag(STUDIOBOT, "prompt.library.rules",
                    "Enable Rules Prompt Library",
                    "When enabled, add Rules section to prompt library settings screen.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_PROMPT_LIBRARY_CHAT_LOOKUP_ENABLED =
    new BooleanFlag(STUDIOBOT, "prompt.library.chat.lookup",
                    "Show Saved Prompts in chat lookup",
                    "When enabled, add Rules section to lookup popup.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_SCROLL_TO_BOTTOM_ENABLED =
    new BooleanFlag(STUDIOBOT, "chat.scroll.to.bottom",
                    "Enable AutoScroll Button",
                    "When enabled, the chat will show a button on the timeline to toggle auto-scrolling.",
                    enabledUpTo(DEV));

  public static final Flag<Boolean> STUDIOBOT_RESPONSE_CANCELLATION_ENABLED =
    new BooleanFlag(STUDIOBOT, "chat.response.cancellation.enabled",
                    "Enable cancellation in Chat Timeline",
                    "When enabled, the chat will show a banner that will allow cancelling ongoing responses.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_SHOW_MODEL_NAME_IN_QUERY_BOX =
    new BooleanFlag(STUDIOBOT, "chat.show.model.name",
                    "Show model name in query box",
                    "Shows the model name in the query box, with an animation when a query is submitted.",
                    false);

  public static final Flag<Boolean> COMMIT_MESSAGE_SUGGESTION =
    new BooleanFlag(STUDIOBOT, "commit.message.suggestion",
                    "Use ML model to suggest commit messages",
                    "Enables the \"Suggest Commit Message\" button in the Commit tool window",
                    true);

  public static final Flag<Boolean> COMMIT_MESSAGE_SUGGESTION_OVERRIDE =
    new BooleanFlag(STUDIOBOT, "commit.message.suggestion.override",
                    "Allow users to override prompt for suggesting commit messages",
                    "Enables the \"Commit Message Generation\" in Prompt Library setting",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> README_GENERATION =
    new BooleanFlag(STUDIOBOT, "readme.generation",
                    "Use ML model to create a README",
                    "Enables the \"Generate README\" button in the Project tool window",
                    false);

  public static final Flag<Boolean> ANALYZE_THREAD_SAFETY =
    new BooleanFlag(STUDIOBOT, "analyze.thread.safety",
                    "Use ML model analyze thread safety of selected files",
                    "Enables the \"Analyze Thread Safety\" button in the Project tool window",
                    false);


  public static final Flag<Boolean> AI_RETHINK_ACTION =
    new BooleanFlag(STUDIOBOT, "ai.rethink.action",
                    "Use AI to suggest better variable names",
                    "Enables AI to provide better variable renaming functionalities",
                    true);


  public static final Flag<Boolean> AI_RENAME_ACTION =
    new BooleanFlag(STUDIOBOT, "ai.rename.action",
                    "Use AI to suggest a better identifier name",
                    "Enables AI rename suggestion functionality",
                    true);

  public static final Flag<Boolean> FIX_WITH_AI_EDITOR_ACTION =
    new BooleanFlag(STUDIOBOT, "ai.fix.error.editor.action",
                    "Use AI to fix simple compiler errors",
                    "Editor action to provide quick fixes for errors", enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_APPLY_CHANGES_ACTION_ENABLED =
    new BooleanFlag(STUDIOBOT, "studiobot.apply.changes.action",
                    "Enable the apply changes action",
                    "When enabled, applies the code block from the chat to the open editor",
                    enabledUpTo(DEV));

  public static final Flag<Boolean> STUDIOBOT_ATTACHMENTS =
    new BooleanFlag(STUDIOBOT, "attachments",
                    "Enable action to add attachments",
                    "When enabled, enables the actions to manage attachments",
                    enabledUpTo(CANARY));

  // rate limits are controlled by server flags
  public static final Flag<Integer> STUDIOBOT_COMPLETIONS_PER_HOUR =
    new IntFlag(STUDIOBOT, "completions.per.hour",
                "AI completion requests per hour",
                "AI completion requests per hour",
                36000);

  public static final Flag<Integer> STUDIOBOT_CONVERSATIONS_PER_HOUR =
    new IntFlag(STUDIOBOT, "conversations.per.hour",
                "AI conversations per hour",
                "AI conversations per hour",
                120);

  public static final Flag<Integer> STUDIOBOT_GENERATIONS_PER_HOUR =
    new IntFlag(STUDIOBOT, "generations.per.hour",
                "AI generation requests per hour",
                "AI generation requests per hour",
                3600);

  public static final Flag<Integer> STUDIOBOT_GENERATION_CANDIDATE_COUNT =
    new IntFlag(STUDIOBOT, "generations.candidate.count",
                "How many candidates to request for each generation",
                "How many candidates to request for each generation",
                1);

  public static final Flag<Integer> STUDIOBOT_CHAT_MODEL_INPUT_TOKEN_LIMIT =
    new IntFlag(STUDIOBOT, "chat.model.input.tokens",
                "Input token limit for default chat model",
                "Input token limit for default chat model",
                16384);

  public static final Flag<Integer> STUDIOBOT_CHAT_MODEL_OUTPUT_TOKEN_LIMIT =
    new IntFlag(STUDIOBOT, "chat.model.output.tokens",
                "Output token limit for default chat model",
                "Output token limit for default chat model",
                8192);

  public static final Flag<Boolean> STUDIOBOT_GENERATE_TEST_SCENARIOS =
    new BooleanFlag(STUDIOBOT, "generate.test.scenarios",
                    "Enable test scenario generation.",
                    "When enabled, generate test scenarios and corresponding function names for the selected code.",
                    true);

  public static final Flag<Boolean> STUDIOBOT_SUPPORT_GIAS_ENTERPRISE =
    new BooleanFlag(STUDIOBOT, "support.gias.enterprise",
                    "Enable support for GCA Enterprise tier",
                    "Enable support for GCA Enterprise tier",
                    true);

  public static Flag<Boolean> STUDIOBOT_SHIMMER_PLACEHOLDER =
    new BooleanFlag(STUDIOBOT, "show.shimmer.placeholder",
                    "Enable shimmering placeholder in chat timeline.",
                    "When enabled, the compose chat timeline will show a shimmering placeholder while awaiting initial response content.",
                    enabledUpTo(CANARY));

  public static Flag<Boolean> GEMINI_BRING_YOUR_OWN_KEY_ENABLED =
    new BooleanFlag(STUDIOBOT, "bring.your.own.key",
                    "Enable providing a public Gemini API key to override the default model.",
                    "When enabled, a setting and various UI is made visible to provide a Gemini API key, and when provided and" +
                    "enabled it replaces the default model with the public Gemini model.",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> STUDIOBOT_INCLUDE_GRADLE_PROJECT_STRUCTURE_TOOLS_BY_DEFAULT =
    new BooleanFlag(STUDIOBOT, "include.gradle.project.structure.tools.by.default",
                    "Enable using Gradle project structure Agent tools by default",
                    "When enabled, a set of tools allowing the agent to query for the Gradle project structure will be included by default.",
                    enabledUpTo(DEV));

  public static final Flag<Boolean> GEMINI_AGENT_MODE =
    new BooleanFlag(STUDIOBOT, "agent.mode",
                    "Enable agent mode.",
                    "When enabled, the agent mode will be enabled in the Gemini toolwindow.", enabledUpTo(CANARY));

  public static final Flag<Boolean> GEMINI_VERSION_UPGRADE_AGENT =
    new BooleanFlag(STUDIOBOT, "version.upgrade.agent",
                    "Enable Gemini Version Upgrade Agent.",
                    "Enables the agent that helps with upgrading dependencies to newer versions.", enabledUpTo(DEV));

  public enum CodeIndexingMode {NONE, BM25}

  public static final EnumFlag<CodeIndexingMode> GEMINI_INDEX_CODEBASE =
    new EnumFlag<>(STUDIOBOT, "codebase.indexing.mode",
                   "Codebase Indexing Mode",
                   "Index the codebase to allow searching using natural language",
                   CodeIndexingMode.NONE);

  public enum DasherSupportMode {
    /**
     * Don't include any special treatment for dasher users.
     * This is mainly useful as a workaround for situations like in b/407825030
     */
    NEVER,
    /**
     * If we detect a dasher user, attempt to automatically figure out their eligibility for various tiers
     */
    AUTO,
    /**
     * Always show the tier selection mode and let the user choose.
     * This is a bypass in case the AUTO mode doesn't work for some reason.
     */
    ALWAYS
  }

  public static final EnumFlag<DasherSupportMode> STUDIOBOT_SUPPORT_GIAS_DASHER_ACCOUNTS =
    new EnumFlag<>(STUDIOBOT, "support.gias.dasher.accounts",
                   "Enable support for GCA Dasher accounts",
                   "Enable support for GCA Dasher accounts",
                   DasherSupportMode.AUTO);

  public static final Flag<Boolean> GEMINI_SHOW_SIGN_IN_DIALOG =
    new BooleanFlag(STUDIOBOT, "gemini.show.sign.in.dialog",
                    "Enable sign in dialog for Gemini",
                    "Enable Gemini actions to display a dialog prompting the user to sign in",
                    enabledUpTo(CANARY));

  public static final Flag<Boolean> GEMINI_VERIFY_USER_TIER_IN_ALL_AIDA_RPCS =
    new BooleanFlag(STUDIOBOT, "verify.user.tier.in.aida.rpcs",
                    "Verify user tier in all API requests to the AIDA endpoint",
                    "Verify user tier in all API requests to the AIDA endpoint",
                    true);

  // endregion STUDIO_BOT

  // region EXPERIMENTAL_UI
  private static final FlagGroup EXPERIMENTAL_UI = new FlagGroup(FLAGS, "experimentalui", "Experimental UI");
  public static final Flag<Boolean> EXPERIMENTAL_UI_SURVEY_ENABLED =
    new BooleanFlag(EXPERIMENTAL_UI, "enabled", "Enable Experimental UI Survey", "Enable the experimental UI survey.", true);
  // endregion EXPERIMENTAL_UI

  // region STUDIO_LABS
  private static final FlagGroup STUDIO_LABS = new FlagGroup(FLAGS, "studiolabs", "Studio Labs");
  public static final Flag<Boolean> STUDIO_LABS_SETTINGS_ENABLED =
    new BooleanFlag(STUDIO_LABS, "enabled", "Enable Studio Labs in settings", "Enables studio labs in settings.", enabledUpTo(DEV));
  public static final Flag<Boolean> STUDIO_LABS_SETTINGS_FAKE_FEATURE_ENABLED =
    new BooleanFlag(STUDIO_LABS, "fakefeature", "Enable fake feature in StudioLabs.", "Enable this for testing.", enabledUpTo(DEV));
  // endregion STUDIO_LABS

  // region WEAR_RUN_CONFIGS_AUTOCREATE
  private static final FlagGroup WEAR_RUN_CONFIGS_AUTOCREATE =
    new FlagGroup(FLAGS, "wear.runconfigs.autocreate", "Autocreate Wear Run Configs");
  public static final Flag<Boolean> WEAR_RUN_CONFIGS_AUTOCREATE_ENABLED =
    new BooleanFlag(WEAR_RUN_CONFIGS_AUTOCREATE, "enabled", "Enable Autocreate Wear Run Configs",
                    "When enabled, Wear run configurations will be automatically created.", true);
  public static final Flag<Integer> WEAR_RUN_CONFIGS_AUTOCREATE_MAX_TOTAL_RUN_CONFIGS =
    new IntFlag(WEAR_RUN_CONFIGS_AUTOCREATE, "max.total.runconfigs", "Maximum total run configurations",
                "Maximum total number of all types of run configurations that can be reached after autocreating Wear Run Configs. Wear Run Configurations will not be created if this limit is breached.",
                10);
  // endregion WEAR_RUN_CONFIGS_AUTOCREATE

  // region Google Login
  private static final FlagGroup GOOGLE_LOGIN =
    new FlagGroup(FLAGS, "google.login", "Google Login");
  public static final Flag<Boolean> ENABLE_COMBINED_LOGIN_COMPOSE_UI =
    new BooleanFlag(GOOGLE_LOGIN, "combined.login.use.compose.flow", "Enable combined login using Compose",
                    "When enabled, a combined sign-in flow using Compose will show when logging in for a new user.", enabledUpTo(CANARY));
  public static final Flag<Boolean> USE_1P_LOGIN_UI =
    new BooleanFlag(GOOGLE_LOGIN, "use.1p.login.ui", "Use 1P login UI",
                    "Use 1P login UI to show the OAuth scopes that will be requested", false);
  public static final Flag<String> CHIME_ENDPOINT =
    new StringFlag(GOOGLE_LOGIN, "chime.endpoint", "Chime Endpoint",
                   "Endpoint to use for Chime API", "notifications-pa.googleapis.com");
  public static final Flag<Boolean> SHOW_MARKETING_DIALOG =
    new BooleanFlag(GOOGLE_LOGIN, "show.marketing.dialog", "Show marketing dialog",
                    "Show marketing dialog after user logs in", true);
  // endregion Google Login

  // region Backup
  private static final FlagGroup BACKUP = new FlagGroup(FLAGS, "backup", "Backup");
  public static final Flag<Boolean> BACKUP_ENABLED =
    new BooleanFlag(
      BACKUP,
      "enable",
      "Enable Backup/Restore feature",
      "Enable Backup/Restore feature",
      true);

  public static final Flag<Integer> BACKUP_GMSCORE_MIN_VERSION =
    new IntFlag(
      BACKUP,
      "gmscore.min.version",
      "Minimum version of the GmsCore Backup module that is supported",
      "Minimum version of the GmsCore Backup module that is supported",
      250632000);

  public static final Flag<Boolean> BACKUP_ACTION_IN_RUNNING_DEVICES =
    new BooleanFlag(
      BACKUP,
      "enable.running.devices",
      "Display Backup action in Running Devices",
      "Display Backup action in Running Devices",
      true);
  // endregion Backup

  // region GOOGLE_PLAY_SDK_INDEX
  private static final FlagGroup GOOGLE_PLAY_SDK_INDEX = new FlagGroup(FLAGS, "google.play.sdk.index", "Google Play SDK Index");
  public static final Flag<Boolean> SHOW_SDK_INDEX_NOTES_FROM_DEVELOPER = new BooleanFlag(
    GOOGLE_PLAY_SDK_INDEX, "show.sdk.index.notes", "Show notes from SDK developer",
    "Whether or not SDK Index critical issues should include notes from developer",
    // The default should match GooglePlaySdkIndex.DEFAULT_SHOW_NOTES_FROM_DEVELOPER so the behavior of Android Studio and CLI is consistent
    true
  );
  public static final Flag<Boolean> SHOW_SDK_INDEX_RECOMMENDED_VERSIONS = new BooleanFlag(
    GOOGLE_PLAY_SDK_INDEX, "show.sdk.index.recommended.versions", "Show SDK recommended versions",
    "Whether or not to display recommended versions on SDK Index issues",
    // The default should match GooglePlaySdkIndex.DEFAULT_SHOW_RECOMMENDED_VERSIONS so the behavior of Android Studio and CLI is consistent
    true
  );
  public static final Flag<Boolean> SHOW_SUMMARY_NOTIFICATION = new BooleanFlag(
    GOOGLE_PLAY_SDK_INDEX, "show.sdk.index.summary.notification", "Show a notification for SDK Index issues",
    "Show a notification after initial sync when there are blocking SDK Index issues",
    true
  );
  public static final Flag<Boolean> SHOW_SDK_INDEX_DEPRECATION_ISSUES = new BooleanFlag(
    GOOGLE_PLAY_SDK_INDEX, "show.sdk.index.deprecation.issues", "Show library deprecation issues",
    "Show issues related to deprecated libraries from SDK Index in Lint and PSD",
    true
  );
  // endregion GOOGLE_PLAY_SDK_INDEXx

  // region JOURNEYS_WITH_GEMINI
  private static final FlagGroup JOURNEYS_WITH_GEMINI = new FlagGroup(FLAGS, "journeys.with.gemini", "Journeys with Gemini");
  public static final Flag<Boolean> JOURNEYS_WITH_GEMINI_EXECUTION = new BooleanFlag(
    JOURNEYS_WITH_GEMINI, "enable.journeys.with.gemini.execution", "Enable Journeys with Gemini execution",
    "Enable Journeys with Gemini related functionality to allow users to create, edit and execute Journeys.",
    enabledUpTo(CANARY)
  );
  public static final Flag<Boolean> JOURNEYS_WITH_GEMINI_AUTO_GRADLE_CONFIGURATION = new BooleanFlag(
    JOURNEYS_WITH_GEMINI, "enable.journeys.with.gemini.auto.gradle.configuration",
    "Enable automatic Gradle configuration for Journeys with Gemini",
    "Applies the Gradle configuration needed to run Journeys automatically when a Journeys run configuration is triggered",
    enabledUpTo(CANARY)
  );
  public static final Flag<String> JOURNEYS_WITH_GEMINI_AUTO_GRADLE_CONFIGURATION_DEP = new StringFlag(
    JOURNEYS_WITH_GEMINI, "dependency.journeys.with.gemini.auto.gradle.configuration",
    "Journey plugin dependency name used by automatic Gradle configuration",
    "The ID of the Journey AGP plugin to use in the init-script injected when JOURNEYS_WITH_GEMINI_AUTO_GRADLE_CONFIGURATION " +
    "is enabled, Use the `-dev` suffix to use a locally built plugin.",
    "com.android.tools.journeys:journeys-gradle-plugin:0.0.1-alpha03"
  );
  public static final Flag<String> JOURNEYS_WITH_GEMINI_AUTO_GRADLE_CONFIGURATION_EXTRA_REPOSITORY_URL = new StringFlag(
    JOURNEYS_WITH_GEMINI, "extra.repository.url.for.journeys.with.gemini.auto.gradle.configuration",
    "URL of extra repository used by automatic Gradle configuration (e.g. staging repo)",
    "URL of extra repository used by automatic Gradle configuration (e.g. staging repo)",
    ""
  );
  public static final Flag<Boolean> JOURNEYS_WITH_GEMINI_AUTO_GRADLE_CONFIGURATION_INIT_SCRIPT_V2 = new BooleanFlag(
    JOURNEYS_WITH_GEMINI, "enable.journeys.with.gemini.auto.gradle.configuration.init.script.v2",
    "Use a V2 version of Journeys init script which is used in the Gradle run configuration for Journeys with Gemini",
    "Applies Journeys Gradle plugin to your Gradle project by the new version of Journeys Gradle init script." +
    " This version includes a fix for ClassNotFound exception when AppPlugin is not applied in your root Gradle project (b/418228060).",
    enabledUpTo(CANARY)
  );
  public static final Flag<Boolean> JOURNEYS_WITH_GEMINI_RECORDING = new BooleanFlag(
    JOURNEYS_WITH_GEMINI, "enable.journeys.with.gemini.recording", "Enable Journeys with Gemini recording",
    "Enable recording of Journeys with Gemini",
    enabledUpTo(DEV)
  );
  // endregion JOURNEYS_WITH_GEMINI

  // region WIZARD_MIGRATION
  private static final FlagGroup WIZARD_MIGRATION = new FlagGroup(
    FLAGS,
    "wizard.migration",
    "Wizard Migration"
  );

  public static final Flag<Boolean> FIRST_RUN_MIGRATED_WIZARD_ENABLED = new BooleanFlag(
    WIZARD_MIGRATION,
    "first.run.migrated.wizard.enabled",
    "Migrated First Run Wizard Enabled",
    "Show the migrated version of the welcome wizard when Studio first starts",
    enabledUpTo(STABLE)
  );
  public static final Flag<Boolean> SDK_SETUP_MIGRATED_WIZARD_ENABLED = new BooleanFlag(
    WIZARD_MIGRATION,
    "sdk.setup.migrated.wizard.enabled",
    "Migrated SDK Setup Wizard Enabled",
    "Show the migrated version of the SDK setup wizard",
    enabledUpTo(STABLE)
  );
  public static final Flag<Boolean> AEHD_CONFIGURATION_MIGRATED_WIZARD_ENABLED = new BooleanFlag(
    WIZARD_MIGRATION,
    "aehd.configuration.migrated.wizard.enabled",
    "Migrated AEHD Configuration Wizard Enabled",
    "Show the migrated version fo the AEHD configuration wizard",
    enabledUpTo(STABLE)
  );
  // endregion WIZARD_MIGRATION

  public static Boolean isBuildOutputShowsDownloadInfo() {
    // In Android Studio: enabled if BUILD_OUTPUT_DOWNLOADS_INFORMATION=true.
    // In IDEA: disables unless the user explicitly overrides BUILD_OUTPUT_DOWNLOADS_INFORMATION.
    return IdeInfo.getInstance().isAndroidStudio() || BUILD_OUTPUT_DOWNLOADS_INFORMATION.isOverridden()
           ? BUILD_OUTPUT_DOWNLOADS_INFORMATION.get()
           : false;
  }

  private static boolean isAndroidStudio() {
    return "AndroidStudio".equals(getPlatformPrefix());
  }

  // region Settings Sync
  private static final FlagGroup SETTINGS_SYNC = new FlagGroup(FLAGS, "settingssync", "Settings Sync");
  public static final Flag<Boolean> SETTINGS_SYNC_ENABLED =
    new BooleanFlag(
      SETTINGS_SYNC,
      "enable",
      "Enable Settings Sync feature",
      "Enable Settings Sync feature",
      enabledUpTo(CANARY));
  // endregion Settings sync

  // region PROJECT_TOOL_WINDOW
  private static final FlagGroup PROJECT_TOOL_WINDOW = new FlagGroup(FLAGS, "project.tool.window", "Project Toolwindow");
  public static final Flag<Boolean> SHOW_DEFAULT_PROJECT_VIEW_SETTINGS =
    new BooleanFlag(
      PROJECT_TOOL_WINDOW,
      "default.project.view",
      "Show UI for default project view in settings",
      "Show UI for default project view in settings",
      enabledUpTo(CANARY));
  public static final Flag<Boolean> SHOW_BUILD_FILES_IN_MODULE_SETTINGS =
    new BooleanFlag(
      PROJECT_TOOL_WINDOW,
      "gradle.files.in.module",
      "Show UI for having build files per module",
      "When enabled, the settings menu will show a checkbox to change the behavior of the Android view to display gradle files under each module",
      enabledUpTo(CANARY));
  // endregion PROJECT_TOOL_WINDOW

  // region Wifi 2.0
  private static final FlagGroup WIFI_V2 = new FlagGroup(FLAGS, "wifiv2", "Wifi V2");
  public static final Flag<Boolean> WIFI_V2_ENABLED =
    new BooleanFlag(
      WIFI_V2,
      "enable",
      "Enable Wifi 2.0",
      "Enable Wifi 2.0 feature",
      false);
  // endregion Wifi 2.0

  // region Benchmark Survey
  private static final FlagGroup BENCHMARK_SURVEY = new FlagGroup(FLAGS, "benchmark.survey", "Benchmark Survey");
  public static final Flag<Boolean> BENCHMARK_SURVEY_ENABLED =
    new BooleanFlag(
      BENCHMARK_SURVEY,
      "enable",
      "Enable Benchmark Survey",
      "Enable the benchmark survey when requesting user satisfaction",
      false);
  // endregion Wifi 2.0

  // region deprecation policy
  private static final FlagGroup DEPRECATION_POLICY = new FlagGroup(FLAGS, "deprecationpolicy", "Deprecation Policy");
  public static final Flag<Boolean> USE_POLICY_WITH_DEPRECATE =
    new BooleanFlag(
      DEPRECATION_POLICY,
      "use.policy.with.deprecate",
      "Use N2 deprecation policy",
      "Use N2 deprecation policy that supports DEPRECATED state",
      enabledUpTo(CANARY));
  public static final Flag<String> DEFAULT_MORE_INFO_URL =
    new StringFlag(
      DEPRECATION_POLICY,
      "default.more.info.url",
      "Defaul More Info URL",
      "Redirect to this URL if moreInfoUrl is not provided",
      "https://developer.android.com/studio/releases#service-compat"
    );
  // endregion deprecation policy

  private StudioFlags() { }

  private static Boolean isUnitTestMode() {
    return ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode();
  }
}
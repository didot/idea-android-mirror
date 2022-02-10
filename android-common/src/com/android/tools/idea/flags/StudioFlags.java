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

import com.android.flags.Flag;
import com.android.flags.FlagGroup;
import com.android.flags.FlagOverrides;
import com.android.flags.Flags;
import com.android.flags.overrides.DefaultFlagOverrides;
import com.android.flags.overrides.PropertyOverrides;
import com.android.tools.idea.flags.overrides.ServerFlagOverrides;
import com.android.tools.idea.util.StudioPathManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

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
    Application app = ApplicationManager.getApplication();
    FlagOverrides userOverrides;
    if (app != null && !app.isUnitTestMode()) {
      userOverrides = StudioFlagSettings.getInstance();
    }
    else {
      userOverrides = new DefaultFlagOverrides();
    }
    return new Flags(userOverrides, new PropertyOverrides(), new ServerFlagOverrides());
  }

  //region New Project Wizard
  private static final FlagGroup NPW = new FlagGroup(FLAGS, "npw", "New Project Wizard");

  public static final Flag<Boolean> NPW_FIRST_RUN_WIZARD = Flag.create(
    NPW, "first.run.wizard", "Show new Welcome Wizard",
    "Show new version of the Welcome Wizard when Studio starts",
    false);

  public static final Flag<Boolean> NPW_FIRST_RUN_SHOW = Flag.create(
    NPW, "first.run.wizard.show", "Show Welcome Wizard always",
    "Show the Welcome Wizard when Studio starts",
    false);

  public static final Flag<Boolean> NPW_SHOW_JDK_STEP = Flag.create(
    NPW, "first.run.jdk.step", "Show JDK setup step",
    "Show JDK Setup Step in Welcome Wizard",
    true);

  public static final Flag<Boolean> NPW_SHOW_FRAGMENT_GALLERY = Flag.create(
    NPW, "show.fragment.gallery", "Show fragment gallery",
    "Show fragment gallery which contains fragment based templates",
    true);

  public static final Flag<Boolean> NPW_SHOW_GRADLE_KTS_OPTION = Flag.create(
    NPW, "show.gradle.kts.option", "Show gradle kts option",
    "Shows an option on new Project/Module to allow the use of Kotlin script",
    false);

  public static final Flag<Boolean> NPW_NEW_NATIVE_MODULE = Flag.create(
    NPW, "new.native.module", "New Android Native Module",
    "Show template to create a new Android Native module in the new module wizard.",
    true);

  public static final Flag<Boolean> NPW_NEW_MACRO_BENCHMARK_MODULE = Flag.create(
    NPW, "new.macro.benchmark.module", "New Macro Benchmark Module",
    "Show template to create a new Macro Benchmark module in the new module wizard.",
    true);

  public static final Flag<Boolean> NPW_MATERIAL3_ENABLED = Flag.create(
    NPW, "new.material3.templates", "New Material3 Templates",
    "Enable the new material 3 templates.",
    true);
  //endregion

  //region Profiler
  private static final FlagGroup PROFILER = new FlagGroup(FLAGS, "profiler", "Android Profiler");

  public static final Flag<Boolean> PROFILER_UNIFIED_PIPELINE = Flag.create(
    PROFILER, "unified.pipeline", "Enables new event pipeline to be used for core components.",
    "Toggles usage of gRPC apis to fetch data from perfd and the datastore.",
    true);

  public static final Flag<Boolean> PROFILER_ENERGY_PROFILER_ENABLED = Flag.create(
    PROFILER, "energy", "Enable Energy profiling",
    "Enable the new energy profiler. It monitors battery usage of the selected app.", true);

  public static final Flag<Boolean> PROFILER_STARTUP_CPU_PROFILING = Flag.create(
    PROFILER, "startup.cpu.profiling", "Enable startup CPU Profiling",
    "Record a method trace on startup by enabling it in the Profiler tab of Run/Debug configuration.",
    true);

  public static final Flag<Boolean> PROFILER_MEMORY_CSV_EXPORT = Flag.create(
    PROFILER, "memory.csv", "Allow exporting entries in memory profiler",
    "Allow exporting entries in the views for heap dump and native/JVM recordings in CSV format.",
    false);

  public static final Flag<Boolean> PROFILER_TRACK_JNI_REFS = Flag.create(
    PROFILER, "jni", "Enable JVMTI-based JNI reference tracking.",
    "For Android O or newer, JNI references are tracked in Memory Profiler and shown in JNI heap.",
    true);

  public static final Flag<Boolean> PROFILER_PERFORMANCE_MONITORING = Flag.create(
    PROFILER, "performance.monitoring", "Enable Profiler Performance Monitoring Options",
    "Toggles if profiler performance metrics options are enabled.",
    false
  );

  public static final Flag<Boolean> PROFILER_CPU_NEW_RECORDING_WORKFLOW = Flag.create(
    PROFILER, "cpu.new.recording.workflow", "Enable new CPU recording workflow",
    "Shows recording options and status of the ongoing recording in the method trace pane.",
    true
  );

  public static final Flag<Boolean> PROFILER_JANK_DETECTION_UI = Flag.create(
    PROFILER, "jank.ui", "Enable jank detection UI",
    "Add a track in the display group showing frame janks.",
    true
  );

  public static final Flag<Boolean> PROFILER_CUSTOM_EVENT_VISUALIZATION = Flag.create(
    PROFILER, "custom.event.visualization", "Enable Profiler Custom Event Visualization",
    "When enabled, profiler will track and display events defined through developer APIs",
    false);

  public static final Flag<Boolean> PROFILER_USE_TRACEPROCESSOR = Flag.create(
    PROFILER, "perfetto.traceprocessor", "Enable TraceProcessorDaemon",
    "Use TraceProcessor to parse Perfetto captures instead of Trebuchet/Atrace backend.",
    true);

  public static final Flag<Boolean> PROFILEABLE = Flag.create(
    PROFILER, "profileable", "Support profileable processes on S+",
    "Show profileable processes on S and later",
    true);

  public static final Flag<Boolean> PROFILER_MIGRATION_TO_APPINSPECTION = Flag.create(
    PROFILER, "profiler.migration.to.appinspection", "Enable migration support for network and system events profiling.",
    "Show migration messaging to guide users to the App Inspection tool window",
    true);

  // PROFILEABLE_IN_QR is an extension of PROFILEABLE. When it's true, it assumes PROFILEABLE is also true.
  public static final Flag<Boolean> PROFILEABLE_IN_QR = Flag.create(
    PROFILER, "profileable.qr", "Support profileable processes on Q & R",
    "Show profileable processes on Q and R",
    PROFILEABLE.get() && true);

  public static final Flag<Boolean> PROFILEABLE_BUILDS = Flag.create(
    PROFILER, "profileable.builds", "Support building profileable apps",
    "Allow users to build apps as profileable with a supported Gradle plugin version (>7.3.0)",
    false);
  //endregion

  //region ML
  private static final FlagGroup ML = new FlagGroup(FLAGS, "ml", "ML");
  public static final Flag<Boolean> ML_MODEL_BINDING = Flag.create(
    ML, "modelbinding", "Enable ML model binding",
    "When enabled, TFLite model file will be recognized and indexed. Please invalidates file caches after enabling " +
    "(File -> Invalidate Caches...) in order to reindex model files.",
    true);
  //endregion

  //region Asset Studio
  private static final FlagGroup ASSET = new FlagGroup(FLAGS, "asset", "Asset Studio");
  public static final Flag<Boolean> ASSET_COPY_MATERIAL_ICONS = Flag.create(
    ASSET, "copy.material.icons", "Allow copying icons to Sdk folder",
    "Allow the IconPickerDialog in Asset Studio to copy bundled material icons in to the Android/Sdk folder",
    true);
  public static final Flag<Boolean> ASSET_DOWNLOAD_MATERIAL_ICONS = Flag.create(
    ASSET, "download.material.icons", "Allow downloading icons to Sdk folder",
    "Allow the IconPickerDialog in Asset Studio to download any new material icons in to the Android/Sdk folder",
    true);
  //endregion

  //region Design Tools
  private static final FlagGroup DESIGN_TOOLS = new FlagGroup(FLAGS, "design.tools", "Design Tools");
  public static final Flag<Boolean> DESIGN_TOOLS_POWER_SAVE_MODE_SUPPORT = Flag.create(
    DESIGN_TOOLS, "power.save.support", "Enable previews support for PowerSave mode",
    "If enabled, the the Layout Editor and Compose Preview will respect the Power Save mode and avoid auto-refresh, reduce FPS, etc.",
    true);
  public static final Flag<Boolean> USE_COMPONENT_TREE_TABLE = Flag.create(
    DESIGN_TOOLS, "design.component.tree.table", "Enable TreeTable implementation of component tree",
    "Use a TreeTable for displaying the component tree in the LayoutInspector and the Nav editor.", true);

  //region Layout Editor
  private static final FlagGroup NELE = new FlagGroup(FLAGS, "nele", "Layout Editor");
  public static final Flag<Boolean> NELE_ANIMATIONS_PREVIEW = Flag.create(
    NELE, "animated.preview", "Show preview animations toolbar",
    "Show an animations bar that allows playback of vector drawable animations.",
    true);
  public static final Flag<Boolean> NELE_ANIMATED_SELECTOR_PREVIEW = Flag.create(
    NELE, "animated.selector.preview", "Show preview animations toolbar for animated selector",
    "Show an animations bar that allows playback of transitions in animated selector.",
    true);
  public static final Flag<Boolean> NELE_ANIMATIONS_LIST_PREVIEW = Flag.create(
    NELE, "animated.list.preview", "Show preview animations toolbar for animation list",
    "Show an animations bar that allows playback of animation list files.",
    true);
  public static final Flag<Boolean> NELE_MOTION_AREA_GRAPH = Flag.create(
    NELE, "motion.area.graph", "Show area graph in Timeline panel",
    "Show area graph in Timeline panel for Motion Editor.",
    true);
  public static final Flag<Boolean> NELE_MOTION_HORIZONTAL = Flag.create(
    NELE, "animated.motion.horizontal", "Display motion editor horizontally",
    "Controls the placement of the motion editor (horizontal versus vertical).",
    false);
  public static final Flag<Boolean> NELE_MOCKUP_EDITOR = Flag.create(
    NELE, "mockup.editor", "Enable the Mockup Editor",
    "Enable the Mockup Editor to ease the creation of Layouts from a design file.",
    false);

  public static final Flag<Boolean> NELE_RENDER_DIAGNOSTICS = Flag.create(
    NELE, "diagnostics", "Enable rendering on-screen stats",
    "If enabled, the surface displays some debug information to diagnose performance",
    false);

  public static final Flag<Boolean> NELE_COLOR_RESOURCE_PICKER_FOR_FILE_EDITORS = Flag.create(
    NELE, "editor.color.picker", "Enable popup color resource picker for java and kotlin files.",
    "Show the popup color resource picker when clicking the gutter icon of color resource in java and kotlin files.",
    true);

  public static final Flag<Boolean> NELE_DRAWABLE_POPUP_PICKER = Flag.create(
    NELE, "show.drawable.popup.picker", "Enable drawable popup picker in Xml Editor.",
    "Show the resource popup picker for picking drawable resources from the Editor's gutter icon.",
    true);

  public static final Flag<Boolean> NELE_DRAWABLE_BACKGROUND_MENU = Flag.create(
    NELE, "show.drawable.background.menu", "Enable background option menu in drawable preview panel.",
    "Show the background option menu to switch the background when previewing drawable resources.",
    true);

  public static final Flag<Boolean> NELE_WEAR_DEVICE_FIXED_ORIENTATION = Flag.create(
    NELE, "wear.fixed.orientation", "Fixes the orientation of wear os devices.",
    "For wear device, force using the portrait for square and round devices and landscape for chin devices.",
    true);

  public static final Flag<Boolean> NELE_LOG_ANDROID_FRAMEWORK = Flag.create(
    NELE, "log.android.framework", "Log messages coming from Layoutlib Native.",
    "Log in the IDEA log the messages coming from Java and native code of Layoutlib Native.",
    false);

  public static final Flag<Boolean> NELE_SCENEVIEW_TOP_TOOLBAR = Flag.create(
    NELE, "sceneview.top.toolbar", "Enable the per SceneView top toolbar.",
    "Enable the per SceneView top toolbar that displays the SceneView contextual actions.",
    true);

  public static final Flag<Boolean> NELE_SCENEVIEW_BOTTOM_BAR = Flag.create(
    NELE, "sceneview.bottom.bar", "Enable the per SceneView bottom bar.",
    "Enable the per SceneView bottom bar that displays the SceneView contextual actions.",
    false);

  public static final Flag<Boolean> NELE_SCENEVIEW_LEFT_BAR = Flag.create(
    NELE, "sceneview.left.bar", "Enable SceneView left bar for overlay actions.",
    "Enable the SceneView left bar that displays the overlay actions.",
    true);


  private static final FlagGroup ASSISTANT = new FlagGroup(FLAGS, "assistant", "Assistants");
  public static final Flag<Boolean> CONNECTION_ASSISTANT_ENABLED = Flag.create(
    ASSISTANT, "connection.enabled", "Enable the connection assistant",
    "If enabled, user can access the Connection Assistant under \"Tools\" and \"Deploy Target Dialog\"",
    true);

  public static final Flag<Boolean> NELE_CONSTRAINT_LAYOUT_ASSISTANT = Flag.create(
    ASSISTANT, "layout.editor.help.constraintlayout", "Display Help for Constraint Layout",
    "If enabled, the assistant panel will display helpful guide on using Constraint Layout.",
    true);

  public static final Flag<Boolean> NELE_MOTION_LAYOUT_ASSISTANT = Flag.create(
    ASSISTANT, "layout.editor.help.motionlayout", "Display Help for Motion Layout",
    "If enabled, the assistant panel will display helpful guide on using Motion Layout.",
    true);

  public static final Flag<Boolean> NELE_NAV_EDITOR_ASSISTANT = Flag.create(
    ASSISTANT, "layout.editor.help.naveditor", "Display Help for Navigation Editor",
    "If enabled, the assistant panel will display helpful guide on using the Navigation Editor.",
    true);

  public static final Flag<Boolean> NELE_DRAG_PLACEHOLDER = Flag.create(
    NELE, "drag.placeholder", "Dragging widgets with Placeholders",
    "New architecture for dragging widgets in Layout Editor",
    true);

  public static final Flag<Boolean> NELE_PROPERTY_PANEL_ACTIONBAR = Flag.create(
    NELE, "property.panel.actionbar", "Property Panel Actionbar",
    "Support Actionbar in property panel",
    false);

  public static final Flag<Boolean> NELE_NEW_DEVICE_MENU = Flag.create(
    NELE, "new.device.menu", "New Device Menu in Layout Editor",
    "Use the new designed device menu to support device classes",
    true);

  public static final Flag<Boolean> NELE_VISUALIZATION_WINDOW_SIZE_MODE = Flag.create(
    NELE, "visualization.window.sizes", "Use Window Sizes Category in Layout Validation Tool",
    "Use Window Sizes as default group and replace the pixel devices category with it in Layout Validation Tool",
    true);

  public static final Flag<Boolean> NELE_VISUALIZATION_LOCALE_MODE = Flag.create(
    NELE, "visualization.locale", "Locale Mode in Layout Validation Tool",
    "Enable locale mode in Layout Validation Tool to preview layout in project's locales",
    true);

  public static final Flag<Boolean> NELE_VISUALIZATION_APPLY_CONFIG_TO_LAYOUT_EDITOR = Flag.create(
    NELE, "visualization.apply.config", "Apply Selected Configuration in Validation Tool to Layout Editor",
    "Apply the configuration to Layout Editor by double clicking the preview in Validation Tool",
    true);

  public static final Flag<Boolean> NELE_VISUALIZATION_MULTIPLE_CUSTOM = Flag.create(
    NELE, "visualization.multiple.custom", "Multiple Custom Categories in Layout Validation Tool",
    "Allow to create or delete multiple custom categories in Layout Validation Tool",
    false);

  public static final Flag<Boolean> NELE_SOURCE_CODE_EDITOR = Flag.create(
    NELE, "show.source.code.editor", "New Source Code Editor",
    "Enable new source code editor with preview(s) coming as a substitute to Compose and Custom View editors.",
    true);

  public static final Flag<Boolean> NELE_TOGGLE_TOOLS_ATTRIBUTES_IN_PREVIEW = Flag.create(
    NELE, "toggle.tools.attributes.preview", "New Toggle for Tools namespaces attributes",
    "Enable the new toggle in the Layout Editor. Allows toggling tools attributes in the Layout preview.",
    true);

  public static final Flag<Boolean> NELE_SHOW_RECYCLER_VIEW_SETUP_WIZARD = Flag.create(
    NELE, "recyclerview.setup.wizard", "Show setup wizard for recycler view",
    "When you right click recycler view in layout editor, you can now see \"Generate Adapter\" " +
    "that takes you through setup wizard",
    false);

  public static final Flag<Boolean> NELE_CUSTOM_SHORTCUT_KEYMAP = Flag.create(
    NELE, "custom.shortcut.keymap", "Design Tool Custom Shortcut",
    "Make the shortcuts of design tools configurable. The shortcut keymap can be changed in Preferences -> Keymap -> Android Design" +
    " Tools",
    true
  );

  public static final Flag<Boolean> NELE_LAYOUT_SCANNER_IN_EDITOR = Flag.create(
    NELE, "toggle.layout.editor.validator.a11y", "Toggle layout validator for layout editor.",
    "When the model changes, layout editor will run the series of layout validations and update lint output",
    true);

  public static final Flag<Boolean> NELE_LAYOUT_SCANNER_ADD_INCLUDE = Flag.create(
    NELE, "toggle.layout.editor.validator.a11y.include", "Toggle whether to show included layout or not.",
    "If the layout contains <include>, turning this flag on will run the scanner in the included layout.",
    false);

  public static final Flag<Boolean> NELE_LAYOUT_SCANNER_COMMON_ERROR_PANEL = Flag.create(
    NELE, "toggle.layout.editor.validator.a11y.common.panel", "Enable common error panel to display scanner results.",
    "If the xml layout contains atf results, it will be shown in the common error panel as well as issue panel.",
    false);

  public static final Flag<Boolean> NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS = Flag.create(
    NELE, "use.shared.issue.panel.for.design.tools", "Enabled shared issue panels",
    "Use a shared issue panel to display the issue for all design tools",
    false);

  public static final Flag<Boolean> NELE_SHOW_VISUAL_LINT_ISSUE_IN_COMMON_PROBLEMS_PANEL = Flag.create(
    NELE, "show.issue.in.common.panel", "Enable showing issues in common problems panel.",
    "If the xml layout contains any visual lint issues, it will be shown in the common error panel as well as in laytout validation issue panel.",
    false);

  public static final Flag<Boolean> NELE_VISUAL_LINT_ALWAYS_RUN = Flag.create(
    NELE, "visual.lint.always.run", "Run visual lint always",
    "Enable so that visual lint always runs for select configurations.",
    false);

  public static final Flag<Boolean> NELE_INCLUDE_QUALIFIERS_FOR_TRAFFIC_LIGHTS = Flag.create(
    NELE, "include.qualifiers.for.traffic.lights", "Include errors from qualifier variants in traffic lights",
    "Use errors from the current file and its qualifier variants to be displayed in the traffic light renderer",
    false
  );

  public static final Flag<Boolean> NELE_TRANSFORM_PANEL = Flag.create(
    NELE, "toggle.layout.editor.transform.panel", "Toggle transform panel in layout editor and motion editor.",
    "Enable the new transform panel in the layout editor and motion editor",
    true);

  public static final Flag<Boolean> NELE_TRANSITION_PANEL = Flag.create(
    NELE, "toggle.layout.editor.transition.panel", "Toggle transition panel in motion editor.",
    "Enable the new transition panel in the motion editor",
    true);

  public static final Flag<Boolean> NELE_ON_SWIPE_PANEL = Flag.create(
    NELE, "toggle.layout.editor.on.swipe.panel", "Toggle on swipe panel in motion editor.",
    "Enable the new on swipe panel in the motion editor",
    true);

  public static final Flag<Boolean> NELE_OVERLAY_PROVIDER = Flag.create(
    NELE, "toggle.overlay.provider.extension.point", "Toggle overlay provider extension point.",
    "Enable the overlay provider extension point",
    true);

  public static final Flag<Boolean> NELE_CLASS_BINARY_CACHE = Flag.create(
    NELE, "toggle.layout.editor.class.binary.cache", "Enable binary cache",
    "Enable binary cache of classes used in preview",
    true);

  public static final Flag<Boolean> NELE_STATE_LIST_PICKER = Flag.create(
    NELE, "state.list.picker", "Enable State List Picker",
    "Enable state list picker for selector drawable.",
    true);

  public static final Flag<Boolean> NELE_ASSET_REPOSITORY_INCLUDE_AARS_THROUGH_PROJECT_SYSTEM = Flag.create(
    NELE, "asset.repository.include.aars.through.project.system", "Include AARs through project system",
    "Include resource directories from AARs found through project system.",
    false);

  public static final Flag<Boolean> NELE_VISUAL_LINT = Flag.create(
    NELE, "visual.lint", "Enable visual linting for layouts",
    "Enable all the various tools related to visual linting of layouts.",
    true);

  public static final Flag<Boolean> NELE_VISUAL_LINT_TOGGLE_ISSUE_LAYOUTS = Flag.create(
    NELE, "visual.lint.toggle.issue.layouts", "Checkbox to show the the layouts with issue only.",
    "Provide a checkbox in Layout Validation Tool to allow user only show the layouts with issue detected by visual lint.",
    false);

  public static final Flag<Boolean> NELE_ATF_IN_VISUAL_LINT = Flag.create(
    NELE, "visual.lint.atf", "Enable ATF integration in visual linting for layouts",
    "Enable ATF integration in visual linting of layouts.",
    false);

  public static final Flag<Boolean> NELE_WARN_NEW_THREADS = Flag.create(
    NELE, "preview.warn.new.threads", "Enable new threads warning",
    "Display a warning if user code creates new threads in the preview",
    true);

  public static final Flag<Boolean> NELE_CLASS_PRELOADING_DIAGNOSTICS = Flag.create(
    NELE, "preview.class.preloading.diagnostics", "Enable class preloading overlay",
    "If enabled, the surface displays background class preloading progress",
    false);
  //endregion

  //region Navigation Editor
  private static final FlagGroup NAV_EDITOR = new FlagGroup(FLAGS, "nav", "Navigation Editor");
  public static final Flag<Boolean> NAV_SAFE_ARGS_SUPPORT = Flag.create(
    NAV_EDITOR, "safe.args.enabled", "Enable support for Safe Args",
    "Generate in-memory Safe Args classes if the current module is using the feature.",
    true);
  //endregion

  //region Resource Manager
  private static final FlagGroup RES_MANAGER = new FlagGroup(FLAGS, "res.manager", "Resource Manager");
  public static final Flag<Boolean> EXTENDED_TYPE_FILTERS = Flag.create(
    RES_MANAGER, "extended.filters", "Enable extended filters for resources",
    "Adds more filter options for resources based on the selected ResourceType. Includes options to filter by resource XML tag or "
    + "File extension.",
    true);

  public static final Flag<Boolean> NAVIGATION_PREVIEW = Flag.create(
    RES_MANAGER, "nav.preview", "Enable previews for Navigation resources",
    "Adds a visual preview to the Navigation resources in the Resource Manager. The preview corresponds to the start destination " +
    "of the graph.",
    true);
  //endregion

  //region Resource Repository
  private static final FlagGroup RESOURCE_REPOSITORY = new FlagGroup(FLAGS, "resource.repository", "Resource Repository");
  public static final Flag<Boolean> RESOURCE_REPOSITORY_TRACE_UPDATES = Flag.create(
    RESOURCE_REPOSITORY, "trace.updates", "Enable Tracing of Resource Repository Updates",
    "Enables tracing of resource repository updates",
    false);
  public static final Flag<Integer> RESOURCE_REPOSITORY_TRACE_SIZE = Flag.create(
    RESOURCE_REPOSITORY, "trace.size", "Maximum Size of Resource Repository Update Trace",
    "Size of the in-memory cyclic buffer used for tracing of resource repository updates",
    10000);
  //endregion

  //endregion

  //region Run/Debug
  private static final FlagGroup RUNDEBUG = new FlagGroup(FLAGS, "rundebug", "Run/Debug");
  public static final Flag<Boolean> RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED = Flag.create(
    RUNDEBUG, "logcat.console.output.enabled", "Show logcat process output in Run/Debug console window",
    "When running or debugging an Android process, output the logcat output of the process in the console window.",
    true);

  public static final Flag<Boolean> RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED = Flag.create(
    RUNDEBUG, "android.bundle.build.enabled", "Enable the Build Bundle action",
    "If enabled, the \"Build Bundle(s)\" menu item is enabled. " +
    "Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> DELTA_INSTALL = Flag.create(
    RUNDEBUG,
    "deltainstall",
    "Delta install",
    "Upon installing, if application is already on device, only send parts of the apks which have changed (the delta).",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_OPTIMISTIC_SWAP = Flag.create(
    RUNDEBUG,
    "applychanges.optimisticswap",
    "Use the 'Apply Changes 2.0' deployment pipeline",
    "Supports Install-without-Install, Speculative Diff and Structural Redefinition",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_OPTIMISTIC_RESOURCE_SWAP = Flag.create(
    RUNDEBUG,
    "applychanges.optimisticresourceswap",
    "Use the 'Apply Changes 2.0' deployment pipeline for full Apply Changes",
    "Requires applychanges.optimisticswap to be true.",
    true);

  public static final Flag<Boolean> NEW_EXECUTION_FLOW_ENABLED = Flag.create(
    RUNDEBUG, "android.new.execution.flow.enabled", "Enable new Execution flow",
    "If enabled, AS executes Run Configuration via new.AndroidRunProfileState",
    false);

  /**
   * Details: go/decouple-android-debuggers-from-android-configurations, "Android Java Debugger refactoring" section .
   */
  public static final Flag<Boolean> NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER = Flag.create(
    RUNDEBUG, "android.new.execution.flow.for.java.debugger.enabled", "Enable new Execution flow for debuggers",
    "If enabled, AS executes Run Java debugger via com.android.tools.idea.run.debug.StartJavaDebuggerKt.attachJavaDebuggerToClient",
    false);

  public static final Flag<Boolean> ALLOW_RUN_WEAR_CONFIGURATIONS_FROM_GUTTER = Flag.create(
    RUNDEBUG, "run.wear.configuration.gutter.enabled", "Run Wear Configurations from gutter",
    "If enabled, allow to Run Wear Configurations from the gutter.",
    true);

  /**
   * The level of APK change that will be supported by the deployment pipeline's optimistic
   * "deploy-without-installing" path. Deploying changes that exceed the level of support
   * configured here will cause the deployment to install via the package manager.
   */
  public enum OptimisticInstallSupportLevel {
    /** Always fall back to a package manager installation. */
    DISABLED,
    /** Support deploying changes to dex files only. */
    DEX,
    /** Support deploying changes to dex files and native libraries only. */
    DEX_AND_NATIVE,
    /** Support deploying changes to dex files, native libraries, and resources. */
    DEX_AND_NATIVE_AND_RESOURCES,
  }

  public static final Flag<OptimisticInstallSupportLevel> OPTIMISTIC_INSTALL_SUPPORT_LEVEL = Flag.create(
    RUNDEBUG,
    "optimisticinstall.supportlevel",
    "The amount of support for using the 'Apply Changes 2.0' pipeline on Run.",
    "This can be \"DISABLED\" to always use a package manager installation; \"DEX\" to use the pipeline for dex-only changes;" +
        " \"DEX_AND_NATIVE\" to use the pipeline for dex and native library-only changes;" +
        " or \"DEX_AND_NATIVE_AND_RESOURCES\" to use the pipeline for changes to dex, native libraries, and/or resource/asset files." +
        " Deploying changes that exceed the level of support configured here will cause the deployment to install via the package manager.",
    OptimisticInstallSupportLevel.DEX);

  public static final Flag<Boolean> APPLY_CHANGES_STRUCTURAL_DEFINITION = Flag.create(
    RUNDEBUG,
    "applychanges.structuralredefinition",
    "Use ART's new structural redefinition extension for Apply Changes.",
    "Requires applychanges.optimisticswap to be true.",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_VARIABLE_REINITIALIZATION = Flag.create(
    RUNDEBUG,
    "applychanges.variablereinitialization",
    "Use ART's new variable reinitializaiton extension for Apply Changes.",
    "Requires applychanges.structuralredefinition to be true.",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_FAST_RESTART_ON_SWAP_FAIL = Flag.create(
    RUNDEBUG,
    "applychanges.swap.fastrestartonswapfail",
    "Allow fast restart on swap failure.",
    "Eliminate the need to build again when auto re-run checkbox is turned on.",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_KEEP_CONNECTION_ALIVE = Flag.create(
    RUNDEBUG,
    "applychanges.connection.keepalive",
    "Keep connection to device alive.",
    "Eliminate the cost of opening a connection and spawning a process when using Apply Changes.",
    true);

  public static final Flag<Boolean> SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED = Flag.create(
    RUNDEBUG,
    "select.device.snapshot.combo.box.snapshots.enabled",
    "Enable Select Device/Snapshot combo box snapshots",
    "So the new Instant Run can use the combo box",
    true);

  public static final Flag<Boolean> ADB_CONNECTION_STATUS_WIDGET_ENABLED = Flag.create(
    RUNDEBUG,
    "adb.connection.status.widget.enabled",
    "Enable and Show ADB Connection Widget",
    "Enables and shows the ADB connection status widget in the status bar",
    false);

  public static final Flag<Boolean> ADB_WIRELESS_PAIRING_ENABLED = Flag.create(
    RUNDEBUG,
    "adb.wireless.enabled",
    "Enable pairing devices through ADB wireless",
    "Allow pairing new physical device through QR Code pairing via ADB wireless",
    true);

  public static final Flag<Boolean> ADB_SERVER_MANAGEMENT_MODE_SETTINGS_VISIBLE = Flag.create(
    RUNDEBUG,
    "adb.server.management.mode.settings.visible",
    "Show ADB server management mode settings",
    "To allow toggling between automatic or user managed ADB server mode.",
    false);

  public static final Flag<Boolean> ADBLIB_MIGRATION_DEVICE_EXPLORER = Flag.create(
    RUNDEBUG,
    "adblib.migration.device.explorer",
    "Use adblib in Device Explorer",
    "Use adblib instead of ddmlib for Device Explorer",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_WIFI_PAIRING = Flag.create(
    RUNDEBUG,
    "adblib.migration.wifi.pairing",
    "Use adblib in Pair Device over Wi-Fi",
    "Use adblib instead of ddmlib for Pair Device over Wi-Fi",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_LOGCAT_V2 = Flag.create(
    RUNDEBUG,
    "adblib.migration.logcatv2",
    "Use adblib in the logcat v2 toolwindow",
    "Use adblib instead of ddmlib in the logcat v2 toolwindow",
    true);

  public static final Flag<Boolean> SUPPORT_FEATURE_ON_FEATURE_DEPS = Flag.create(
    RUNDEBUG,
    "feature.on.feature",
    "Enable feature-on-feature dependencies",
    "Enables Studio to understand feature-on-feature dependencies when launching dynamic apps.",
    false
  );

  public static final Flag<Boolean> COROUTINE_DEBUGGER_ENABLE = Flag.create(
    RUNDEBUG,
    "coroutine.debugger.enable",
    "Enable Coroutine Debugger",
    "Enables the Coroutine Debugger, that shows up as a panel in the debugger when debugging an app that uses coroutines",
    true
  );

  public static final Flag<Boolean> LOGCAT_EXPRESSION_FILTER_ENABLE = Flag.create(
    RUNDEBUG,
    "logcat.expression.filter.enable",
    "Enable expression filter in Logcat",
    "Enables the expression filter in Logcat",
    false
  );

  public static final Flag<Boolean> LOGCAT_SUPPRESSED_TAGS_ENABLE = Flag.create(
    RUNDEBUG,
    "logcat.suppressed.tags.enable",
    "Enable Suppressed Tags Dialog in Logcat",
    "Enables a dialog that allows the user to maintain a global set of tags to be suppressed in Logcat",
    false
  );

  public static final Flag<Boolean> LOGCAT_V2_ENABLE = Flag.create(
    RUNDEBUG,
    "logcat.v2.enable",
    "Enable Logcat V2",
    "Enables version 2 of the Logcat tool window",
    true
  );

  public static final Flag<Boolean> LOGCAT_V2_NAMED_FILTERS_ENABLE = Flag.create(
    RUNDEBUG,
    "logcat.v2.named.filters.enable",
    "Enable Logcat named filters feature",
    "Enables the named filters feature in the Logcat tool window",
    false
  );
  //endregion

  //region Gradle Project System
  private static final FlagGroup GRADLE_IDE = new FlagGroup(FLAGS, "gradle.ide", "Gradle Project System");
  public static final Flag<Boolean> FIX_ANDROID_RUN_CONFIGURATIONS_ENABLED = Flag.create(
    GRADLE_IDE, "gradle.run.configuration.fix.enabled",
    "Check Android Run Configurations contains the \"Gradle-aware Make\" task and fix them",
    "When a project is loaded, automatically add a \"Gradle-aware Make\" task to each Run Configuration if the task is missing",
    true);

  public static final Flag<Boolean> USE_DEVELOPMENT_OFFLINE_REPOS = Flag.create(
    GRADLE_IDE, "development.offline.repos", "Enable development offline repositories",
    "Makes Gradle use development offline repositories such as /out/repo", StudioPathManager.isRunningFromSources());
  public static final Flag<Boolean> BUILD_ANALYZER_JETIFIER_ENABLED = Flag.create(
    GRADLE_IDE, "build.analyzer.jetifier.warning", "Enable Jetifier usage analyzis",
    "Enable Jetifier usage analyzis is Build Analyzer.", true);
  public static final Flag<Boolean> DISABLE_FORCED_UPGRADES = Flag.create(
    GRADLE_IDE, "forced.agp.update", "Disable forced Android Gradle plugin upgrades",
    "This option is only respected when running Android Studio internally.", false);
  public static final Flag<Boolean> USE_MODULE_PER_SOURCE_SET = Flag.create(
    GRADLE_IDE, "module.per.source.set", "Enables creating multiple modules per Gradle project",
    "This allows the IDE to more closely represent how the project is configured in Gradle.", true);

  public static final Flag<Boolean> GRADLE_SYNC_PARALLEL_SYNC_ENABLED = Flag.create(
    GRADLE_IDE, "gradle.sync.parallel.sync.enabled", "Enables parallel sync",
    "This allows the IDE to fetch models in parallel (if supported by Gradle and enabled via org.gradle.parallel=true).", false);

  public static final Flag<Boolean> GRADLE_SYNC_PARALLEL_SYNC_PREFETCH_VARIANTS = Flag.create(
    GRADLE_IDE, "gradle.sync.parallel.sync.prefetch.variants", "Enables speculative syncing of current variants",
    "This allows the IDE to pre-fetch models for the currently selected variants in parallel before resolving the " +
    "new variant selection (which is less parallelizable process).", false);

  public static final Flag<Boolean> GRADLE_SYNC_ENABLE_CACHED_VARIANTS = Flag.create(
    GRADLE_IDE, "gradle.sync.enable.cached.variants", "Enables caching of build variants",
    "Enables caching of build variant data so that the IDE does not always run Gradle when switching between build variants. " +
    "While faster this mode may be incompatible with some plugins.", true);

  public static final Flag<Boolean> ALLOW_DIFFERENT_JDK_VERSION = Flag.create(
    GRADLE_IDE, "jdk.allow.different", "Allow different Gradle JDK", "Allow usage of a different JDK version when running Gradle.", true);

  public static final Flag<Boolean> GRADLE_SYNC_USE_V2_MODEL = Flag.create(
    GRADLE_IDE, "gradle.sync.use.v2", "Use V2 Builder models", "Enable fetching V2 builder models from AGP when syncing.", true);

  public static final Flag<Boolean> GRADLE_SYNC_RECREATE_JDK = Flag.create(
    GRADLE_IDE, "gradle.sync.recreate.jdk", "Recreate JDK on sync", "Recreate Gradle JDK when syncing if there are changed roots.", true);

  public static final Flag<Boolean> GRADLE_DSL_TOML_SUPPORT = Flag.create(
    GRADLE_IDE, "gradle.dsl.toml", "Parse TOML files", "Parse TOML files to support use of Version Catalogs.", false);

  //endregion

  //region Database Inspector
  private static final FlagGroup DATABASE_INSPECTOR = new FlagGroup(FLAGS, "database.inspector", "Database Inspector");
  public static final Flag<Boolean> DATABASE_INSPECTOR_OPEN_FILES_ENABLED = Flag.create(
    DATABASE_INSPECTOR,
    "open.files.enabled",
    "Enable support for opening SQLite files in Database Inspector",
    "If enabled, the Database Inspector tool will be able to open SQLite files." +
    "eg. SQLite files opened from the Device Explorer will open in the inspector.",
    false
  );
  //endregion

  //region Layout Inspector
  private static final FlagGroup LAYOUT_INSPECTOR = new FlagGroup(FLAGS, "layout.inspector", "Layout Inspector");
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_USE_DEVBUILD_SKIA_SERVER = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.devbuild.skia", "Use the locally-built skia rendering server",
    "If enabled and this is a locally-built studio instance, use the locally-built skia server instead of one from the SDK.", false);
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_COUNTS = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.enable.recomposition.counts", "Enable recomposition counts",
    "Enable gathering and display of recomposition counts in the layout inspector.", true);
  //endregion

  //region Embedded Emulator
  private static final FlagGroup EMBEDDED_EMULATOR = new FlagGroup(FLAGS, "embedded.emulator", "Embedded Emulator");
  public static final Flag<Boolean> EMBEDDED_EMULATOR_SCREENSHOT_STATISTICS = Flag.create(
    EMBEDDED_EMULATOR, "screenshot.statistics", "Enable Collection of Screenshot Statistics",
    "Captures statistics of received Emulator screenshots",
    false);
  public static final Flag<Integer> EMBEDDED_EMULATOR_STATISTICS_INTERVAL_SECONDS = Flag.create(
    EMBEDDED_EMULATOR, "screenshot.statistics.interval", "Aggregation Interval for Screenshot Statistics",
    "Aggregation interval in seconds for statistics of received Emulator screenshots",
    120);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_GRPC_CALLS = Flag.create(
    EMBEDDED_EMULATOR, "trace.grpc.calls", "Enable Emulator gRPC Tracing",
    "Enables tracing of most Emulator gRPC calls",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS = Flag.create(
    EMBEDDED_EMULATOR, "trace.high.volume.grpc.calls", "Enable High Volume Emulator gRPC Tracing",
    "Enables tracing of high volume Emulator gRPC calls",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_SCREENSHOTS = Flag.create(
    EMBEDDED_EMULATOR, "trace.screenshots", "Enable Emulator Screenshot Tracing",
    "Enables tracing of received Emulator screenshots",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS = Flag.create(
    EMBEDDED_EMULATOR, "trace.notifications", "Enable Emulator Notification Tracing",
    "Enables tracing of received Emulator notifications",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_DISCOVERY = Flag.create(
    EMBEDDED_EMULATOR, "trace.discovery", "Enable Tracing of Emulator Discovery",
    "Enables tracing of Emulator discovery",
    false);
  //endregion

  //region Device Mirroring
  private static final FlagGroup DEVICE_MIRRORING = new FlagGroup(FLAGS, "device.mirroring", "Device Mirroring");
  public static final Flag<Boolean> DEVICE_MIRRORING_ENABLED = Flag.create(
    DEVICE_MIRRORING, "enabled", "Enable Mirroring of Physical Devices",
    "Mirrors displays of connected physical devices",
    false);
  public static final Flag<Boolean> DEVICE_MIRRORING_STANDALONE_EMULATORS = Flag.create(
    DEVICE_MIRRORING, "allow.standalone.emulators", "Allow Mirroring of Standalone Emulators",
    "Treats standalone emulators the same as physical devices for the purpose of display mirroring",
    false);
  //endregion

  //region Refactorings
  private static final FlagGroup REFACTORINGS = new FlagGroup(FLAGS, "refactor", "Refactor menu");

  public static final Flag<Boolean> MIGRATE_TO_RESOURCE_NAMESPACES_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.resourcenamespaces.enabled", "Enable the Migrate to Resource Namespaces refactoring",
    "If enabled, show the action in the refactoring menu", false);

  public static final Flag<Boolean> MIGRATE_TO_NON_TRANSITIVE_R_CLASSES_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.nontransitiverclasses.enabled", "Enable the Migrate to non-transitive R classes refactoring",
    "If enabled, show the action in the refactoring menu", true);

  public static final Flag<Boolean> INFER_ANNOTATIONS_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "infer.annotations.enabled", "Enable the Infer Annotations refactoring",
    "If enabled, show the action in the refactoring menu", false);
  //endregion

  //region NDK
  private static final FlagGroup NDK = new FlagGroup(FLAGS, "ndk", "Native code features");
  public static final Flag<Boolean> CMAKE_ENABLE_FEATURES_FROM_CLION = Flag.create(
    NDK, "cmakeclionfeatures", "Enable CMake language support from CLion",
    "If enabled, language support features (e.g. syntax highlighting) currently present in CLion will be turned on.", true);

  public static final Flag<Boolean> APK_DEBUG_BUILD_ID_CHECK = Flag.create(
    NDK, "apkdebugbuildidcheck", "Enable build ID check in APK debugging",
    "If enabled, the build ID of user-provided symbol files are compared against the binaries inside the APK.", true);

  public static final Flag<Boolean> APK_DEBUG_RELOAD = Flag.create(
    NDK, "apkdebugreload", "Enable APK reloading feature",
    "If enabled, the user will be provided with an option to reload the APK inside an APK debugging project", true);

  private static final FlagGroup NDK_SIDE_BY_SIDE = new FlagGroup(FLAGS, "ndk.sxs", "NDK Side by Side");
  public static final Flag<Boolean> NDK_SIDE_BY_SIDE_ENABLED = Flag.create(
    NDK_SIDE_BY_SIDE, "ndk.sxs.enabled", "Enable side by side NDK support",
    "If enabled, C/C++ projects will have NDK side by side support",
    true);

  public static final Flag<Boolean> USE_CONTENT_ROOTS_FOR_NATIVE_PROJECT_VIEW = Flag.create(
    NDK, "use.content.roots.for.native.project.view", "Use content roots for native project view",
    "If enabled, the C/C++ content roots are displayed in Android View and Project View. Otherwise, each individual native target " +
    "is displayed.",
    true);

  public static final Flag<Boolean> ENABLE_SHOW_FILES_UNKNOWN_TO_CMAKE = Flag.create(
    NDK, "ndk.projectview.showfilessunknowntocmake", "Enable option to show files unknown to CMake",
    "If enabled, for projects using CMake, Android project view menu would show an option to `Show Files Unknown To CMake`.",
    true
  );

  // b/202709703: Disable jb_formatters (which is used to pull Natvis) temporarily, because
  // the latest changes in cidr-debugger cause the jb_formatters to conflict with the
  // built-in lldb formatters.
  public static final Flag<Boolean> ENABLE_LLDB_NATVIS = Flag.create(
    NDK, "lldb.natvis", "Use NatVis visualizers in native debugger",
    "If enabled, native debugger formats variables using NatVis files found in the project.",
    false
  );
  //endregion

  //region Editor
  private static final FlagGroup EDITOR = new FlagGroup(FLAGS, "editor", "Editor features");

  public static final Flag<Boolean> COLLAPSE_ANDROID_NAMESPACE = Flag.create(
    EDITOR,
    "collapse.android.namespace",
    "Collapse the android namespace in XML code completion",
    "If enabled, XML code completion doesn't include resources from the android namespace. Instead a fake completion item " +
    "is used to offer just the namespace prefix.", true);

  public static final Flag<Boolean> AGSL_LANGUAGE_SUPPORT = Flag.create(
    EDITOR, "agsl.support.enabled",
    "Enable editor support for AGSL (Android Graphics Shading Language)",
    "If enabled, it offers basic editor support (syntax highlighting and basic validation) for AGSL",
    true
  );

  public static final Flag<Boolean> ADVANCED_JNI_ASSISTANCE = Flag.create(
    EDITOR, "advanced.jni.assistance",
    "Enable advanced JNI assistance",
    "If enabled, additional inspection, completion, and refactoring supports are provided related to JNI. If disabled, some " +
    "inspections related to JNI may stop working.",
    true
  );

  public static final Flag<Boolean> CUSTOM_JAVA_NEW_CLASS_DIALOG = Flag.create(
    EDITOR, "custom.new.class.dialog",
    "Enable custom New Class dialog",
    "If enabled, our custom UI for creating a new Java class is used. Otherwise the platform default is used.",
    false
  );

  public static final Flag<Boolean> TWEAK_COLOR_SCHEME = Flag.create(
    EDITOR, "tweak.color.scheme",
    "Change the default color scheme",
    "If enabled, we modify the default color scheme slightly.",
    true
  );

  public static final Flag<Boolean> SAMPLES_SUPPORT_ENABLED = Flag.create(
    EDITOR, "samples.support.enabled",
    "Enable supports of samples (tag inside KDoc) that are used in quick documentation",
    "Enable supports of samples (tag inside KDoc) that are used in quick documentation",
    false
  );

  public static final Flag<Boolean> DAGGER_SUPPORT_ENABLED = Flag.create(
    EDITOR, "dagger.support.enabled",
    "Enable editor support for Dagger",
    "If enabled adds Dagger specific find usages, gutter icons and new parsing for Dagger errors",
    true
  );

  //endregion

  //region Unified App Bundle
  private static final FlagGroup UAB = new FlagGroup(FLAGS, "uab", "Unified App Bundle");

  public static final Flag<Boolean> UAB_ENABLE_NEW_INSTANT_APP_RUN_CONFIGURATIONS = Flag.create(
    UAB, "enable.ia.run.configs", "Enable new instant app run configuration options",
    "If enabled, shows the new instant app deploy checkbox in the run configuration dialog and allows new instant app deploy workflow.",
    true
  );
  //endregion

  //region Testing
  private static final FlagGroup TESTING = new FlagGroup(FLAGS, "testing", "Testing support");

  public static final Flag<Boolean> PRINT_INSTRUMENTATION_STATUS = Flag.create(
    TESTING, "print.instrumentation.status", "Print instrumentation status information when testing",
    "If enabled, instrumentation output keys (from calling Instrumentation#sendStatus) that begin with 'android.studio.display.' "
    + "will have their values printed after a test has finished running.",
    true
  );

  public static final Flag<Boolean> UTP_TEST_RESULT_SUPPORT = Flag.create(
    TESTING, "utp.instrumentation.tests", "Allow importing UTP test results.",
    "If enabled, you can import UTP test results and display them in test result panel.",
    true
  );

  public static final Flag<Boolean> UTP_INSTRUMENTATION_TESTING = Flag.create(
    TESTING, "utp.instrumentation.testing", "Run instrumentation tests via UTP",
    "If enabled, a checkbox to opt-in to running instrumentation tests via UTP feature is displayed in the settings.",
    true
  );
  //endregion

  //region Memory
  private static final FlagGroup MEMORY_SETTINGS = new FlagGroup(FLAGS, "memory.settings", "Memory Settings");
  public static final Flag<Boolean> LOW_IDE_XMX_CAP = Flag.create(
    MEMORY_SETTINGS, "low.ide.xmx.cap", "Set low IDE Xmx cap in memory settings",
    "If set, IDE Xmx is capped at 4GB in the configuration dialog. Otherwise, the cap is 8GB",
    true);
  //endregion

  //region System Health
  private static final FlagGroup SYSTEM_HEALTH = new FlagGroup(FLAGS, "system.health", "System Health");
  public static final Flag<Boolean> WINDOWS_UCRT_CHECK_ENABLED = Flag.create(
    SYSTEM_HEALTH, "windows.ucrt.check.enabled", "Enable Universal C Runtime system health check",
    "If enabled, a notification will be shown if the Universal C Runtime in Windows is not installed",
    false);

  public static final Flag<Boolean> ANTIVIRUS_NOTIFICATION_ENABLED = Flag.create(
    SYSTEM_HEALTH, "antivirus.notification.enabled", "Enable antivirus system health check",
    "If enabled, a notification will be shown if antivirus realtime scanning is enabled and directories relevant to build performance aren't excluded",
    true);

  public static final Flag<Boolean> ANTIVIRUS_METRICS_ENABLED = Flag.create(
    SYSTEM_HEALTH, "antivirus.metrics.enabled", "Enable antivirus metrics collection",
    "If enabled, metrics about the status of antivirus realtime scanning and excluded directories will be collected",
    true);

  public static final Flag<Boolean> ANTIVIRUS_CHECK_USE_REGISTRY = Flag.create(
    SYSTEM_HEALTH, "antivirus.check.registry", "Use registry instead of PowerShell for checking antivirus status",
    "If enabled, the antivirus status checker will use the Windows registry instead of PowerShell commands",
    true);

  //endregion

  //region Compose
  private static final FlagGroup COMPOSE = new FlagGroup(FLAGS, "compose", "Compose");

  public static final Flag<Boolean> COMPOSE_PREVIEW_RUN_CONFIGURATION = Flag.create(
    COMPOSE, "preview.run.configuration", "Enable running Compose Previews on device/emulator",
    "If enabled, it will be possible to create run configurations that launch a Compose Preview directly to the device/emulator.",
    true);

  public static final Flag<Boolean> COMPOSE_PREVIEW_DOUBLE_RENDER = Flag.create(
    COMPOSE, "preview.double.render", "Enable the Compose double render mode",
    "If enabled, preview components will be rendered twice so components depending on a recompose (like tableDecoration) " +
    "render correctly.",
    false);

  public static final Flag<Boolean> COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE = Flag.create(
    COMPOSE, "preview.scroll.on.caret.move", "Enable the Compose Preview scrolling when the caret moves",
    "If enabled, when moving the caret in the text editor, the Preview will show the preview currently under the cursor.",
    true);

  public static final Flag<Boolean> COMPOSE_PREVIEW_INTERRUPTIBLE = Flag.create(
    COMPOSE, "preview.interruptible", "Allows the Compose Preview to interrupt rendering calls",
    "If enabled, if a render takes too long, the preview will be able to interrupt the execution.",
    true);

  public static final Flag<Boolean> COMPOSE_EDITOR_SUPPORT = Flag.create(
    COMPOSE, "editor",
    "Compose-specific support in the code editor",
    "Controls whether Compose-specific editor features, like completion tweaks, are enabled. This flag has priority over " +
    "all flags in the `compose.editor.*` namespace.",
    true
  );

  public static final Flag<Boolean> COMPOSE_COMPLETION_PRESENTATION = Flag.create(
    COMPOSE, "editor.completion.presentation",
    "Custom presentation for code completion items for composable functions",
    "If enabled, code completion items for composable functions use a custom presentation (icon, text).",
    true
  );

  public static final Flag<Boolean> COMPOSE_COMPLETION_WEIGHER = Flag.create(
    COMPOSE, "editor.completion.weigher",
    "Custom weigher for Compose",
    "If enabled, code completion puts composable functions above other completion suggestions.",
    true
  );

  public static final Flag<Boolean> COMPOSE_COMPLETION_INSERT_HANDLER = Flag.create(
    COMPOSE, "editor.completion.insert.handler",
    "Custom insert handler for composable functions",
    "If enabled, code completion for composable functions uses a custom InsertHandler that inserts required parameter names.",
    true
  );

  public static final Flag<Boolean> COMPOSE_CONSTRAINTLAYOUT_COMPLETION = Flag.create(
    COMPOSE, "editor.completion.constraintlayout.json",
    "Completion for ConstraintLayout JSON syntax",
    "If enabled, code completion will be abailable for the JSON syntax of Compose ConstraintLayout.",
    true
  );

  public static final Flag<Boolean> COMPOSE_AUTO_DOCUMENTATION = Flag.create(
    COMPOSE, "editor.auto.documentation",
    "Show quick documentation automatically for Compose",
    "If enabled, during code completion popup with documentation shows automatically",
    true
  );

  public static final Flag<Boolean> COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION = Flag.create(
    COMPOSE, "editor.render.sample",
    "Render samples of compose elements inside documentation",
    "If enabled, adds rendered image of sample for compose element if such exists",
    false
  );

  public static final Flag<Boolean> COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION_SLOW = Flag.create(
    COMPOSE, "editor.render.sample.slow",
    "Slow down rendering of samples of compose elements inside documentation",
    "If enabled, slow down rendering of samples of compose elements inside documentation, this flag is used for demonstration of non-blocking behavior",
    false
  );

  public static final Flag<Boolean> COMPOSE_FUNCTION_EXTRACTION = Flag.create(
    COMPOSE, "editor.function.extraction",
    "Enables extracting @Composable function from other composables",
    "If enabled, function extracted from @Composable function will annotated @Composable",
    true
  );

  public static final Flag<Boolean> COMPOSE_WIZARD_TEMPLATES = Flag.create(
    COMPOSE, "wizard.templates",
    "Show Compose Wizards",
    "If enabled, allows adding new Compose Projects/Modules/Activities through the wizards",
    true
  );

  public static final Flag<Boolean> COMPOSE_ANIMATED_PREVIEW = Flag.create(
    COMPOSE, "preview.animated.enable",
    "Enable animated compose preview",
    "If enabled, a user can switch compose preview to be animated",
    true
  );

  public static final Flag<Boolean> COMPOSE_ANIMATED_PREVIEW_SHOW_CLICK = Flag.create(
    COMPOSE, "preview.animated.click.enable",
    "Enable displaying clicks on the animated preview",
    "If enabled, clicking on the animated preview will generate a ripple",
    true
  );

  public static final Flag<Boolean> COMPOSE_ANIMATION_INSPECTOR = Flag.create(
    COMPOSE, "preview.animation.inspector",
    "Enable compose preview animation inspection",
    "If enabled, users can inspect animations in compose previews, e.g. play/pause and jump to specific frame",
    true
  );

  public static final Flag<Boolean> COMPOSE_ANIMATION_PREVIEW_LABEL_INSPECTION = Flag.create(
    COMPOSE, "preview.animation.label.inspection",
    "Enable 'label' parameter inspection of Compose animations PropKeys",
    "If enabled, show a warning when the 'label' parameter of Compose animations PropKeys are not set.",
    true
  );

  public static final Flag<Boolean> COMPOSE_LIVE_LITERALS = Flag.create(
    COMPOSE, "preview.live.literals",
    "Enable the live literals",
    "If enabled, the live literals feature is enabled",
    true
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_LITERALS = Flag.create(
    COMPOSE, "deploy.live.literals.deploy",
    "Enable live literals deploy",
    "If enabled, literals within Composable functions are instantly deployed to device",
    true
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT = Flag.create(
    COMPOSE, "deploy.live.edit.deploy",
    "Enable live edits deploy",
    "If enabled, edits within Kotlin and Composable functions are instantly deployed to device",
    false
  );

  public static final Flag<Integer> COMPOSE_LIVE_LITERALS_UPDATE_RATE = Flag.create(
    COMPOSE, "deploy.live.literals.updaterate",
    "Update rate of live literals edits",
    "The rate of which live literals are updated in milliseconds",
    50
  );

  public static final Flag<Boolean> COMPOSE_DEBUG_BOUNDS = Flag.create(
    COMPOSE, "preview.debug.bounds",
    "Enable the debug bounds switch controls",
    "If enabled, the user can enable/disable the painting of debug bounds",
    false
  );

  public static final Flag<Boolean> COMPOSE_PREVIEW_ELEMENT_PICKER = Flag.create(
    COMPOSE, "preview.element.picker.enable",
    "Enable @Preview picker",
    "If enabled, the picker for @Preview elements will be available",
    true
  );

  public static final Flag<Boolean> COMPOSE_BLUEPRINT_MODE = Flag.create(
    COMPOSE, "preview.blueprint",
    "Enable the blueprint mode for Compose previews",
    "If enabled, the user can change the mode of Compose previews, between design and blueprint mode",
    false
  );

  public static final Flag<Boolean> COMPOSE_QUICK_ANIMATED_PREVIEW = Flag.create(
    COMPOSE, "preview.animated.quick",
    "Speed up transition between static and animated compose previews",
    "If enabled, a transition between static and animated compose preview is almost instant",
    true
  );

  public static final Flag<Boolean> COMPOSE_COLORBLIND_MODE = Flag.create(
    COMPOSE, "preview.colorblind",
    "Enable the colorblind mode for Compose previews",
    "If enabled, the user can change the mode of Compose previews, between different types of colorblind modes",
    true
  );

  public static final Flag<Boolean> COMPOSE_PIN_PREVIEW = Flag.create(
    COMPOSE, "preview.pin.enable",
    "Enable pinning compose previews",
    "If enabled, a user can pin a preview",
    false
  );

  public static final Flag<Boolean> COMPOSE_CONSTRAINT_VISUALIZATION = Flag.create(
    COMPOSE, "constraint.visualization",
    "Enable ConstraintLayout visualization in Compose previews",
    "If enabled, constraints from a ConstraintLayout composable will be shown in the preview",
    true
  );

  public static final Flag<Boolean> COMPOSE_INDIVIDUAL_PIN_PREVIEW = Flag.create(
    COMPOSE, "preview.individual.pin.enable",
    "Enable pinning of individual compose previews",
    "If enabled, a user can pin a single preview within a file",
    false
  );

  public static final Flag<Integer> COMPOSE_INTERACTIVE_FPS_LIMIT = Flag.create(
    COMPOSE, "preview.interactive.fps.limit",
    "Interactive Preview FPS limit",
    "Controls the maximum number of frames per second in Compose Interactive Preview",
    30
  );

  public static final Flag<Boolean> COMPOSE_CLASSLOADERS_PRELOADING = Flag.create(
    COMPOSE, "preview.classloaders.preloading",
    "Enable background classes preloading",
    "If enabled, a background classes preloading will happen to speed-up preview ClassLoader warm-up",
    true
  );

  public static final Flag<Boolean> COMPOSE_STATE_OBJECT_CUSTOM_RENDERER = Flag.create(
    COMPOSE, "custom.renderer.for.compose.state.objects",
    "Enable custom renderers for compose state objects",
    "If enabled, a given compose 'StateObject' type object will be rendered by the corresponding custom renderer",
    true
  );

  public static final Flag<Boolean> COMPOSE_INTERACTIVE_ANIMATION_CURVES = Flag.create(
    COMPOSE, "preview.animation.curves",
    "Enable animation curves in Animation Inspector",
    "If enabled, animation curves will be rendered in Animation Inspector timeline.",
    true
  );

  public static final Flag<Boolean> COMPOSE_ANIMATION_PREVIEW_COORDINATION = Flag.create(
    COMPOSE, "preview.animation.coordination",
    "Enable animation coordination in Animation Inspector",
    "If enabled, animation coordination will be available in Animation Inspector timeline.",
    false
  );

  public static final Flag<Boolean> COMPOSE_FAST_PREVIEW = Flag.create(
    COMPOSE, "preview.fast.reload.enabled", "Enable the Compose fast-reload preview",
    "If enabled, the preview enabled the fast-reload feature.",
    false);

  public static final Flag<Boolean> COMPOSE_FAST_PREVIEW_DAEMON_DEBUG = Flag.create(
    COMPOSE, "preview.fast.reload.debug.daemon", "Starts the Live Edit daemon in debug mode",
    "If enabled, the compiler daemon will wait for a debugger to be attached.",
    false);

  public static final Flag<Boolean> COMPOSE_FAST_PREVIEW_USE_IN_PROCESS_DAEMON = Flag.create(
    COMPOSE, "preview.fast.reload.in.process.enabled", "Enable the Compose fast-reload in-process compiler",
    "If enabled, the preview will use the in-process compiler for compiling.",
    true);

  public static final Flag<Boolean> COMPOSE_MULTIPREVIEW = Flag.create(
    COMPOSE, "preview.multipreview.enabled", "Enable Compose Multipreview",
    "If enabled, annotation classes annotated with Preview, and its usages, will be considered when finding Previews in a file",
    false);
  //endregion

  // region App Inspection
  private static final FlagGroup APP_INSPECTION = new FlagGroup(FLAGS, "appinspection", "App Inspection");
  public static final Flag<Boolean> ENABLE_APP_INSPECTION_TOOL_WINDOW = Flag.create(
    APP_INSPECTION, "enable.tool.window", "Enable App Inspection Tool Window",
    "Enables the top-level App Inspection tool window, which will contain tabs to various feature inspectors",
    true
  );

  public static final Flag<Boolean> APP_INSPECTION_USE_DEV_JAR = Flag.create(
    APP_INSPECTION, "use.dev.jar", "Use a precompiled, prebuilt inspector jar",
    "If enabled, grab inspector jars from prebuilt locations, skipping over version checking and dynamic resolving of " +
    "inspector artifacts from maven. This is useful for devs who want to load locally built inspectors.",
    false
  );

  public static final Flag<Boolean> APP_INSPECTION_USE_SNAPSHOT_JAR = Flag.create(
    APP_INSPECTION, "use.snapshot.jar", "Always extract latest inspector jar from library",
    "If enabled, override normal inspector resolution logic, instead searching the IDE cache directly. This allows finding " +
    "inspectors bundled in local, snapshot builds of Android libraries, as opposed to those released through the normal process on maven.",
    false
  );

  public static final Flag<Boolean> COMPOSE_USE_LOADER_WITH_AFFINITY = Flag.create(
    COMPOSE, "preview.loader.affinity", "Enable the class loading affinity.",
    "If enabled, the class loading will cache which class loaders are more likely to have the class.",
    true);
  // endregion

  // region WorkManager Inspector
  private static final FlagGroup WORK_MANAGER_INSPECTOR = new FlagGroup(FLAGS, "work.inspector", "WorkManager Inspector");
  public static final Flag<Boolean> ENABLE_WORK_MANAGER_INSPECTOR_TAB = Flag.create(
    WORK_MANAGER_INSPECTOR, "enable.tab", "Enable WorkManager Inspector Tab",
    "Enables a WorkManager Inspector Tab in the App Inspection tool window",
    true
  );

  public static final Flag<Boolean> ENABLE_WORK_MANAGER_GRAPH_VIEW = Flag.create(
    WORK_MANAGER_INSPECTOR, "enable.graph.view", "Enable WorkManager Graph View",
    "Enables a Graph View for visualizing work dependencies in the WorkManager Inspector Tab",
    true
  );
  // endregion

  // region Network Inspector
  private static final FlagGroup NETWORK_INSPECTOR = new FlagGroup(FLAGS, "network.inspector", "Network Inspector");
  public static final Flag<Boolean> ENABLE_NETWORK_MANAGER_INSPECTOR_TAB = Flag.create(
    NETWORK_INSPECTOR, "enable.network.inspector.tab", "Enable Network Inspector Tab",
    "Enables a Network Inspector Tab in the App Inspection tool window",
    true
  );
  public static final Flag<Boolean> ENABLE_NETWORK_INTERCEPTION = Flag.create(
    NETWORK_INSPECTOR, "enable.network.interception", "Enable Network Interception",
    "Enables interceptions on network requests and responses",
    false
  );
  // endregion

  // region BackgroundTask Inspector
  private static final FlagGroup BACKGROUND_TASK_INSPECTOR =
    new FlagGroup(FLAGS, "backgroundtask.inspector", "BackgroundTask Inspector");
  public static final Flag<Boolean> ENABLE_BACKGROUND_TASK_INSPECTOR_TAB = Flag.create(
    BACKGROUND_TASK_INSPECTOR, "enable.backgroundtask.inspector.tab", "Enable BackgroundTask Inspector Tab",
    "Enables a BackgroundTask Inspector Tab in the App Inspection tool window",
    true
  );
  // endregion

  //region Device Manager
  private static final FlagGroup DEVICE_MANAGER = new FlagGroup(FLAGS, "device.manager", "Device Manager");
  public static final Flag<Boolean> ENABLE_NEW_DEVICE_MANAGER_PANEL = Flag.create(
    DEVICE_MANAGER, "enable.device.manager", "Enable new Device Manager panel",
    "Enables the new Device Manager panel. It replaces AVD Manager with additional functionality.",
    true
  );
  public static final Flag<Boolean> ENABLE_DEVICE_MANAGER_HALF_BAKED_FEATURES = Flag.create(
    DEVICE_MANAGER, "enable.device.manager.half.baked.features", "Enable half baked Device Manager features",
    "Enables some features that could be unpolished or unready in the new Device Manager",
    false
  );

  public static final Flag<Boolean> WEAR_OS_VIRTUAL_DEVICE_PAIRING_ASSISTANT_ENABLED = Flag.create(
    DEVICE_MANAGER,
    "wear.os.virtual.device.pairing.assistant.enabled",
    "Enable the Wear OS virtual device pairing assistant",
    "Enable the Wear OS virtual device pairing assistant",
    true);

  public static final Flag<Boolean> PAIRED_DEVICES_TAB_ENABLED = Flag.create(
    DEVICE_MANAGER,
    "paired.devices.tab.enabled",
    "Enable the Paired devices tab",
    "Enable the Paired devices tab in the details panel",
    true);
  // endregion

  //region DDMLIB
  private static final FlagGroup DDMLIB = new FlagGroup(FLAGS, "ddmlib", "DDMLIB");
  public static final Flag<Boolean> ENABLE_JDWP_PROXY_SERVICE = Flag.create(
    DDMLIB, "enable.jdwp.proxy.service", "Enable jdwp proxy service",
    "Creates a proxy service within DDMLIB to allow shared device client connections.",
    false
  );
  public static final Flag<Boolean> ENABLE_DDMLIB_COMMAND_SERVICE = Flag.create(
    DDMLIB, "enable.ddmlib.command.service", "Enable ddmlib command service",
    "Creates a service within DDMLIB to allow external processes to issue commands to ddmlib.",
    false
  );
  // endregion DDMLIB

  //region SERVER_FLAGS
  private static final FlagGroup SERVER_FLAGS = new FlagGroup(FLAGS, "serverflags", "Server Flags");
  public static final Flag<Boolean> TEST_SERVER_FLAG = Flag.create(
    SERVER_FLAGS, "test", "Test Server Enabled Flag",
    "Creates a sample studio flag that can be set using a server flag",
    false
  );
  // endregion SERVER_FLAGS

  //region METRICS
  private static final FlagGroup METRICS = new FlagGroup(FLAGS, "metrics", "Metrics");
  public static final Flag<Boolean> NEW_CONSENT_DIALOG = Flag.create(
    METRICS, "new.consent.dialog", "New consent dialog",
    "Enable the new consent dialog for opting into metrics",
    true
  );
  // endregion SERVER_FLAGS

  // region App Insights
  private static final FlagGroup APP_INSIGHTS = new FlagGroup(FLAGS, "appinsights", "App Insights");
  public static final Flag<Boolean> APP_INSIGHTS_ENABLED =
    Flag.create(
      APP_INSIGHTS,
      "enabled",
      "Enabled",
      "Enable App Insights tool window and highlighting support.",
      false);
  // endregion App Insights
  private StudioFlags() { }
}

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
package com.android.tools.idea;

import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.TestUtils;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.tests.IdeaTestSuiteBase;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
public class IdeaTestSuite extends IdeaTestSuiteBase {

  public static final String DATA_BINDING_RUNTIME_ZIP = "tools/data-binding/data_binding_runtime.zip";
  private static final String ANDROID_GRADLE_PLUGIN_ZIP = "tools/base/build-system/android_gradle_plugin.zip";

  static {
    try {
      if (TestUtils.workspaceFileExists(ANDROID_GRADLE_PLUGIN_ZIP)) {
        unzipIntoOfflineMavenRepo(ANDROID_GRADLE_PLUGIN_ZIP);
      }
      linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest");
      linkIntoOfflineMavenRepo("tools/adt/idea/android/test_deps.manifest");
      linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest");
      // When using iml_module's split_test_target attribute, not all bazel targets will include this dependency.
      // Only bazel targets which rely on data_binding_runtime.zip will include this runtime dependency.
      if (TestUtils.workspaceFileExists(DATA_BINDING_RUNTIME_ZIP)) {
        unzipIntoOfflineMavenRepo(DATA_BINDING_RUNTIME_ZIP);
      }

      // Disable production AdbService to prevent it from interfering with AndroidDebugBridge (b/267107145).
      AdbService.disabled = true;
    }
    catch (Throwable e) {
      // See b/143359533 for why we are handling errors here
      System.err.println("ERROR: Error initializing test suite, tests will likely fail following this error");
      e.printStackTrace();
    }
  }
}

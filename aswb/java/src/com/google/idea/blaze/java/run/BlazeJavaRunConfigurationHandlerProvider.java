/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Java-specific handler provider for {@link BlazeCommandRunConfiguration}s. */
public class BlazeJavaRunConfigurationHandlerProvider
    implements BlazeCommandRunConfigurationHandlerProvider {

  static boolean supportsKind(@Nullable Kind kind) {
    return JavaLikeLanguage.EP_NAME.getExtensionList().stream()
      .mapMulti(BlazeJavaRunConfigurationHandlerProvider::languageToKinds)
      .anyMatch(k -> k.equals(kind));
  }

  private static void languageToKinds(JavaLikeLanguage language, Consumer<Kind> consumer) {
    language.getDebuggableKinds().forEach(consumer);
  }

  @Override
  public String getDisplayLabel() {
    return "JVM Compatible (Binary or Test)";
  }

  @Override
  public boolean canHandleKind(TargetState state, @Nullable Kind kind) {
    return supportsKind(kind);
  }

  @Override
  public BlazeCommandRunConfigurationHandler createHandler(BlazeCommandRunConfiguration config) {
    return new BlazeJavaRunConfigurationHandler(config);
  }

  @Override
  public String getId() {
    return "BlazeJavaRunConfigurationHandlerProvider";
  }
}

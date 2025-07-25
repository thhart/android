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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.SourceSetModelImpl.ROOT;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VWO;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SourceSetDslElement extends GradleDslBlockElement implements GradleDslNamedDomainElement {
  public static final PropertiesElementDescription<SourceSetDslElement> SOURCE_SET =
    new PropertiesElementDescription<>(null,
                                       SourceSetDslElement.class,
                                       SourceSetDslElement::new,
                                       "sourceSet");

  public static final ImmutableMap<String,PropertiesElementDescription<?>> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"aidl", SourceDirectoryDslElement.AIDL},
    {"assets", SourceDirectoryDslElement.ASSETS},
    {"java", SourceDirectoryDslElement.JAVA},
    {"jni", SourceDirectoryDslElement.JNI},
    {"jniLibs", SourceDirectoryDslElement.JNI_LIBS},
    {"manifest", SourceFileDslElement.MANIFEST},
    {"mlModels", SourceDirectoryDslElement.ML_MODELS},
    {"renderscript", SourceDirectoryDslElement.RENDERSCRIPT},
    {"res", SourceDirectoryDslElement.RES},
    {"resources", SourceDirectoryDslElement.RESOURCES},
    {"shaders", SourceDirectoryDslElement.SHADERS}
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"setRoot", exactly(1), ROOT, SET}
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"root", property, ROOT, VWO},
    {"setRoot", exactly(1), ROOT, SET},
    {"root", exactly(1), ROOT, SET}
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"root", property, ROOT, VAR},
  }).collect(toModelMap());

  @Override
  @NotNull
  public ImmutableMap<String,PropertiesElementDescription<?>> getChildPropertiesElementsDescriptionMap(
    @NotNull GradleDslNameConverter.Kind kind
  ) {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public SourceSetDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Nullable
  private String methodName;

  @Nullable
  @Override
  public String getMethodName() {
    return methodName;
  }

  @Override
  public void setMethodName(@Nullable String value) {
    methodName = value;
  }

  @Override
  @Nullable
  public String getAccessMethodName() { return SOURCE_SET.namedObjectAssociatedName; }
}

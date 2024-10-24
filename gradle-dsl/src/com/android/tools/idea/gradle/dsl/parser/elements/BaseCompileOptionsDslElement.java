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
package com.android.tools.idea.gradle.dsl.parser.elements;

import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.*;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for representing compileOptions block or other blocks which have sourceCompatibility / targetCompatibility fields.
 */
public abstract class BaseCompileOptionsDslElement extends GradleDslBlockElement {
  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"sourceCompatibility", property, SOURCE_COMPATIBILITY, VAR},
    {"sourceCompatibility", exactly(1), SOURCE_COMPATIBILITY, SET},
    {"setSourceCompatibility", exactly(1), SOURCE_COMPATIBILITY, SET},
    {"targetCompatibility", property, TARGET_COMPATIBILITY, VAR},
    {"targetCompatibility", exactly(1), TARGET_COMPATIBILITY, SET},
    {"setTargetCompatibility", exactly(1), TARGET_COMPATIBILITY, SET},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"sourceCompatibility", property, SOURCE_COMPATIBILITY, VAR},
    {"sourceCompatibility", exactly(1), SOURCE_COMPATIBILITY, SET},
    {"targetCompatibility", property, TARGET_COMPATIBILITY, VAR},
    {"targetCompatibility", exactly(1), TARGET_COMPATIBILITY, SET}
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"sourceCompatibility", property, SOURCE_COMPATIBILITY, VAR},
    {"targetCompatibility", property, TARGET_COMPATIBILITY, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  protected BaseCompileOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    setParsedElement(element);
  }
}

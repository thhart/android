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
package com.android.tools.idea.gradle.dsl.parser.android.splits;

import static com.android.tools.idea.gradle.dsl.model.android.splits.DensityModelImpl.AUTO;
import static com.android.tools.idea.gradle.dsl.model.android.splits.DensityModelImpl.COMPATIBLE_SCREENS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class DensityDslElement extends BaseSplitOptionsDslElement {
  public static final PropertiesElementDescription<DensityDslElement> DENSITY =
    new PropertiesElementDescription<>("density",
                                       DensityDslElement.class,
                                       DensityDslElement::new);

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"setAuto", exactly(1), AUTO, SET},
    {"compatibleScreens", atLeast(0), COMPATIBLE_SCREENS, OTHER}
  }).collect(toModelMap(BaseSplitOptionsDslElement.ktsToModelNameMap));

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"auto", property, AUTO, VAR},
    {"auto", exactly(1), AUTO, SET},
    {"compatibleScreens", property, COMPATIBLE_SCREENS, VAR},
    {"compatibleScreens", atLeast(0), COMPATIBLE_SCREENS, OTHER},
  }).collect(toModelMap(BaseSplitOptionsDslElement.groovyToModelNameMap));

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"auto", property, AUTO, VAR},
    {"compatibleScreens", property, COMPATIBLE_SCREENS, VAR},
  }).collect(toModelMap(BaseSplitOptionsDslElement.declarativeToModelNameMap));

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public DensityDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();
    if (property.equals("compatibleScreens")) {
      addToParsedExpressionList(COMPATIBLE_SCREENS, element);
      return;
    }

    super.addParsedElement(element);
  }
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.codeInsight.documentation.DocumentationHintEditorPane;


public class DocumentationWindowFixture {

  public static String getDocumentationContent(IdeFrameFixture ideFrame) {
    GuiTests.waitUntilShowing(ideFrame.robot(),
                              Matchers.byType(DocumentationHintEditorPane.class).andIsShowing());
    String quickDocumentationContent = ideFrame.robot().finder().findByType(DocumentationHintEditorPane.class, true /* showing */).getText();
    return quickDocumentationContent;
  }
}

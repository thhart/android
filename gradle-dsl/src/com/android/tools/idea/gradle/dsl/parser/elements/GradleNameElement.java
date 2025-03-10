/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

public class GradleNameElement {
  /**
   * This regex is used to extract indexes out from a dereferenced map or list property.
   * Example properties would be:
   *   someListProperty[0]
   *   otherMap['key'][1]
   *   list[0][2]['key'][2]
   *
   * The first match will be the property name, with the index texts in group 1 of succeeding matches.
   * For example, for the last example property:
   *    1st match, group 0 -> "list"
   *    2nd match, group 1 -> "0"
   *    3rd match, group 1 -> "2"
   *    4th match, group 1 -> "'key'"
   *    5th match, group 1 -> "2"
   */
  @NotNull
  public static final Pattern INDEX_PATTERN = Pattern.compile("\\[(.+?)\\]|(.+?)(?=\\[)");

  @Nullable
  private PsiElement myNameElement;
  @Nullable
  private String myLocalName;
  @Nullable
  private String myFakeName; // Used for names that do not require a file element.
  @Nullable
  private String myName = null; // Cached version of the final name (to be reset on any change of the above fields).
  @Nullable
  private String myOriginalName; // used to detect name changes in checkForModifiedName

  /**
   * Requires read access.
   */
  @NotNull
  public static GradleNameElement from(@NotNull PsiElement element, GradleDslNameConverter converter) {
    return new GradleNameElement(element, converter);
  }

  @NotNull
  public static GradleNameElement empty() {
    return new GradleNameElement(null, null);
  }

  @NotNull
  public static GradleNameElement create(@NotNull String name) {
    return new GradleNameElement(escape(name), false);
  }

  @NotNull
  public static GradleNameElement fake(@NotNull String name) {
    return new GradleNameElement(name, true);
  }

  @NotNull
  public static GradleNameElement copy(@NotNull GradleNameElement element) { return new GradleNameElement(element); }

  /**
   * Requires read access.
   */
  private GradleNameElement(@Nullable PsiElement element, GradleDslNameConverter converter) {
    setUpFrom(element, converter);
  }

  private GradleNameElement(@NotNull String name, boolean isFake) {
    if (isFake) {
      myFakeName = name;
    }
    else {
      myLocalName = name;
    }
    myOriginalName = name;
  }

  private GradleNameElement(@NotNull GradleNameElement element) {
    myLocalName = element.myLocalName;
    myFakeName = element.myFakeName;
    myOriginalName = element.myOriginalName;
  }

  /**
   * Changes this element to be backed by the given PsiElement. This method should not be called outside of
   * GradleWriter subclasses.
   */
  public void commitNameChange(@Nullable PsiElement nameElement,
                               GradleDslNameConverter converter,
                               GradleDslElement context) {
    setUpFrom(nameElement, converter);
    // TODO(xof): I'm suspicious of this fullName() call here.  Either work out why it's not a problem (and comment here) or come
    //  up with a case where it does the wrong thing (and fix).
    ModelPropertyDescription property = converter.modelDescriptionForParent(fullName(), context);
    if (property == null) {
      rename(fullNameParts());
    }
    else {
      rename(property.name);
    }
    myOriginalName = findName();
  }

  @NotNull
  public String fullName() {
    List<String> parts = fullNameParts();
    return createNameFromParts(parts);
  }

  @NotNull
  @Unmodifiable
  public List<String> fullNameParts() {
    String name = findName();
    if (name == null) {
      return new ArrayList<>();
    }
    List<String> nameSegments = split(name);
    return ContainerUtil.map(nameSegments, GradleNameElement::convertNameToKey);
  }

  public static String createNameFromParts(@NotNull @Unmodifiable List<String> parts) {
    return join(parts);
  }

  @NotNull
  @Unmodifiable
  public List<String> qualifyingParts() {
    List<String> parts = fullNameParts();
    if (parts.isEmpty()) {
      return parts;
    }
    else {
      return parts.subList(0, parts.size() - 1);
    }
  }

  public boolean isQualified() {
    List<String> parts = fullNameParts();
    return parts.size() > 1;
  }

  @NotNull
  public String name() {
    List<String> parts = fullNameParts();
    if (parts.isEmpty()) {
      return "";
    }
    else {
      return parts.get(parts.size() - 1);
    }
  }

  @Nullable
  public PsiElement getNamedPsiElement() {
    return myNameElement;
  }

  @Nullable
  public String getLocalName() {
    return myLocalName;
  }

  @Nullable
  public String getOriginalName() {
    return myOriginalName;
  }

  private void internalRename(@NotNull String newName) {
    if (!isFake()) {
      myLocalName = newName;
    }
    else {
      myFakeName = newName;
    }
    myName = null;
  }

  public void rename(@NotNull String newName) {
    internalRename(escape(newName));
  }

  public void rename(@NotNull List<String> hierarchicalName) {
    internalRename(join(hierarchicalName));
  }

  public boolean isEmpty() {
    String name = findName();
    return name == null || name.isEmpty();
  }

  public boolean isFake() {
    return myNameElement == null && myFakeName != null;
  }

  @Override
  @NotNull
  public String toString() {
    return fullName();
  }

  public boolean isReferencedIn(@NotNull String propertyReference) {
    String name = name();
    if (propertyReference.equals(name)) {
      return true;
    }

    Matcher matcher = INDEX_PATTERN.matcher(propertyReference);
    if (matcher.find() && matcher.groupCount() > 0) {
      String indexName = matcher.group(0);
      if (indexName.equals(name)) {
        return true;
      }
    }

    List<String> parts = split(propertyReference);
    if (!parts.isEmpty() && parts.get(0).equals(name)) {
      return true;
    }
    if (parts.size() > 1 && parts.get(0).equals(EXT.name) && parts.get(1).equals(name)) {
      return true;
    }

    return false;
  }

  @Nullable
  private String findName() {
    if (myName != null) return myName;
    String name = null;
    if (myLocalName != null) {
      name = myLocalName;
    }
    if (name == null && myFakeName != null) {
      name = myFakeName;
    }

    myName = name;
    return name;
  }

  @NotNull
  public static String convertNameToKey(@NotNull String str) {
    return StringUtil.unquoteString(str);
  }

  @NotNull
  public static String escape(@NotNull String part) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < part.length(); i++) {
      char c = part.charAt(i);
      if (c == '.' || c == '\\') {
        buf.append('\\');
      }
      buf.append(c);
    }
    String result = buf.toString();
    return result;
  }

  @NotNull
  public static String unescape(@NotNull String part) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < part.length(); i++) {
      char c = part.charAt(i);
      if (c == '\\') {
        assert i < part.length() - 1;
        buf.append(part.charAt(++i));
      }
      else {
        buf.append(c);
      }
    }
    String result = buf.toString();
    return result;
  }

  @NotNull
  public static String join (@NotNull List<String> parts) {
    String result = parts.stream().map(GradleNameElement::escape).collect(Collectors.joining("."));
    return result;
  }

  @NotNull
  public static List<String> split(@NotNull String name) {
    StringBuilder buf = new StringBuilder();
    List<String> result = new ArrayList<>();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '\\') {
        assert i < name.length() - 1;
        buf.append(name.charAt(++i));
      }
      else if (c == '.') {
        result.add(buf.toString());
        buf.setLength(0);
      }
      else {
        buf.append(name.charAt(i));
      }
    }
    result.add(buf.toString());
    return result;
  }

  /**
   * READ ACCESS REQUIRED.
   */
  private void setUpFrom(@Nullable PsiElement element, GradleDslNameConverter converter) {
    myNameElement = element;
    myFakeName = null;
    if (myNameElement instanceof PsiNamedElement) {
      myLocalName = escape(((PsiNamedElement)myNameElement).getName());
    }
    else if (myNameElement != null) {
      myLocalName = converter.psiToName(myNameElement);
    }
    myOriginalName = myLocalName;
    myName = null;
  }
}

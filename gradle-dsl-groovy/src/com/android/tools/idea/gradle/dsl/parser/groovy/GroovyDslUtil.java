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
package com.android.tools.idea.gradle.dsl.parser.groovy;

import static com.android.tools.idea.gradle.dsl.parser.SharedParserUtilsKt.findLastPsiElementIn;
import static com.android.tools.idea.gradle.dsl.parser.SharedParserUtilsKt.getNextValidParent;
import static com.android.tools.idea.gradle.dsl.parser.SharedParserUtilsKt.isDomainObjectConfiguratorMethodName;
import static com.android.tools.idea.gradle.dsl.parser.SharedParserUtilsKt.removePsiIfInvalid;
import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT;
import static com.intellij.psi.util.PsiTreeUtil.findChildOfType;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOLON;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOMMA;
import static org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil.addQuotes;
import static org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil.escapeStringCharacters;

import com.android.tools.idea.gradle.dsl.api.ext.InterpolatedText;
import com.android.tools.idea.gradle.dsl.api.ext.RawText;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslAnchor;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSettableExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleScriptFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

public final class GroovyDslUtil {
  private static final Logger LOG = Logger.getInstance(GroovyDslUtil.class);

  static boolean isBlockElement(
    @NotNull GrMethodCallExpression methodCall,
    @NotNull GradleDslNameConverter converter,
    @NotNull GradlePropertiesDslElement parent
  ) {
    GrReferenceExpression referenceExpression = findChildOfType(methodCall, GrReferenceExpression.class);
    if (referenceExpression == null) return false;
    String name = referenceExpression.getReferenceName();
    if (name == null) return false;
    boolean zeroOrOneClosures = methodCall.getClosureArguments().length < 2;
    GrExpression[] expressions = methodCall.getExpressionArguments();
    boolean namedDomainBlockReference = parent instanceof GradleDslNamedDomainContainer &&
                                        ((expressions.length == 0) ||
                                         (expressions.length == 1 && isDomainObjectConfiguratorMethodName(name)));
    List<String> specialCases = Arrays.asList("allprojects", APPLY_BLOCK_NAME, EXT.name);
    boolean knownBlockForParent = expressions.length == 0 &&
                                  (specialCases.contains(name) ||
                                   parent instanceof ConfigurationDslElement ||
                                   parent.getChildPropertiesElementDescription(converter, name) != null);
    return zeroOrOneClosures && (namedDomainBlockReference || knownBlockForParent);
  }

  @Nullable
  static GroovyPsiElement ensureGroovyPsi(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    if (element instanceof GroovyPsiElement) {
      return (GroovyPsiElement)element;
    }
    LOG.warn(new IllegalArgumentException("Wrong PsiElement type for writer! Must be of type GroovyPsiElement"));
    return null;
  }

  @Nullable
  static GrClosableBlock getClosableBlock(@NotNull PsiElement element) {
    if (!(element instanceof GrMethodCallExpression)) {
      return null;
    }

    GrClosableBlock[] closureArguments = ((GrMethodCallExpression)element).getClosureArguments();
    if (closureArguments.length > 0) {
      return closureArguments[0];
    }

    return null;
  }

  static GroovyPsiElementFactory getPsiElementFactory(@NotNull GradleDslElement element) {
    GroovyPsiElement psiElement = ensureGroovyPsi(element.getPsiElement());
    if (psiElement == null) {
      return null;
    }

    Project project = psiElement.getProject();
    return GroovyPsiElementFactory.getInstance(project);
  }

  static void maybeDeleteIfEmpty(@Nullable PsiElement element, @NotNull GradleDslElement dslElement) {
    GradleDslElement parentDslElement = dslElement.getParent();
    if (((parentDslElement instanceof GradleDslExpressionList && !((GradleDslExpressionList)parentDslElement).shouldBeDeleted()) ||
         (parentDslElement instanceof GradleDslExpressionMap && !((GradleDslExpressionMap)parentDslElement).shouldBeDeleted())) &&
        parentDslElement.getPsiElement() == element) {
      // Don't delete parent if empty.
      return;
    }
    deleteIfEmpty(element, dslElement);
  }

  private static void deleteIfEmpty(@Nullable PsiElement element, @NotNull GradleDslElement containingDslElement) {
    if (element == null) {
      return;
    }

    PsiElement parent = element.getParent();
    GradleDslElement dslParent = getNextValidParent(containingDslElement);

    if (!element.isValid()) {
      // Skip deleting
    }
    else if (element instanceof GrAssignmentExpression) {
      if (((GrAssignmentExpression)element).getRValue() == null) {
        element.delete();
      }
    }
    else if (element instanceof GrApplicationStatement) {
      if (((GrApplicationStatement)element).getArgumentList() == null) {
        element.delete();
      }
    }
    else if (element instanceof GrClosableBlock) {
      if (dslParent == null || dslParent.isInsignificantIfEmpty()) {
        final Boolean[] isEmpty = new Boolean[]{true};
        ((GrClosableBlock)element).acceptChildren(new GroovyElementVisitor() {
          @Override
          public void visitElement(@NotNull GroovyPsiElement child) {
            if (child instanceof GrParameterList) {
              if (((GrParameterList)child).getParameters().length == 0) {
                return; // Ignore the empty parameter list.
              }
            }
            isEmpty[0] = false;
          }
        });
        if (isEmpty[0]) {
          element.delete();
        }
      }
    }
    else if (element instanceof GrMethodCallExpression) {
      GrMethodCallExpression call = ((GrMethodCallExpression)element);
      // getArgumentList() logs an error if the argument list has already been deleted.
      GrArgumentList argumentList = null;
      for (PsiElement curr = call.getFirstChild(); curr != null; curr = curr.getNextSibling()) {
        if (curr instanceof GrArgumentList) {
          argumentList = (GrArgumentList)curr;
          break;
        }
      }
      GrClosableBlock[] closureArguments = call.getClosureArguments();
      if ((argumentList == null || argumentList.getAllArguments().length == 0)
          && closureArguments.length == 0) {
        element.delete();
      }
    }
    else if (element instanceof GrArgumentList) {
      GrArgumentList argumentList = (GrArgumentList)element;
      if (argumentList.getAllArguments().length == 0) {
        if (!(parent instanceof GrMethodCallExpression) || !((GrMethodCallExpression)parent).hasClosureArguments()) {
          argumentList.delete();
        }
      }
    }
    else if (element instanceof GrNamedArgument) {
      GrNamedArgument namedArgument = (GrNamedArgument)element;
      if (namedArgument.getExpression() == null) {
        namedArgument.delete();
      }
    }
    else if (element instanceof GrVariableDeclaration) {
      GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)element;
      for (GrVariable grVariable : variableDeclaration.getVariables()) {
        if (grVariable.getInitializerGroovy() == null) {
          grVariable.delete();
        }
      }
      // If we have no more variables, delete the declaration.
      if (variableDeclaration.getVariables().length == 0) {
        variableDeclaration.delete();
      }
    }
    else if (element instanceof GrVariable) {
      GrVariable variable = (GrVariable)element;
      if (variable.getInitializerGroovy() == null) {
        variable.delete();
      }
    }
    else if (element instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)element;
      if (listOrMap.isMap() && listOrMap.getNamedArguments().length == 0) {
        listOrMap.delete();
      }
      else if (!listOrMap.isMap() && listOrMap.getInitializers().length == 0) {
        listOrMap.delete();
      }
    }

    if (!element.isValid()) {
      // Give the parent a chance to adapt to the missing child.
      handleElementRemoved(parent, element);
      // If this element is deleted, also delete the parent if it is empty.
      if (dslParent != null) {
        if (element == dslParent.getPsiElement() && dslParent.isInsignificantIfEmpty()) {
          maybeDeleteIfEmpty(parent, dslParent);
        }
        else {
          maybeDeleteIfEmpty(parent, containingDslElement);
        }
      }
    }
  }

  /**
   * This method is used to edit the PsiTree once an element has been deleted.
   * <p>
   * It currently only looks at GrListOrMap to insert a ":" into a map. This is needed because once we delete
   * the final element in a map we are left with [], which is a list.
   */
  static void handleElementRemoved(@Nullable PsiElement psiElement, @Nullable PsiElement removed) {
    if (psiElement == null) {
      return;
    }

    if (psiElement instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)psiElement;
      // Make sure it was being used as a map
      if (removed instanceof GrNamedArgument) {
        if (listOrMap.isEmpty()) {
          final ASTNode node = listOrMap.getNode();
          node.addLeaf(mCOLON, ":", listOrMap.getRBrack().getNode());
        }
      }
    }
  }

  @Nullable
  static GrExpression extractUnsavedExpression(@NotNull GradleDslSettableExpression literal) {
    GroovyPsiElement newElement = ensureGroovyPsi(literal.getUnsavedValue());
    if (!(newElement instanceof GrExpression)) {
      return null;
    }

    return (GrExpression)newElement;
  }

  private static String escapeString(@NotNull String str, boolean forGString) {
    StringBuilder sb = new StringBuilder();
    escapeStringCharacters(str.length(), str, forGString ? "\"" : "'", true, true, sb);
    return sb.toString();
  }

  /**
   * Creates a literal from a context and value.
   *
   * @param context      the expression context within which this literal will be interpreted
   * @param applyContext the context used to create a GrPsiElementFactory
   * @param unsavedValue the value for the new expression
   * @return created PsiElement
   */
  @Nullable
  static PsiElement createLiteral(@NotNull GradleDslSimpleExpression context,
                                  @NotNull GradleDslFile applyContext,
                                  @NotNull Object unsavedValue) {
    CharSequence unsavedValueText = null;
    if (unsavedValue instanceof String) {
      String stringValue = (String)unsavedValue;
      if (StringUtil.isQuotedString(stringValue)) {
        // We need to escape the string without the quotes and then add them back.
        String unquotedString = GrStringUtil.removeQuotes(stringValue);
        unsavedValueText = addQuotes(escapeString(unquotedString, true), true);
      }
      else {
        unsavedValueText = addQuotes(escapeString((String)unsavedValue, false), false);
      }
    }
    else if (unsavedValue instanceof Integer || unsavedValue instanceof Boolean || unsavedValue instanceof BigDecimal) {
      unsavedValueText = unsavedValue.toString();
    }
    else if (unsavedValue instanceof ReferenceTo) {
      unsavedValueText = convertToExternalTextValue(((ReferenceTo)unsavedValue).getReferredElement(), context, applyContext, false);
      if (unsavedValueText == null) unsavedValueText = ((ReferenceTo)unsavedValue).getReferredElement().getFullName();
    }
    else if (unsavedValue instanceof InterpolatedText) {
      InterpolatedText interpolatedValue = (InterpolatedText)unsavedValue;
      StringBuilder builder = new StringBuilder();
      for (InterpolatedText.InterpolatedTextItem elem : interpolatedValue.getInterpolationElements()) {
        if (elem.getTextItem() != null) {
          builder.append(elem.getTextItem());
        }
        if (elem.getReferenceItem() != null) {
          String externalText = convertToExternalTextValue(elem.getReferenceItem().getReferredElement(), context, applyContext, true);
          builder.append(externalText != null ? externalText : elem.getReferenceItem().getReferredElement().getFullName());
        }
      }
      unsavedValueText = addQuotes(builder.toString(), true);
    }
    else if (unsavedValue instanceof RawText) {
      unsavedValueText = ((RawText)unsavedValue).getGroovyText();
    }

    if (unsavedValueText == null) {
      return null;
    }

    GroovyPsiElementFactory factory = getPsiElementFactory(applyContext);
    if (factory == null) {
      return null;
    }

    return factory.createExpressionFromText(unsavedValueText);
  }

  public static String convertToExternalTextValue(GradleDslSimpleExpression context,
                                                  GradleDslFile applyContext,
                                                  String referenceText,
                                                  boolean forInjection) {
    GradleDslElement referenceElement = context.resolveInternalSyntaxReference(referenceText, false);
    if (referenceElement == null) return referenceText;

    String externalReference = convertToExternalTextValue(referenceElement, context, applyContext, forInjection);
    return externalReference != null ? externalReference : referenceText;
  }

  public static String convertToExternalTextValue(GradleDslElement referenceElement,
                                                  GradleDslSimpleExpression context,
                                                  GradleDslFile applyContext,
                                                  boolean forInjection) {
    StringBuilder externalName = new StringBuilder();
    GradleDslElement currentParent = referenceElement.getParent();

    HashSet<GradleDslElement> contextParents = new HashSet<>(10);
    GradleDslElement contextParent = context.getParent();
    while (contextParent != null && !(contextParent instanceof GradleDslFile)) {
      contextParents.add(contextParent);
      contextParent = contextParent.getParent();
    }

    ArrayList<GradleDslElement> resolutionElements = new ArrayList<>();
    resolutionElements.add(referenceElement);
    while (currentParent != null && currentParent.getParent() != null && !contextParents.contains(currentParent)) {
      resolutionElements.add(0, currentParent);
      currentParent = currentParent.getParent();
    }

    if (currentParent instanceof GradleVersionCatalogFile && context.getDslFile() instanceof GradleScriptFile) {
      externalName.append(((GradleVersionCatalogFile)currentParent).getCatalogName()).append(".");
      if (!resolutionElements.isEmpty() && "libraries".equals(resolutionElements.get(0).getName())) {
        resolutionElements.remove(0);
      }
    }

    for (GradleDslElement currentElement : resolutionElements) {
      List<String> elementExternalNameParts =
        applyContext.getParser().externalNameForParent(currentElement.getName(), currentElement.getParent()).externalNameParts;
      if (currentElement.getDslFile() instanceof GradleVersionCatalogFile) {
        externalName.append(String.join(".", elementExternalNameParts.get(0).split("[_.-]")));
      }
      else if (currentElement.getParent() instanceof GradleDslExpressionList && currentElement instanceof GradleDslSimpleExpression) {
        GradleDslExpressionList parent = (GradleDslExpressionList)currentElement.getParent();
        int i = parent.getSimpleExpressions().indexOf(currentElement);
        externalName.append(i + "]");
      }
      else if (currentElement instanceof ExtDslElement || currentElement instanceof BuildScriptDslElement){
        // don't need to add ext.variableName or parentProject variable name for Groovy as it smart enough to
        // fall back to ext and to root in case it cannot find variable during runtime
      }
      else {
        externalName.append(quotePartIfNecessary(elementExternalNameParts.get(0)));
      }
      if (currentElement != referenceElement) {
        if (currentElement instanceof GradleDslExpressionList) {
          externalName.append("[");
        }
        else if (currentElement instanceof ExtDslElement || currentElement instanceof BuildScriptDslElement) {
          // do nothing
        }
        else {
          externalName.append(".");
        }
      }
    }

    if (externalName.isEmpty()) {
      return null;
    }
    else {
      if (!forInjection) return externalName.toString();
      Pattern varShouldNotBeWrapped = Pattern.compile("(([a-zA-Z0-9_]\\w*)(\\.([a-zA-Z0-9_]\\w+))*)");
      if (varShouldNotBeWrapped.matcher(externalName.toString()).matches()) {
        return "$" + externalName.toString();
      }
      else {
        return "${" + externalName.toString() + "}";
      }
    }
  }

  // this is a bit like GrStringUtil.isStringLiteral() but does not rely on the element being a
  // GrLiteral (because we deal with syntactical forms where the objects are lexically literals
  // but not grammatical literals, such as the method call named by a string in
  //   buildTypes {
  //     'foo' {
  //        ...
  //     }
  //   }
  public static boolean isStringLiteral(@NotNull PsiElement element) {
    ASTNode node = getFirstASTNode(element);
    if (node == null) return false;
    return TokenSets.STRING_LITERAL_SET.contains(node.getElementType());
  }

  public static boolean decodeStringLiteral(@NotNull PsiElement element, @NotNull StringBuilder sb) {
    // extract the portion of text corresponding to the string contents
    String contents = GrStringUtil.removeQuotes(element.getText());

    // process escapes in the contents appropriately to the token type (like GrLiteralEscaper.decode(),
    // but as described in the comment above isStringLiteral, we do not have a GrLiteral for all of our uses.)
    final IElementType elementType = element.getFirstChild().getNode().getElementType();
    if (GroovyTokenSets.STRING_LITERALS.contains(elementType) || elementType == GroovyTokenTypes.mGSTRING_CONTENT) {
      return GrStringUtil.parseStringCharacters(contents, sb, null);
    }
    else if (elementType == GroovyTokenTypes.mREGEX_LITERAL || elementType == GroovyTokenTypes.mREGEX_CONTENT) {
      return GrStringUtil.parseRegexCharacters(contents, sb, null, true);
    }
    else if (elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT) {
      return GrStringUtil.parseRegexCharacters(contents, sb, null, false);
    }
    else return false;
  }

  static @Nullable String methodCallBlockName(@NotNull GrMethodCallExpression methodCallExpression) {
    GrReferenceExpression referenceExpression = findChildOfType(methodCallExpression, GrReferenceExpression.class);
    if (referenceExpression == null) return null;
    String callName = referenceExpression.getReferenceName();
    if (callName == null) return null;
    if (!isDomainObjectConfiguratorMethodName(callName)) return null;
    GrExpression[] arguments = methodCallExpression.getExpressionArguments();
    if (arguments.length != 1) return null;
    GrExpression argument = arguments[0];
    if (isStringLiteral(argument)) {
      StringBuilder sb = new StringBuilder();
      boolean result = decodeStringLiteral(argument, sb);
      if (result) return sb.toString();
    }
    return null;
  }

  public static String gradleNameFor(GrExpression expression) {
    final boolean[] allValid = {true};
    StringBuilder result = new StringBuilder();

    expression.accept(new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
        GrExpression qualifierExpression = referenceExpression.getQualifierExpression();
        if (qualifierExpression != null) {
          qualifierExpression.accept(this);
          result.append(".");
        }
        String name = referenceExpression.getReferenceName();
        if (name != null) {
          result.append(GradleNameElement.escape(name));
        }
        else {
          allValid[0] = false;
        }
      }

      @Override
      public void visitMethodCallExpression(@NotNull GrMethodCallExpression e) {
        if (e.getText().startsWith("project") && e.getArgumentList().getAllArguments().length == 1 &&
            e.getArgumentList().getAllArguments()[0] instanceof GrLiteral) {
          // TODO(karimai): Add interpolation handling when these are supported.
          result.append(e.getText().replaceAll("\\s", "").replace("\"", "'"));
        }
        else {
          String name = methodCallBlockName(e);
          if (name == null) {
            allValid[0] = false;
          }
          else {
            GrReferenceExpression referenceExpression = findChildOfType(e, GrReferenceExpression.class); // known not-null
            if (referenceExpression.isQualified()) {
              referenceExpression.getQualifierExpression().accept(this);
              result.append(".");
            }
            result.append(GradleNameElement.escape(name));
          }
        }
      }

      @Override
      public void visitIndexProperty(@NotNull GrIndexProperty indexPropertyExpression) {
        GrExpression invokedExpression = indexPropertyExpression.getInvokedExpression();
        invokedExpression.accept(this);
        result.append("[");
        GrArgumentList argumentList = indexPropertyExpression.getArgumentList();
        GroovyPsiElement[] arguments = argumentList.getAllArguments();
        if (arguments.length != 1) {
          allValid[0] = false;
          return;
        }
        result.append(arguments[0].getText());
        result.append("]");
      }

      @Override
      public void visitElement(@NotNull GroovyPsiElement element) {
        allValid[0] = false;
      }
    }));

    return allValid[0] ? result.toString() : null;
  }

  /**
   * Creates a literal expression map enclosed with brackets "[]" from the given {@link GradleDslExpressionMap}.
   */
  static PsiElement createDerivedMap(@NotNull GradleDslExpressionMap expressionMap) {
    PsiElement parentPsiElement = getParentPsi(expressionMap.getParent());
    if (parentPsiElement == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentPsiElement.getProject());
    GrExpression emptyMap = factory.createExpressionFromText("[:]");
    GrNamedArgument namedArgument = factory.createNamedArgument(expressionMap.getName(), emptyMap);
    PsiElement addedElement = addToMap((GrListOrMap)parentPsiElement, namedArgument);
    if (addedElement instanceof GrNamedArgument) {
      PsiElement added = ((GrNamedArgument)addedElement).getExpression();
      expressionMap.setPsiElement(added);
      return added;
    }
    else {
      LOG.warn("Unexpected result of addToMap: " + addedElement);
      return null;
    }
  }

  /**
   * This method is used in order to add elements to the back of a map,
   * it is derived from {@link ASTDelegatePsiElement#addInternal(ASTNode, ASTNode, ASTNode, Boolean)}.
   */
  private static @Nullable PsiElement realAddBefore(@NotNull GrListOrMap element,
                                                    @NotNull PsiElement newElement,
                                                    @NotNull PsiElement anchor) {
    CheckUtil.checkWritable(element);
    TreeElement elementCopy = ChangeUtil.copyToElement(newElement);
    ASTNode anchorNode = getAnchorNode(element, anchor.getNode(), true);
    ASTNode newNode = CodeEditUtil.addChildren(element.getNode(), elementCopy, elementCopy, anchorNode);
    if (newNode == null) {
      LOG.warn(new IncorrectOperationException("Element cannot be added"));
      return null;
    }
    if (newNode instanceof TreeElement) {
      return ChangeUtil.decodeInformation((TreeElement)newNode).getPsi();
    }
    return newNode.getPsi();
  }

  /**
   * This method has been taken from {@link ASTDelegatePsiElement} in order to implement a correct version of
   * {@link #realAddBefore}.
   */
  private static ASTNode getAnchorNode(@NotNull PsiElement element, final ASTNode anchor, final Boolean before) {
    ASTNode anchorBefore;
    if (anchor != null) {
      anchorBefore = before.booleanValue() ? anchor : anchor.getTreeNext();
    }
    else {
      if (before != null && !before.booleanValue()) {
        anchorBefore = element.getNode().getFirstChildNode();
      }
      else {
        anchorBefore = null;
      }
    }
    return anchorBefore;
  }

  static @Nullable PsiElement addToMap(@NotNull GrListOrMap map, @NotNull GrNamedArgument newValue) {
    final ASTNode astNode = map.getNode();
    if (map.getNamedArguments().length != 0) {
      astNode.addLeaf(mCOMMA, ",", map.getRBrack().getNode());
    }
    else {
      // Empty maps are defined by [:], we need to delete the colon before adding the first element.
      while (map.getLBrack().getNextSibling() != map.getRBrack()) {
        map.getLBrack().getNextSibling().delete();
      }
    }

    return realAddBefore(map, newValue, map.getRBrack());
  }

  @Nullable
  static PsiElement processListElement(@NotNull GradleDslSettableExpression expression) {
    GradleDslElement parent = expression.getParent();
    if (parent == null) {
      return null;
    }

    PsiElement parentPsi = parent.create();
    if (parentPsi == null) {
      return null;
    }

    PsiElement newExpressionPsi = expression.getUnsavedValue();
    if (newExpressionPsi == null) {
      return null;
    }

    PsiElement added = createPsiElementInsideList(parent, expression, parentPsi, newExpressionPsi);
    expression.setPsiElement(added);
    expression.commit();
    return expression.getPsiElement();
  }

  @Nullable
  static PsiElement processMapElement(@NotNull GradleDslSettableExpression expression) {
    GradleDslElement parent = expression.getParent();
    assert parent != null;

    GroovyPsiElement parentPsiElement = ensureGroovyPsi(parent.create());
    if (parentPsiElement == null) {
      return null;
    }

    expression.setPsiElement(parentPsiElement);
    GrExpression newLiteral = extractUnsavedExpression(expression);
    if (newLiteral == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(newLiteral.getProject());
    GrNamedArgument namedArgument = factory.createNamedArgument(expression.getName(), newLiteral);
    PsiElement added;
    if (parentPsiElement instanceof GrCommandArgumentList) {
      // addNamedArgument() on a GrCommandArgumentList adds the argument before the last existing argument, if any.  Although it doesn't
      // directly affect the semantics, we want to add new elements at the end of the argument list.  (This doesn't work for general
      // GrArgumentLists because the last child of the Psi might be the closing bracket of an explicit method call.)
      added = parentPsiElement.addAfter(namedArgument, parentPsiElement.getLastChild());
    }
    else if (parentPsiElement instanceof GrArgumentList) {
      added = ((GrArgumentList)parentPsiElement).addNamedArgument(namedArgument);
    }
    else if (parentPsiElement instanceof GrListOrMap) {
      GrListOrMap grListOrMap = (GrListOrMap)parentPsiElement;
      added = addToMap(grListOrMap, namedArgument);
    }
    else {
      added = parentPsiElement.addBefore(namedArgument, parentPsiElement.getLastChild());
    }
    if (added instanceof GrNamedArgument) {
      GrNamedArgument addedNameArgument = (GrNamedArgument)added;
      GrExpression grExpression = getChildOfType(addedNameArgument, GrExpression.class);
      if (grExpression != null) {
        expression.setExpression(grExpression);
        expression.commit();
        expression.reset();
        return expression.getPsiElement();
      }
      else {
        return null;
      }
    }
    else {
      LOG.warn(new IllegalStateException("Unexpected element type added to map: " + added));
      return null;
    }
  }

  static PsiElement createInfixElement(@NotNull GradleDslSettableExpression expression) {
    GradleDslElement parent = expression.getParent();
    if (!(parent instanceof GradleDslInfixExpression)) return null;
    GradleDslInfixExpression infixExpression = (GradleDslInfixExpression) parent;
    PsiElement parentPsi = parent.create();
    if (parentPsi == null) return null;
    PsiElement expressionPsi = expression.getUnsavedValue();
    if (expressionPsi == null) return null;

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentPsi.getProject());
    String expressionText = parentPsi.getText() + " " + expression.getName() + " " + expressionPsi.getText();
    GrExpression newInfixExpression = factory.createExpressionFromText(expressionText);
    PsiElement newParentPsi = parentPsi.replace(newInfixExpression);
    parent.setPsiElement(newParentPsi);

    expression.setPsiElement(newParentPsi.getLastChild().getLastChild());
    expression.commit();
    expression.reset();
    return expression.getPsiElement();
  }

  static void applyDslLiteralOrReference(@NotNull GradleDslSettableExpression expression, GroovyDslWriter writer) {
    PsiElement psiElement = ensureGroovyPsi(expression.getPsiElement());
    if (psiElement == null) {
      return;
    }

    maybeUpdateName(expression, writer);

    GrExpression newLiteral = extractUnsavedExpression(expression);
    if (newLiteral == null) {
      return;
    }
    PsiElement psiExpression = ensureGroovyPsi(expression.getExpression());
    if (psiExpression != null) {
      PsiElement replace = psiExpression.replace(newLiteral);
      if (replace instanceof GrLiteral || replace instanceof GrReferenceExpression || replace instanceof GrIndexProperty) {
        expression.setExpression(replace);
      }
    }
    else {
      // This element has just been created and will currently look like "propertyName =" or "propertyName ". Here we add the value.
      PsiElement added;
      // This element will look like "propertyName propertyValue" and we need to create an intermediate psiElement (commandArgumentList)
      // to prevent breaking the psiTree of a GrApplicationStatement.
      if (psiElement instanceof GrApplicationStatement) {
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
        // Create a fake applicationStatement element.
        GrApplicationStatement fakeStatement = (GrApplicationStatement)(factory.createStatementFromText("a 'a'"));
        GrCommandArgumentList arguments = fakeStatement.getArgumentList();
        // Empty the arguments list so we can use it for psiElement.
        arguments.getFirstChild().delete();
        psiElement.addAfter(arguments, psiElement.getLastChild());
        added = ((GrApplicationStatement)psiElement).getArgumentList().add(newLiteral);
      }
      else if (psiElement instanceof GrMethodCallExpression) {
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
        GrMethodCallExpression fakeExpression = (GrMethodCallExpression)(factory.createExpressionFromText("a('a')"));
        GrArgumentList arguments = fakeExpression.getArgumentList();
        arguments.getExpressionArguments()[0].delete();
        psiElement.addAfter(arguments, psiElement.getLastChild());
        added = ((GrMethodCallExpression)psiElement).getArgumentList().add(newLiteral);
      }
      else {
        added = psiElement.addAfter(newLiteral, psiElement.getLastChild());
      }
      expression.setExpression(added);

      if (expression.getUnsavedClosure() != null) {
        createAndAddClosure(expression.getUnsavedClosure(), expression);
      }
    }

    expression.reset();
    expression.commit();
  }

  @Nullable
  static PsiElement createNamedArgumentList(@NotNull GradleDslExpressionList expressionList) {
    GradleDslElement parent = expressionList.getParent();
    assert parent instanceof GradleDslExpressionMap;

    PsiElement parentPsiElement = parent.create();
    if (parentPsiElement == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentPsiElement.getProject());
    GrExpression expressionFromText = factory.createExpressionFromText("[]");
    GrNamedArgument namedArgument = factory.createNamedArgument(expressionList.getName(), expressionFromText);
    PsiElement added;
    if (parentPsiElement instanceof GrArgumentList) {
      GrArgumentList argList = (GrArgumentList)parentPsiElement;
      // This call can return a placeholder PsiElement. We can't use its return value.
      argList.addNamedArgument(namedArgument);

      GrNamedArgument[] args = argList.getNamedArguments();
      added = args[args.length - 1];
    }
    else if (parentPsiElement instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)parentPsiElement;
      // For list and maps we need to add the element delimiter "," after the added element if there is more than one.
      if (!listOrMap.isEmpty()) {
        final ASTNode node = listOrMap.getNode();
        node.addLeaf(mCOMMA, ",", listOrMap.getLBrack().getNextSibling().getNode());
      }
      added = parentPsiElement.addAfter(namedArgument, parentPsiElement.getLastChild());
    }
    else {
      added = parentPsiElement.addAfter(namedArgument, parentPsiElement.getLastChild());
    }
    if (added instanceof GrNamedArgument) {
      GrNamedArgument addedNameArgument = (GrNamedArgument)added;
      expressionList.setPsiElement(addedNameArgument.getExpression());
      return expressionList.getPsiElement();
    }
    return null;
  }

  @Nullable
  static PsiElement createMethodCallArgumentList(@NotNull GradleDslExpressionList expressionList) {
    GradleDslElement parent = expressionList.getParent();
    assert parent instanceof GradleDslMethodCall;

    PsiElement parentPsiElement = parent.create();
    if (parentPsiElement == null) {
      return null;
    }
    if (parentPsiElement instanceof GrMethodCallExpression) {
      return ((GrMethodCallExpression)parentPsiElement).getArgumentList();
    }
    return null;
  }

  @Nullable
  static String getInjectionName(@NotNull GrStringInjection injection) {
    String variableName = null;

    GrClosableBlock closableBlock = injection.getClosableBlock();
    if (closableBlock != null) {
      String blockText = closableBlock.getText();
      variableName = blockText.substring(1, blockText.length() - 1);
    }
    else {
      GrExpression expression = injection.getExpression();
      if (expression != null) {
        variableName = expression.getText();
      }
    }

    return variableName;
  }

  @NotNull
  static String ensureUnquotedText(@NotNull String str) {
    if (StringUtil.isQuotedString(str)) {
      str = StringUtil.unquoteString(str);
    }
    return str;
  }

  @Nullable
  static PsiElement getParentPsi(@Nullable GradleDslElement parent) {
    if (parent == null) return null;
    return ensureGroovyPsi(parent.create());
  }

  /**
   * This method is required to work out whether a GradleDslReference or GradleDslLiteral is an internal value in a map.
   * This allows us to add the PsiElement into the correct position, note: due to the PsiElements Api we have to add the
   * ASTNodes directly in {@link #emplaceElementIntoList(PsiElement, PsiElement, PsiElement)}. This method checks the specific
   * conditions where we need to add an element to the inside of a literal list. The reason we have to do it this way
   * is that when we are applying a GradleDslReference or GradleDslLiteral we don't know whether (1) we are actually in a list and (2)
   * whether the list actually needs us to add a comma. Ideally we would have the apply/create/delete methods of GradleDslExpressionList
   * position the arguments. This is a workaround for now.
   * <p>
   * Note: In order to get the position of where to insert the item, we set the PsiElement of the literal/reference to be the previous
   * item in the list (this is done in GradleDslExpressionList) and then set it back once we have called apply.
   */
  static boolean shouldAddToListInternal(@NotNull GradleDslElement element) {
    GradleDslElement parent = element.getParent();
    if (!(parent instanceof GradleDslExpressionList)) {
      return false;
    }
    PsiElement parentPsi = parent.getPsiElement();
    return ((parentPsi instanceof GrListOrMap && ((GrListOrMap)parentPsi).getInitializers().length > 0) ||
            (parentPsi instanceof GrArgumentList && ((GrArgumentList)parentPsi).getAllArguments().length > 0));
  }

  static void emplaceElementIntoList(@NotNull PsiElement anchorBefore, @NotNull PsiElement list, @NotNull PsiElement newElement) {
    final ASTNode node = list.getNode();
    final ASTNode anchor = anchorBefore.getNode().getTreeNext();
    node.addChild(newElement.getNode(), anchor);
    node.addLeaf(mCOMMA, ",", newElement.getNode());
  }

  static PsiElement emplaceElementToFrontOfList(@NotNull PsiElement listElement, @NotNull PsiElement newElement) {
    assert listElement instanceof GrListOrMap || listElement instanceof GrArgumentList;
    final ASTNode node = listElement.getNode();
    if (listElement instanceof GrListOrMap) {
      GrListOrMap list = (GrListOrMap)listElement;
      final ASTNode anchor = list.getLBrack().getNode().getTreeNext();
      if (!list.isEmpty()) {
        node.addLeaf(mCOMMA, ",", anchor);
        node.addLeaf(TokenType.WHITE_SPACE, " ", anchor);
      }
      // We want to anchor this off the added mCOMMA node.
      node.addChild(newElement.getNode(), list.getLBrack().getNode().getTreeNext());
    }
    else if (((GrArgumentList)listElement).getLeftParen() != null) {
      GrArgumentList list = (GrArgumentList)listElement;
      PsiElement leftParen = list.getLeftParen();
      assert leftParen != null;
      final ASTNode anchor = list.getLeftParen().getNode().getTreeNext();
      if (list.getAllArguments().length != 0) {
        node.addLeaf(mCOMMA, ",", anchor);
        node.addLeaf(TokenType.WHITE_SPACE, " ", anchor);
      }
      node.addChild(newElement.getNode(), list.getLeftParen().getNode().getTreeNext());
    }
    else {
      ASTNode anchor = getFirstASTNode(listElement);
      if (anchor != null) {
        node.addLeaf(mCOMMA, ",", anchor);
        node.addLeaf(TokenType.WHITE_SPACE, " ", anchor);
      }
      // We want to anchor this off the added mCOMMA node
      node.addChild(newElement.getNode(), getFirstASTNode(listElement));
    }

    return newElement;
  }

  @Nullable
  static ASTNode getFirstASTNode(@NotNull PsiElement parent) {
    final PsiElement firstChild = parent.getFirstChild();
    if (firstChild == null) {
      return null;
    }
    return firstChild.getNode();
  }

  @NotNull
  static PsiElement createPsiElementInsideList(@NotNull GradleDslElement parentDslElement,
                                               @NotNull GradleDslElement dslElement,
                                               @NotNull PsiElement parentPsiElement,
                                               @NotNull PsiElement newElement) {
    PsiElement added;
    GradleDslAnchor anchor = parentDslElement.requestAnchor(dslElement);
    if (shouldAddToListInternal(dslElement) && anchor instanceof GradleDslAnchor.After after) {
      // Get the anchor
      PsiElement anchorPsi = after.getDslElement().getPsiElement();
      assert anchorPsi != null;

      emplaceElementIntoList(anchorPsi, parentPsiElement, newElement);
      added = newElement;
    }
    else {
      added = emplaceElementToFrontOfList(parentPsiElement, newElement);
    }

    return added;
  }

  // from Groovy documentation
  //
  // 2 (Keywords) The following list represents all the keywords of the Groovy language
  @NotNull private static final Set<String> GROOVY_KEYWORDS = new HashSet<>(Arrays.asList(
    "as", "assert", "break", "case",
    "catch", "class", "const", "continue",
    "def", "default", "do", "else",
    "enum", "extends", "false", "finally",
    "for", "goto", "if", "implements",
    "import", "in", "instanceof", "interface",
    "new", "null", "package", "return",
    "super", "switch", "this", "throw",
    "throws", "trait", "true", "try",
    "while"
    ));

  // from Groovy documentation
  //
  // 3.1 (Normal Identifiers) Identifiers start with a letter, a dollar or an underscore. They cannot start with a number.
  //
  // from groovy.flex
  //
  //   mDIGIT = [0-9]
  //   mLETTER = [:letter:] | "_"
  //   mIDENT = ({mLETTER}|\$) ({mLETTER} | {mDIGIT} | \$)*
  //
  // so apparently we can only have ASCII digits, but arbitrary letters.  OK then.
  @NotNull private static final Pattern GROOVY_NORMAL_IDENTIFIER = Pattern.compile("(\\p{L}|[_$])(\\p{L}|[0-9]|[_$])*");

  @NotNull
  static String quotePartIfNecessary(String part) {
    if(!GROOVY_NORMAL_IDENTIFIER.matcher(part).matches()) {
      // TODO(b/126937269): need to escape single quotes (and backslashes).  Also needs support from the parser
      return "\'" + part + "\'";
    }
    else if (GROOVY_KEYWORDS.contains(part)) {
      return "\'" + part + "\'";
    }
    else {
      return part;
    }
  }

  @NotNull
  public static String quotePartsIfNecessary(@NotNull List<String> parts) {
    StringBuilder sb = new StringBuilder();
    boolean firstPart = true;
    for (String part: parts) {
      if (!firstPart) {
        sb.append('.');
      }
      else {
        firstPart = false;
      }
      sb.append(quotePartIfNecessary(part));
    }
    return sb.toString();
  }

  @NotNull
  static String quotePartsIfNecessary(@NotNull ExternalNameInfo info) {
    List<String> parts = info.externalNameParts;
    if (info.verbatim) {
      return String.join(".", parts);
    }
    return quotePartsIfNecessary(parts);
  }

  @Nullable
  static PsiElement createNameElement(@NotNull GradleDslElement context, @NotNull String name) {
    GroovyPsiElementFactory factory = getPsiElementFactory(context);
    if (factory == null) {
      return null;
    }

    String str = name + " 1";
    GrExpression expression = factory.createExpressionFromText(str);
    if (expression instanceof GrApplicationStatement) {
      return ((GrApplicationStatement)expression).getInvokedExpression();
    }
    else {
      return null;
    }
  }

  static void maybeUpdateName(@NotNull GradleDslElement element, GroovyDslWriter writer) {
    GradleNameElement nameElement = element.getNameElement();

    String localName = nameElement.getLocalName();
    if (localName == null || localName.isEmpty()) return;
    if (localName.equals(nameElement.getOriginalName())) return;

    PsiElement oldName = element.getNameElement().getNamedPsiElement();
    if (oldName == null) return;

    GradleDslElement parent = element.getParent();
    if (parent != null) {
      @NotNull Set<ExternalToModelMap.Entry> modelEntries = parent.getExternalToModelMap(writer).getEntrySet();
      for (ExternalToModelMap.Entry entry : modelEntries) {
        if (entry.modelEffectDescription.property.name.equals(nameElement.getOriginalName())) {
          LOG.warn(new UnsupportedOperationException("trying to update a property: " + nameElement.getOriginalName()));
          return;
        }
      }
    }
    String newName = GradleNameElement.unescape(localName);

    PsiElement newElement;
    if (oldName instanceof PsiNamedElement) {
      PsiNamedElement namedElement = (PsiNamedElement)oldName;
      namedElement.setName(newName);
      newElement = namedElement;
    }
    else {
      PsiElement psiElement = createNameElement(element, quotePartIfNecessary(newName));
      if (psiElement == null) {
        return;
      }
      newElement = oldName.replace(psiElement);
    }
    element.getNameElement().commitNameChange(newElement, writer, element.getParent());
  }

  @Nullable
  static PsiElement getPsiElementForAnchor(@NotNull PsiElement parent, @NotNull GradleDslAnchor dslAnchor) {
    PsiElement anchorAfter;
    if (dslAnchor instanceof GradleDslAnchor.Start) {
      anchorAfter = null;
    }
    else if (dslAnchor instanceof GradleDslAnchor.After dslAnchorAfter) {
      anchorAfter = findLastPsiElementIn(dslAnchorAfter.getDslElement());
    }
    else {
      throw new IllegalStateException("dslAnchor neither a Start nor an After anchor");
    }
    if (anchorAfter == null && parent instanceof GrClosableBlock) {
      return adjustForCloseableBlock((GrClosableBlock)parent);
    }
    else {
      while (anchorAfter != null && !(anchorAfter instanceof PsiFile) && anchorAfter.getParent() != parent) {
        anchorAfter = anchorAfter.getParent();
      }
      return anchorAfter instanceof PsiFile
             ? (parent instanceof GrClosableBlock) ? adjustForCloseableBlock((GrClosableBlock)parent) : null
             : anchorAfter;
    }
  }

  private static PsiElement adjustForCloseableBlock(@NotNull GrClosableBlock block) {
    PsiElement element = block.getFirstChild();
    // Skip the first non-empty element, this is normally the '{' of a closable block.
    if (element != null) {
      element = element.getNextSibling();
    }

    // Find the last empty (no newlines or content) child after the initial element.
    while (element != null) {
      element = element.getNextSibling();
      if (element != null && (Strings.isNullOrEmpty(element.getText()) || element.getText().matches("[\\t ]+"))) {
        continue;
      }
      break;
    }

    return element == null ? null : element.getPrevSibling();
  }

  static boolean needToCreateParent(@Nullable GradleDslElement parent) {
    return parent != null && parent.getPsiElement() == null && !(parent instanceof ProjectPropertiesDslElement);
  }

  static boolean closableBlockNeedsNewline(@NotNull GrClosableBlock block) {
    PsiElement start = block.getLBrace();
    PsiElement end = block.getRBrace();
    if (end == null) return false;
    for (PsiElement element = start; element != end; element = element.getNextSibling()) {
      if (element.getNode().getElementType().equals(GroovyTokenTypes.mNLS)) {
        return false;
      }
    }
    return true;
  }

  static List<GradleReferenceInjection> findInjections(@NotNull GradleDslSimpleExpression context,
                                                       @NotNull PsiElement psiElement,
                                                       boolean includeUnresolved) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (psiElement instanceof GrReferenceExpression || psiElement instanceof GrIndexProperty) {
      String name = context.getDslFile().getParser().convertReferencePsi(context, psiElement);
      GradleDslElement element = context.resolveInternalSyntaxReference(name, true);
      return ImmutableList.of(new GradleReferenceInjection(context, element, psiElement, name));
    }

    if (!(psiElement instanceof GrString)) {
      return Collections.emptyList();
    }

    List<GradleReferenceInjection> injections = new ArrayList<>();
    GrStringInjection[] grStringInjections = ((GrString)psiElement).getInjections();
    for (GrStringInjection injection : grStringInjections) {
      if (injection != null) {
        String name = getInjectionName(injection);
        if (name != null) {
          // TODO(xof): It seems bizarre to need to get the injection content as a String, in order to resolve that string in the
          //  external syntax to our internal data, but: although a GrStringInjection can in theory hold arbitrary Groovy code, the
          //  Psi merely contains a ClosableBlock: there is no way to usefully visit the contents of the string injection.  It might
          //  nevertheless be better to integrate that into psiToName rather than special-case getInjectionName, if only to be able
          //  to remove this call to the String form of resolveExternalSyntaxReference.
          GradleDslElement referenceElement = context.resolveExternalSyntaxReference(name, true);
          if (includeUnresolved || referenceElement != null) {
            injections.add(new GradleReferenceInjection(context, referenceElement, injection, name));
          }
        }
      }
    }
    return injections;
  }

  static void createAndAddClosure(@NotNull GradleDslClosure closure, @NotNull GradleDslElement element) {
    GroovyPsiElement psiElement = ensureGroovyPsi(element.getPsiElement());
    if (psiElement == null) {
      return;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
    GrClosableBlock block = factory.createClosureFromText("{ }");
    PsiElement newElement;
    if (psiElement instanceof GrApplicationStatement) {
      GrArgumentList argumentList = ((GrApplicationStatement)psiElement).getArgumentList();
      newElement = argumentList.addAfter(block, argumentList.getLastChild());
    }
    else {
      psiElement.addAfter(factory.createWhiteSpace(), psiElement.getLastChild());
      newElement = psiElement.addAfter(block, psiElement.getLastChild());
    }
    closure.setPsiElement(newElement);
    closure.applyChanges();
    element.setParsedClosureElement(closure);
    element.setNewClosureElement(null);
  }

  static void deletePsiElement(@NotNull GradleDslElement context, @Nullable PsiElement psiElement) {
    if (psiElement == null || !psiElement.isValid()) {
      return;
    }

    PsiElement parent = psiElement.getParent();
    psiElement.delete();

    maybeDeleteIfEmpty(parent, context);

    // Now we have deleted all empty PsiElements in the Psi tree, we also need to make sure
    // to clear any invalid PsiElements in the GradleDslElement tree otherwise we will
    // be prevented from recreating these elements.
    removePsiIfInvalid(context);
  }
}

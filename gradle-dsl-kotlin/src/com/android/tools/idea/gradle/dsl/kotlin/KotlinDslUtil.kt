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
package com.android.tools.idea.gradle.dsl.kotlin

import com.android.tools.idea.gradle.dsl.api.ext.InterpolatedText
import com.android.tools.idea.gradle.dsl.api.ext.RawText
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationDslElement
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.KTS_KNOWN_CONFIGURATIONS
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslAnchor
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSettableExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleScriptFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile
import com.android.tools.idea.gradle.dsl.parser.findLastPsiElementIn
import com.android.tools.idea.gradle.dsl.parser.getNextValidParent
import com.android.tools.idea.gradle.dsl.parser.isDomainObjectConfiguratorMethodName
import com.android.tools.idea.gradle.dsl.parser.removePsiIfInvalid
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.KtNodeTypes.ARRAY_ACCESS_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.STRING_TEMPLATE
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.lexer.KtTokens.LPAR
import org.jetbrains.kotlin.lexer.KtTokens.WHITESPACES
import org.jetbrains.kotlin.lexer.KtTokens.WHITE_SPACE
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getContentRange
import java.math.BigDecimal
import java.util.regex.Pattern
import kotlin.reflect.KClass

private val LOG = Logger.getInstance("KotlinDslUtil")

internal fun String.addQuotes() = "\"$this\""

internal fun KtCallExpression.isBlockElement(converter: GradleDslNameConverter, parent: GradlePropertiesDslElement): Boolean {
  val zeroOrOneClosures = lambdaArguments.size < 2
  val argumentsList = valueArgumentList?.arguments
  val namedDomainBlockReference = parent is GradleDslNamedDomainContainer && argumentsList?.let { it.size == 1 && isDomainObjectConfiguratorMethodName(this.name()) } ?: false
  val zeroArguments = argumentsList == null || argumentsList.isEmpty()
  val knownBlockForParent = zeroArguments &&
                            (listOf("allprojects", APPLY_BLOCK_NAME, EXT.name).contains(this.name()) ||
                             parent is ConfigurationDslElement || // see special-case in SharedParserUtils.getPropertiesElement
                             parent.getChildPropertiesElementDescription(converter, this.name()) != null)
  return zeroOrOneClosures && (namedDomainBlockReference || knownBlockForParent)
}

internal fun GradleScriptFile.transitivelyApplies(file: GradleScriptFile, seen: MutableSet<GradleScriptFile> = mutableSetOf()): Boolean {
  return when {
    file == this -> true
    seen.contains(this) -> false
    else -> { seen.add(this); this.applyDslElement.any { it.transitivelyApplies(file, seen) } }
  }
}

fun convertToExternalTextValue(
  context: GradleDslSimpleExpression,
  applyContext: GradleDslFile,
  referenceText : String,
  forInjection: Boolean
) : String {
  val referenceElement = context.resolveInternalSyntaxReference(referenceText, false) ?: return referenceText

  // Get the resolvedReference value type that might be used for the final cast.
  return convertToExternalTextValue(referenceElement, context, applyContext, forInjection) ?: referenceText
}

internal fun convertToExternalTextValue(dslReference: GradleDslElement,
                                        context: GradleDslSimpleExpression,
                                        applyContext: GradleDslFile,
                                        forInjection: Boolean): String? {
  // TODO(karimai): what if the type needs to be imported ?
  val className = if (dslReference is GradleDslLiteral) dslReference.value?.javaClass?.kotlin?.simpleName else null
  val externalName = StringBuilder()
  var currentParent = dslReference.parent
  var useProjectPrefix = false

  // Trace parents to be used for reference resolution.
  val resolutionElements = ArrayList<GradleDslElement>()
  // TODO(xof): we need the same logic as in the Groovy here, to stop adding parents once the expression context's scope contains them
  //  (modulo possible confusions between lexical scope and model hierarchy)
  resolutionElements.add(dslReference)
  while (currentParent?.parent != null) {
    resolutionElements.add(0, currentParent)
    currentParent = currentParent.parent
  }

  // Now we Reached the dslFile level.
  // We need to add a prefix if we are applying the reference from a parent dslFile context.
  if (currentParent is GradleScriptFile && ((context.dslFile) as? GradleScriptFile)?.transitivelyApplies(currentParent) != true) {
    // If we are applying a property from rootProject => we only need rootProjectPrefix.
    if (currentParent.name == ":") {
      externalName.append("rootProject.")
    }
    else {
      // We can only apply references from parent modules, so walk the context parent modules until we hit the reference dslFile.
      (context.dslFile as? GradleBuildFile)?.let {
        var currentContextParent = it
        do {
          externalName.append("parent.")
          currentContextParent = currentContextParent.parentModuleBuildFile ?: break
        }
        while (currentContextParent != currentParent)
      }
    }
  }
  // We also need to add a prefix if we are referring to a Version Catalog entity from a build script.  (And if we're referring
  // to something under [libraries], we should elide the `libraries` from the external text).
  else if (currentParent is GradleVersionCatalogFile && context.dslFile is GradleScriptFile) {
    externalName.append(currentParent.catalogName).append(".")
    if (resolutionElements[0].name == "libraries") resolutionElements.removeAt(0)
  }
  else {
    // This is specific for extra properties: If we are trying to use the reference from a scope that has a dedicated extra block, we need
    // to use a "project" prefix so that we look for the property in the build file extra scope instead of the current one.
    // TODO(karimai): this is dangerous as we parse properties declared within a different scope that the build script,
    //  so we might break the project if we use the following assumption for such properties.
    //  Limit extra properties parsing to the build file scope only for now.
    useProjectPrefix = true
  }

  // Now we can start appending names from the resolved reference file context.
  var parentElement: GradleDslElement? = null
  val extraArraySyntax: Boolean = currentParent != context.dslFile

  for (currentElement in resolutionElements) {
    // Get the external name for the resolve reference.
    val elementExternalName = applyContext.parser.externalNameForParent(currentElement.name, currentElement.parent!!).externalNameParts.joinToString(".")
    when {
      parentElement == null -> Unit
      currentElement.dslFile is GradleVersionCatalogFile -> externalName.append(".")
      parentElement is GradleDslExpressionMap -> externalName.append("[\"")
      parentElement is GradleDslExpressionList -> externalName.append("[")
      parentElement is ExtDslElement -> {
        if (extraArraySyntax) externalName.append("[\"")
      }
      parentElement is BuildScriptDslElement -> Unit
      // TODO(xof): this conditional (on isNotEmpty) should be unconditional, but we sometimes have spurious parents in resolutionElements
      //  (see the note by the declaration of resolutionElements above)
      else -> if (externalName.isNotEmpty()) externalName.append(".")
    }
    when {
      currentElement.dslFile is GradleVersionCatalogFile -> {
        externalName.append(elementExternalName.split('_', '-', '.').joinToString("."))
      }
      currentElement is ExtDslElement -> if (extraArraySyntax) {
        externalName.append("extra")
      }
      currentElement is GradleDslExpressionMap -> {
        externalName.append(elementExternalName)
        fun maybeCast(close: String) {
          externalName.append(close)
          val updatedName = "($externalName as Map<*, *>)"
          externalName.clear().append(updatedName)
        }
        when (parentElement) {
          is GradleDslExpressionMap -> maybeCast("\"]")
          is GradleDslExpressionList -> maybeCast("]")
          is ExtDslElement -> {
            if (extraArraySyntax) maybeCast("\"]")
          }
        }
      }
      currentElement is GradleDslExpressionList -> {
        externalName.append(elementExternalName)
        fun maybeCast(close: String) {
          externalName.append(close)
          val updatedName = "($externalName as List<*>)"
          externalName.clear().append(updatedName)
        }
        when (parentElement) {
          is GradleDslExpressionMap -> maybeCast("\"]")
          is GradleDslExpressionList -> maybeCast("]")
          is ExtDslElement -> {
            if (extraArraySyntax) maybeCast("\"]")
          }
        }
      }
      currentElement is GradleDslLiteral -> {
        val useTypeCast = !forInjection && currentParent !is GradleVersionCatalogFile && currentParent != context.dslFile && className != null
        when (parentElement) {
          is GradleDslExpressionMap -> {
            externalName.append(elementExternalName)
            if (currentParent !is GradleVersionCatalogFile) externalName.append("\"]")
            if (useTypeCast) externalName.append(" as $className")
          }
          is GradleDslExpressionList -> {
            val i = parentElement.simpleExpressions.indexOf(currentElement)
            externalName.append("$i]")
            if (useTypeCast) externalName.append(" as $className")
          }
          is ExtDslElement -> {
            if (extraArraySyntax) {
              externalName.append("$elementExternalName\"]")
              if (useTypeCast) externalName.append(" as $className")
            }
            else if (currentElement.name == currentElement.fullName) {
              externalName.append(elementExternalName)
            }
            else {
              // This is for extra properties declared using array access expressions
              // TODO(karimai): this workaround assumes we don't currently support properties defined within extensionAware containers.
              //  Decide for a better support to these properties and update this code accordingly.
              if (useProjectPrefix && context.getBlockParent() !is GradleDslFile) externalName.append("project.")
              externalName.append("extra[\"$elementExternalName\"]")
              // If we are using the reference for an assignment, then we should use the type cast.
              if (!forInjection && className != null) externalName.append(" as $className")
            }
          }
          else -> {
            if (currentElement.name == currentElement.fullName) {
              externalName.append(elementExternalName)
            }
          }
        }
      }
      currentElement is GradleDslNamedDomainContainer -> externalName.append(elementExternalName)
      currentElement is GradleDslNamedDomainElement -> externalName.append("getByName(\"$elementExternalName\")")
      else -> {
        // if we have a model property with a transform (so not directly a GradleDslLiteral)
        if (currentElement.modelEffect?.property != null) {
          externalName.append(elementExternalName)
        }
      }
    }
    parentElement = currentElement
  }

  if (externalName.isEmpty()) return null

  val varShouldNotBeWrapped = Pattern.compile("(([a-zA-Z0-9_]\\w*))")
  return when {
    !forInjection -> externalName.toString()
    else -> {
      if (varShouldNotBeWrapped.matcher(externalName.toString()).matches()) "\$$externalName"
      else "\${$externalName}"
    }
  }
}

/**
 * Check if the caller psiElement is a transitive parent for the given psiElement.
 */
internal fun PsiElement?.isParentOf(psiElement: PsiElement) : Boolean {
  var psiElement = psiElement
  while (psiElement != this) {
    psiElement = psiElement.parent ?: return false
  }
  return true
}

internal fun KtStringTemplateExpression.literalContents(): String? {
  val escaper = createLiteralTextEscaper()
  val ssb = StringBuilder()
  return when(escaper.decode(getContentRange(), ssb)) {
    true -> ssb.toString()
    false -> null
  }
}

internal fun KtCallExpression.name() : String? {
  return when (val callee = calleeExpression) {
    null -> null
    is KtSimpleNameExpression -> callee.getReferencedName()
    // This clause arises from inlining KtCallElement.getCallNameExpression(), but we need not handle these (which are
    // superconstructor calls) in the Dsl.  We leave this clause here explicitly in case we later have a need.
    is KtConstructorCalleeExpression -> null
    // TODO(xof): even more ambition: handle "test$foo" as a configuration name
    is KtStringTemplateExpression -> callee.literalContents()
    else -> null
  }
}

internal fun getParentPsi(parentDslElement : GradleDslElement?) : PsiElement? {
  // For extra block, we don't have a psiElement for the dslElement because in Kotlin we don't use the extra block, so we need to add
  // elements straight to the ExtDslElement' parent.
  return if (parentDslElement is ExtDslElement) parentDslElement.parent?.create() else parentDslElement?.create()
}

internal fun GradleDslElement.getBlockParent() : GradleDslElement? {
  when (val parent = this.parent ?: return null) {
    is ExtDslElement -> return parent.getBlockParent()
    is GradleDslElementList, is GradleDslElementMap, is GradleDslBlockElement, is GradleDslFile -> return parent
    else -> return parent.getBlockParent()
  }
}

internal fun getPsiElementForAnchor(parent : PsiElement, dslAnchor : GradleDslAnchor) : PsiElement? {
  var anchorAfter = when(dslAnchor) {
    is GradleDslAnchor.Start -> null
    is GradleDslAnchor.After -> findLastPsiElementIn(dslAnchor.dslElement)
  }
  if (anchorAfter == null && parent is KtBlockExpression) {
    return adjustForKtBlockExpression(parent)?.prevSibling
  }
  else {
    while (anchorAfter != null && anchorAfter !is PsiFile && anchorAfter.parent != parent) {
      anchorAfter = anchorAfter.parent
    }
    return when (anchorAfter) {
      is PsiFile -> {
        if (parent is KtBlockExpression) {
          adjustForKtBlockExpression(parent)?.prevSibling
        }
        else {
          null
        }
      }
      is KtScript -> anchorAfter.blockExpression.lastChild
      else -> anchorAfter
    }
  }
}

internal fun needToCreateParent(parent: GradleDslElement?): Boolean {
  return parent != null && (parent.psiElement == null && parent !is ExtDslElement && parent !is ProjectPropertiesDslElement)
}

/**
 * Get the first non-empty element in a block expression.
 */
internal fun adjustForKtBlockExpression(blockExpression: KtBlockExpression) : PsiElement? {
  var element = blockExpression.firstChild

  // If the first child of the block is not an empty element, return it.
  if (element != null && !(element.text.isNullOrEmpty() || element is PsiWhiteSpace)) {
    return element
  }

  // Find first non-empty child of the block expression.
  while (element != null) {
    element = element.nextSibling
    if (element != null && (element.text.isNullOrEmpty() || element is PsiWhiteSpace)) {
      // This is an empty element, so continue.
      continue
    }
    // We found an element that is not empty, we exit the loop.
    break
  }
  return element
}

/**
 * Get the block name with the valid syntax in kotlin.
 * If the block was read from the KTS script, we use the `methodName` to create the block name. Otherwise, if we want to write
 * the block in the build file for the first time, we use maybeCreate because it tries to create the element only if it doesn't exist.
 */
internal fun getOriginalName(methodName : String?, blockName : String): String {
  return if (methodName != null) "$methodName(\"$blockName\")" else "maybeCreate(\"$blockName\")"
}

internal fun createLiteral(context: GradleDslSimpleExpression, applyContext : GradleDslFile, value : Any) : PsiElement? {
  when (value) {
    is String ->  {
      var valueText : String?
      if (StringUtil.isQuotedString(value)) {
        val unquoted = StringUtil.unquoteString(value)
        valueText = StringUtil.escapeCharCharacters(unquoted).addQuotes()
      }
      else {
        valueText = StringUtil.escapeCharCharacters(value).addQuotes()
      }
      return KtPsiFactory(applyContext.dslFile.project).createExpressionIfPossible(valueText)
    }
    is Int, is Boolean, is BigDecimal -> return KtPsiFactory(applyContext.dslFile.project).createExpressionIfPossible(value.toString())
    // References are canonicals and need to be resolved first before converted to KTS psiElement.
    is ReferenceTo -> {
      val externalTextValue =
        convertToExternalTextValue(value.referredElement, context, applyContext, false) ?: value.referredElement.fullName
      return KtPsiFactory(applyContext.dslFile.project).createExpressionIfPossible(externalTextValue)
    }
    is InterpolatedText -> {
      val builder = StringBuilder()
      for (interpolation in value.interpolationElements) {
        if (interpolation.textItem != null) {
          builder.append(interpolation.textItem)
        }
        if (interpolation.referenceItem != null) {
          val externalText = convertToExternalTextValue(interpolation.referenceItem!!.referredElement, context, applyContext, true)
          builder.append(externalText ?: interpolation.referenceItem!!.referredElement.fullName)
        }
      }
      return KtPsiFactory(applyContext.dslFile.project).createExpressionIfPossible(builder.toString().addQuotes())
    }
    is RawText -> return KtPsiFactory(applyContext.dslFile.project).createExpressionIfPossible(value.ktsText)
    else -> {
      LOG.warn("Expression '${value}' not supported.")
      return null
    }
  }
}

// Check if this is a block with a methodCall as name, and get the name... e.g. getByName("release") -> "release"
internal fun methodCallBlockName(expression: KtCallExpression): String? {
  val callName = expression.name()
  if (!isDomainObjectConfiguratorMethodName(callName)) return null
  val arguments = expression.valueArgumentList?.arguments ?: return null
  if (arguments.size != 1) return null
  // TODO(xof): we should handle injections / resolving here:
  //  buildTypes.getByName("$foo") { ... }
  //  buildTypes.getByName(foo) { ... }
  val argument = arguments[0].getArgumentExpression()
  return when (argument) {
    is KtStringTemplateExpression -> argument.literalContents()
    else -> null
  }
}

// TODO(xof): I would have liked to mark this with @VisibleForTesting, but the visibility that it would otherwise need
//  would be internal, and we have a lint rule that assumes by default that the visibility should otherwise be private.
//  the lint rule error message references an otherwise parameter, but that I think refers to the
//  android.support.annotation.VisibleForTesting annotation, which we don't have, rather than the
//  com.google.common.annotations.VisibleForTesting annotation which supports no parameters.
fun gradleNameFor(expression: KtExpression): String? {
  val sb = StringBuilder()
  var convertIndexToName = false
  var allValid = true

  // the visitor here is responsible for converting Kts DSL syntax, e.g. sourceSets.getByName("arbitrary").extra["foo"], into
  // something that the DSL (in particular GradleNameElement) is prepared to accept -- in the above example,
  // sourceSets.arbitrary.ext.foo.  We therefore have to pay attention to method calls and to array dereferences, and rewrite
  // all method calls that name blocks, and any array dereferences of the extra properties.
  expression.accept(object: KtTreeVisitorVoid() {
    override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
      expression.arrayExpression?.accept(this, null)
      if (expression.indexExpressions.size != 1) {
        allValid = false
        return
      }
      // translating here between Kts extra property lookup (array access, e.g. extra["foo"])
      // and GradleNameElement's expectation (field dereference, e.g. ext.foo).  Only do this
      // conversion if `extra' is the last thing we've seen in the arrayExpression.
      val index = expression.indexExpressions[0]
      val text = when (index) {
        is KtStringTemplateExpression -> index.literalContents() ?: index.text.also { allValid = false }
        else -> index.text
      }
      if (convertIndexToName) {
        sb.append(".${GradleNameElement.escape(text)}")
        convertIndexToName = false
      }
      else {
        when (index) {
          is KtStringTemplateExpression -> sb.append("[\"${text}\"]")
          else -> sb.append("[${text}]")
        }
      }
    }

    override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
      expression.left.accept(this, null)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
      if (expression.name() == "project" && expression.valueArguments.size == 1) {
        when (expression.valueArguments[0].getArgumentExpression()) {
          is KtStringTemplateExpression -> {
            // TODO(karimai): decide on checking for parameters with interpolations once these are supported.
            sb.append(expression.text.replace("\\s".toRegex(), "").replace("\"", "'"))
          }
          else -> allValid = false
        }
      }
      else {
        val name = methodCallBlockName(expression)
        if (name == null) {
          allValid = false
        }
        else {
          sb.append(GradleNameElement.escape(name))
        }
      }
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
      expression.receiverExpression.accept(this, null)
      sb.append('.')
      expression.selectorExpression?.accept(this, null)
    }

    override fun visitParenthesizedExpression(expression: KtParenthesizedExpression) {
      expression.expression?.accept(this, null)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
      when (expression) {
        is KtSimpleNameExpression -> {
          when (val text = expression.getReferencedName()) {
            "extra" -> { convertIndexToName = true; sb.append("ext") }
            else -> sb.append(GradleNameElement.escape(text))
          }
        }
        else -> super.visitReferenceExpression(expression)
      }
    }

    override fun visitKtElement(element: KtElement) {
      allValid = false
    }
  }, null)

  return if (allValid) sb.toString() else null
}

internal fun findInjections(
  context: GradleDslSimpleExpression,
  psiElement: PsiElement,
  includeResolved: Boolean,
  injectionElement: PsiElement? = null
): MutableList<GradleReferenceInjection> {
  val noInjections = mutableListOf<GradleReferenceInjection>()
  val injectionPsiElement = injectionElement ?: psiElement
  when (psiElement) {
    // foo, KotlinCompilerVersion, android.compileSdkVersion
    is KtNameReferenceExpression, is KtDotQualifiedExpression -> {
      val name = context.dslFile.parser.convertReferencePsi(context, psiElement)
      val element = context.resolveInternalSyntaxReference(name, true)
      return mutableListOf(GradleReferenceInjection(context, element, injectionPsiElement, name))
    }
    // extra["PROPERTY_NAME"], someMap["MAP_KEY"], someList[0], rootProject.extra["kotlin_version"]
    is KtArrayAccessExpression -> {
      if (psiElement.arrayExpression == null) return noInjections
      val name = context.dslFile.parser.convertReferencePsi(context, psiElement)
      val element = context.resolveInternalSyntaxReference(name, true)
      return mutableListOf(GradleReferenceInjection(context, element, injectionPsiElement, name))
    }
    // "foo bar", "foo $bar", "foo ${extra["PROPERTY_NAME"]}"
    is KtStringTemplateExpression -> {
      if (!psiElement.hasInterpolation()) return noInjections
      return psiElement.entries
        .flatMap { entry -> when(entry) {
          // any constant portion of a KtStringTemplateExpression
          is KtLiteralStringTemplateEntry -> noInjections
          // short-form interpolation $foo -- we know we have just a name, which we can resolve.
          is KtSimpleNameStringTemplateEntry -> entry.expression?.let { findInjections(context, it, includeResolved, entry) } ?: noInjections
          // long-form interpolation ${...} -- compute injections for the contained expression.
          is KtBlockStringTemplateEntry -> entry.expression?.let { findInjections(context, it, includeResolved, entry) } ?: noInjections
          else -> noInjections
        }}
        .toMutableList()
    }
    is KtBinaryExpressionWithTypeRHS -> return when (val contentExpression = psiElement.left) {
      is KtArrayAccessExpression -> findInjections(context, contentExpression, includeResolved, injectionElement)
      else -> noInjections
    }
    is KtCallExpression -> return when {
      isDomainObjectConfiguratorMethodName(psiElement.name()) -> {
        val name = context.dslFile.parser.convertReferencePsi(context, psiElement)
        val element = context.resolveInternalSyntaxReference(name, true)
        mutableListOf(GradleReferenceInjection(context, element, injectionPsiElement, name))
      }
      else -> noInjections
    }
    else -> return noInjections
  }
}

/**
 * Delete the psiElement for the given dslElement.
 */
internal fun deletePsiElement(dslElement : GradleDslElement, psiElement : PsiElement?) {
  if (psiElement == null || !psiElement.isValid) return
  val parent = psiElement.parent
  // If the psiElement is a KtValueArgument, we use the removeArgument method provided by the KTS psi that handles removing COMMAs between arguments.
  if (psiElement is KtValueArgument) {
    (parent as KtValueArgumentList).removeArgument(psiElement)
  }
  else {
    psiElement.delete()
  }
  maybeDeleteIfEmpty(parent, dslElement)

  // Clear all invalid psiElements in the GradleDslElement tree.
  removePsiIfInvalid(dslElement)
}

internal fun maybeDeleteIfEmpty(psiElement: PsiElement, dslElement: GradleDslElement) {
  val parentDslElement = dslElement.parent
  // We don't want to delete lists and maps if they are empty.
  // For maps, we want to allow deleting a map inside another map, which means that if a map is empty but is inside another map,
  // we should allow deleting it
  if (((parentDslElement is GradleDslExpressionList && !parentDslElement.shouldBeDeleted()) ||
       (parentDslElement is GradleDslExpressionMap && !parentDslElement.shouldBeDeleted()))
      && parentDslElement.psiElement == psiElement) {
    return
  }
  deleteIfEmpty(psiElement, dslElement)
}

internal fun deleteIfEmpty(psiElement: PsiElement?, containingDslElement: GradleDslElement) {

  var psiParent = psiElement?.parent ?: return
  val dslParent = getNextValidParent(containingDslElement)

  if (!psiElement.isValid()) {
    // SKip deletion.
  }
  else {
    when (psiElement) {
      is KtScriptInitializer -> {
        if (psiElement.children.isEmpty()) {
          psiElement.delete()
        }
      }
      is KtBinaryExpression -> {  // This includes assignment expressions and maps elements.
        if (psiElement.right == null) psiElement.delete()
      }
      is KtBlockExpression -> {  // This represents Blocks structure without the { }.
        // Check if the block is empty, then delete it.
        // We should not delete a block if it has KtScript as parent because a script should always have a block even if empty.
        if ((dslParent == null || dslParent.isInsignificantIfEmpty) && psiElement.isNullExpressionOrEmptyBlock() &&
            psiParent !is KtScript) {
          psiElement.delete()
        }
      }
      is KtLambdaArgument -> {
        if (psiElement.getArgumentExpression() == null) {
          psiElement.delete()
        }
      }
      is KtLambdaExpression -> {
        if (psiElement.children.isEmpty()) {
          psiElement.delete()
        }
      }
      is KtFunctionLiteral -> {
        val bodyExpression = psiElement.bodyExpression
        if (bodyExpression == null || bodyExpression.isNullExpressionOrEmptyBlock()) {
          psiElement.delete()
          // If the parent is a KtLambdaExpression, delete it because KtLambdaExpression.getFunctionLiteral() cannot be null.
          if (psiParent is KtLambdaExpression) {
            val newParent = psiParent.parent
            psiParent.delete()
            psiParent = newParent
          }
        }
      }
      is KtCallExpression -> {  // This includes lists and maps as well.
        val argumentsList = psiElement.valueArgumentList
        val blockArguments = psiElement.lambdaArguments
        // Handle cases where the element is a block with callExpression as name (ex: getByName("debug")) => arguments are never empty
        // but if the block {} expression is empty, then we should delete the psiElement.

        // e.g. foo (block reference without configuration) or "foo" (implicit getByName without configuration)
        if (((argumentsList == null) || argumentsList.arguments.isEmpty()) && blockArguments.isEmpty()) {
          psiElement.delete()
        }
        // e.g. getByName("foo") or getByName("foo") { }
        else if (argumentsList?.arguments?.size == 1) {
          if (blockArguments.size <= 1 && containingDslElement.isBlockElement && dslParent != null
              && containingDslElement is GradleDslNamedDomainElement && containingDslElement.methodName == psiElement.name()) {
            psiElement.delete()
          }
        }
      }
      is KtValueArgument -> {
        if (psiElement.getArgumentExpression() == null) {
          (psiParent as KtValueArgumentList).removeArgument(psiElement)
          // Delete any space that might remain after the argument deletion.
          if (psiParent.firstChild.node.elementType == LPAR && psiParent.firstChild.nextSibling.node.elementType == WHITE_SPACE) {
            psiParent.firstChild.nextSibling.delete()
          }
        }
      }
      is KtPropertyDelegate -> {
        if (psiElement.expression == null) psiElement.delete()
      }
      is KtProperty -> {
        // This applies for variables and properties.
        if (psiElement.delegate == null && psiElement.initializer == null) {
          psiElement.delete()
        }
      }
      is KtDotQualifiedExpression -> {
        if (psiElement.selectorExpression == null) {
          psiElement.delete()
        }
      }
    }
  }

  // If psiParent is a child of the parentDsl psiElement, then we should check if psiParent is empty and should be deleted.
  // For KtValueArgumentList : we can't delete them without deleting the callExpression, because otherwise, if maybeDeleteIfEmpty() called
  // for the psiParent (i.e. callExpression) returns that the callExpression cannot be deleted, we will end up having just the
  // callExpression' reference name, which is invalid. This apply for example to maps that we might not want to delete.
  if ((psiElement is KtValueArgumentList || !psiElement.isValid) && dslParent != null && (dslParent.isInsignificantIfEmpty || dslParent.psiElement.isParentOf(psiParent))) {
    // If we are deleting the dslElement parent itself ((psiElement == dslParent.psiElement)), move to dslParent as it's the new element
    // to be deleted.
    maybeDeleteIfEmpty(psiParent, if (psiElement == dslParent.psiElement) dslParent else containingDslElement)
  }
}

/**
 * Given a literal expression, create it's PsiElement and add it to it's parent psiElement.
 */
internal fun createListElement(expression : GradleDslSettableExpression) : PsiElement? {
  val parent = expression.parent ?: return null
  val parentPsi = parent.create() ?: return null

  val expressionPsi = expression.unsavedValue ?: return null

  val added  = createPsiElementInsideList(parent, expression, parentPsi, expressionPsi) ?: return null
  expression.psiElement = added
  expression.commit()
  return expression.psiElement
}

/**
 * Given an literal that is a map element, create the corresponding psiElement and add it to the map (parent) psiElement, and update
 * the expression value.
 */
internal fun createMapElement(expression : GradleDslSettableExpression) : PsiElement? {
  val parent = requireNotNull(expression.parent as? GradleDslExpressionMap)
  val parentPsiElement = parent.create() as? KtCallExpression ?: return null

  expression.psiElement = parentPsiElement
  val expressionValue = expression.unsavedValue ?: return null

  val psiFactory = KtPsiFactory(parentPsiElement.project)
  val expressionRightValue =
    if (expressionValue is KtConstantExpression || expressionValue is KtNameReferenceExpression) expressionValue.text
    else StringUtil.unquoteString(expressionValue.text).addQuotes()
  val argumentStringExpression = when {
    parent.asNamedArgs -> "${expression.name}=$expressionRightValue"
    else -> "${expression.name.addQuotes()} to $expressionRightValue"
  }

  val mapArgument = psiFactory.createExpression(argumentStringExpression)

  val argumentValue = psiFactory.createArgument(mapArgument)

  val added =
    parentPsiElement.valueArgumentList?.addArgumentAfter(argumentValue, parentPsiElement.valueArgumentList?.arguments?.lastOrNull())

  val argumentExpression = added?.getArgumentExpression() as? KtBinaryExpression // Map elements are KtBinaryExpression.
  val expressionRight = argumentExpression?.right
  if (argumentExpression == null || expressionRight == null) return null

  expression.setExpression(expressionRight)
  expression.commit()
  expression.reset()
  return expression.psiElement

}

/**
 * Given an existing infix expression, add a property to it.
 */
internal fun createInfixElement(literal: GradleDslLiteral): PsiElement? {
  val parent = literal.parent as? GradleDslInfixExpression ?: return null
  val parentPsi = parent.create() ?: return null

  val expressionPsi = literal.unsavedValue ?: return null

  val psiFactory = KtPsiFactory(parentPsi.project)
  // FIXME(xof): escaping of name
  val newInfixExpression = psiFactory.createExpression("${parentPsi.text} ${literal.name} ${expressionPsi.text}") as KtBinaryExpression
  val newParentPsi = parentPsi.replace(newInfixExpression) as KtBinaryExpression
  parent.psiElement = newParentPsi

  literal.psiElement = newParentPsi.right
  literal.commit()
  literal.reset()
  return literal.psiElement
}

/**
 * Add an argument to a GradleDslList (parentDslElement). The PsiElement of the list (parentPsiElement) can either be :
 * KtCallExpression : for cases where we have a list in Kotlin (listOf())
 * KtBinaryExpression : for cases where we have binary expressions ( ex: map arguments or assignment expression)
 * KtValueArgument : when we have constructed a DslExpressionList out of a single-argument method call (e.g. flavorDimensions("abi"))
 * KtValueArgumentList : for all the other cases (ex  : KtCallExpression arguments)
 * Others : not handled.
 */
internal fun createPsiElementInsideList(parentDslElement : GradleDslElement,
                                        dslElement : GradleDslSettableExpression,
                                        parentPsiElement: PsiElement,
                                        psiElement: PsiElement) : PsiElement? {
  val parentPsiElement = when (parentPsiElement){
    is KtCallExpression -> parentPsiElement.valueArgumentList ?: return null
    is KtBinaryExpression -> (parentPsiElement.right as? KtCallExpression)?.valueArgumentList ?: return null
    is KtValueArgument -> parentPsiElement.parent as? KtValueArgumentList ?: return null
    is KtValueArgumentList -> parentPsiElement
    else -> return null
  }

  val anchor = parentDslElement.requestAnchor(dslElement)

  // Create a valueArgument to add to the list.
  val psiFactory = KtPsiFactory(parentPsiElement.project)
  // support named argument. ex: plugin = "kotlin-android".
  val argument = when {
    parentDslElement is GradleDslMethodCall && dslElement.name.isNotEmpty() ->
      psiFactory.createArgument(psiElement as? KtExpression, Name.identifier(dslElement.name))
    else -> psiFactory.createArgument(psiElement as? KtExpression)
  }

  // If the dslElement has an anchor that is not null and that the list is not empty, we add it to the list after the anchor ;
  // otherwise, we add it at the beginning of the list.
  if (parentPsiElement.arguments.isNotEmpty() && anchor is GradleDslAnchor.After) {
    val anchorPsi =
      anchor.dslElement.psiElement as? KtValueArgument ?:
      getNextValidParentPsiElement(anchor.dslElement.psiElement, KtValueArgument::class) as? KtValueArgument ?: return null

    return parentPsiElement.addArgumentAfter(argument, anchorPsi)
  }
  return parentPsiElement.addArgumentBefore(argument, parentPsiElement.arguments.firstOrNull()).getArgumentExpression()
}

/**
 * Return, if found, first parent psiElement that is of the type eClass, otherwise, return null.
 */
internal fun getNextValidParentPsiElement(psiElement: PsiElement?, eClass: KClass<*>) : PsiElement? {
  var psiElement = psiElement ?: return null
  do {
    psiElement = psiElement.parent ?: return null
  } while (!eClass.isInstance(psiElement))

  return psiElement
}

internal fun getKtBlockExpression(psiElement: PsiElement) : KtBlockExpression? {
  if (psiElement is KtBlockExpression) return psiElement
  return (psiElement as? KtCallExpression)?.lambdaArguments?.lastOrNull()?.getLambdaExpression()?.bodyExpression
}

internal fun maybeUpdateName(element : GradleDslElement, writer: KotlinDslWriter) {
  val nameElement = element.nameElement

  val localName = nameElement.localName ?: return
  if (localName == "") return
  if (localName == nameElement.originalName) return

  val oldName = nameElement.namedPsiElement ?: return

  val modelEntries = element.parent?.getExternalToModelMap(writer)?.entrySet
  if (modelEntries != null) {
    for (entry in modelEntries) {
      if (entry.modelEffectDescription.property.name == nameElement.originalName) {
        LOG.warn(UnsupportedOperationException("trying to updateName a property: ${nameElement.originalName}"))
        return
      }
    }
  }

  val newName = GradleNameElement.unescape(localName)

  val newElement : PsiElement
  if (oldName is PsiNamedElement) {
    oldName.setName(newName)
    newElement = oldName
  }
  else {
    val project = element.psiElement?.project ?: return
    val factory = KtPsiFactory(project)
    val psiElement: PsiElement =
      when (oldName.node.elementType) {
        IDENTIFIER -> factory.createNameIdentifierIfPossible(newName)
        STRING_TEMPLATE -> when {
          element.parent is DependenciesDslElement && KTS_KNOWN_CONFIGURATIONS.contains(newName) ->
            factory.createExpressionIfPossible(newName)
          else -> factory.createExpressionIfPossible(StringUtil.unquoteString(newName).addQuotes())
        }
        ARRAY_ACCESS_EXPRESSION -> when {
          newName.startsWith("ext.") -> {
            // TODO(b/148769031): this is a bandage over the fact that we don't (yet) have a principled translation from Psi to Dsl to Psi.
            //  We parse extra["foo"] to ext.foo, so when writing we have to do the reverse.
            val extraExpression = "extra[\"${newName.substring("ext.".length, newName.length)}\"]"
            factory.createExpressionIfPossible(extraExpression)
          }
          else -> factory.createExpressionIfPossible(newName)
        }
        else -> when {
          element.parent is DependenciesDslElement && !KTS_KNOWN_CONFIGURATIONS.contains(newName) ->
            factory.createExpressionIfPossible(StringUtil.unquoteString(newName).addQuotes())
          else -> factory.createExpressionIfPossible(newName)
        }
      } ?: return

    // For Kotlin, committing changes is a bit different, and if the psiElement is invalid, it throws an exception (unlike Groovy), so we
    // need to check if the oldName is still valid, otherwise, we use the psiElement created to update the name.
    if (!oldName.isValid) {
      element.nameElement.commitNameChange(psiElement, writer, element.parent)
      return
    }
    else {
      newElement = oldName.replace(psiElement)
    }
  }

  element.nameElement.commitNameChange(newElement, writer, element.parent)

  if (element is GradleDslNamedDomainElement) {
    val project = element.psiElement?.project ?: return
    val factory = KtPsiFactory(project)
    val callExpression = newElement.parentOfType<KtCallExpression>() ?: return
    val calleeExpression = callExpression.calleeExpression ?: return
    val newMethodName = when (val parent = element.parent) {
      // TODO(xof): to go even further beyond the call of duty, we should in fact check the contents of parent for colliding names
      //  before the current element.
      is GradleDslNamedDomainContainer -> if (parent.implicitlyExists(newName)) "getByName" else "create"
      else -> element.methodName
    }
    if (element.methodName != newMethodName) {
      val newCalleeExpression = (factory.createExpressionIfPossible("$newMethodName()") as? KtCallExpression)?.calleeExpression ?: return
      (calleeExpression as PsiElement).replace(newCalleeExpression)
      element.methodName = newMethodName
    }
  }
}

internal fun createAndAddClosure(closure : GradleDslClosure, element : GradleDslElement) {
  // If element is a GradleDslMethodCall, then we should consider that this refers to a nested KtCallExpression psiElement in the KTS file
  // (ex: implementation(file())). In such case, element has as psiElement the part that corresponds to "file()" only, and we should not
  // add the block to it but rather to the parent element, which would result in implementation(file()) {}.
  var psiElement = element.psiElement
  if (psiElement !is KtCallExpression || psiElement.name() != element.name) {
    psiElement =
      (if (element is GradleDslMethodCall) getNextValidParentPsiElement(psiElement, KtCallExpression::class) else element.psiElement)
      ?: return
  }
  if (psiElement is KtCallExpression && psiElement.name() != element.name) return

  val psiFactory = KtPsiFactory(psiElement.project)
  psiElement.addAfter(psiFactory.createWhiteSpace(), psiElement.lastChild)
  val block = (psiFactory.createExpression("foo() { }") as? KtCallExpression)?.lambdaArguments?.firstOrNull() ?: return
  val addedBlock =
    (psiElement.addAfter(block, psiElement.lastChild) as? KtLambdaArgument)?.getLambdaExpression()?.bodyExpression ?: return
  closure.psiElement = addedBlock
  closure.applyChanges()
  element.setParsedClosureElement(closure)
  element.setNewClosureElement(null)
}

/**
 * Create the PsiElement for a list that is an argument of a map. In kotlin each map argument is a KtBinaryExpression.
 */
internal fun createBinaryExpression(expressionList : GradleDslExpressionList) : PsiElement? {
  val parent = expressionList.parent as? GradleDslExpressionMap
  if (parent == null) {
    LOG.warn("Can't create expression for parent not being GradleDslExpressionMap")
    return null
  }

  val parentPsiElement = parent.create() ?: return null

  val psiFactory = KtPsiFactory(parentPsiElement.project)
  val listName = expressionList.name
  if (listName.isEmpty()) {
    LOG.warn("The list Name can't be empty.")
    return null
  }

  val expression = psiFactory.createExpression("\"$listName\" to listOf()") as? KtBinaryExpression ?: return null
  val added : PsiElement?

  val mapPsiElement = when (parentPsiElement) {
    // This is the case when a map is a parameter of a KtCallExpression and use the psiElement of the expression arguments list.
    // Ex. implementation(mapOf()).
    is KtValueArgumentList -> parentPsiElement.arguments[0].getArgumentExpression()
    // This is the case where we can have property = mapOf(), where the map has the psi element of the binary expression.
    is KtBinaryExpression -> requireNotNull(parentPsiElement.right)
    // This is the case where the map dsl element uses it's proper psiElement (i.e. mapOf()).
    is KtCallExpression -> parentPsiElement
    else -> return null
  }

  // Get The map arguments list that will be updated and add a new argument to it.
  val argumentsList = (mapPsiElement as? KtCallExpression)?.valueArgumentList ?: return null
  val mapValueArgument = psiFactory.createArgument(expression)
  val lastArgument = argumentsList.arguments.last()
  added = argumentsList.addArgumentAfter(mapValueArgument, lastArgument)
  expressionList.psiElement = added.getArgumentExpression()
  return expressionList.psiElement
}

internal fun hasNewLineBetween(start : PsiElement, end : PsiElement) : Boolean {
  if(start.parent === end.parent && start.startOffsetInParent <= end.startOffsetInParent) {
    var element = start
    while (element !== end) {
      // WHITE_SPACE represents a group of escape characters and a new line can have spaces for indentation,
      // so we check that it starts with a new line.
      if (element.node.elementType == WHITE_SPACE && element.node.text.startsWith("\n")) {
        return true
      }
      element = element.nextSibling
    }
    return false
  }
  return true
}

internal fun isWhiteSpaceOrNls(element: PsiElement?) = element?.node?.let { WHITESPACES.contains(it.elementType) } ?: false

private fun KtExpression?.isNullExpressionOrEmptyBlock(): Boolean =
  this.isNullExpression()
  || this is KtBlockExpression && this.statements.isEmpty()

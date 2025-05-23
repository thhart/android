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
package com.android.tools.idea.gradle.dsl.parser.declarative

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeArgument
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeArgumentsList
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlockGroup
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFactoryReceiver
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFile
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePair
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePsiFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeReceiverPrefixedFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeSimpleFactory
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.AUGMENTED_ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.METHOD
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.UNKNOWN
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslAnchor
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleScriptFile
import com.android.tools.idea.gradle.dsl.parser.findLastPsiElementIn
import com.android.tools.idea.gradle.dsl.parser.getNextValidParent
import com.android.tools.idea.gradle.dsl.parser.maybeTrimForParent
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_LIST
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_MAP
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_SET
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.util.findParentOfType

class DeclarativeDslWriter(private val context: BuildModelContext) : GradleDslWriter, DeclarativeDslNameConverter {

  override fun getContext(): BuildModelContext = context
  override fun moveDslElement(element: GradleDslElement): PsiElement? = null
  override fun createDslElement(element: GradleDslElement): PsiElement? {
    if (element.isAlreadyCreated()) return element.psiElement
    if (element.isNewEmptyBlockElement()) {
      return null // Avoid creation of an empty block statement.
    }

    val parent = element.parent ?: return null
    val psiElementOfParent = parent.create() ?: return null
    val parentPsiElement = when (psiElementOfParent) {
      is DeclarativeBlock -> psiElementOfParent.blockGroup
      is DeclarativeSimpleFactory -> psiElementOfParent.argumentsList ?: psiElementOfParent
      else -> psiElementOfParent
    }
    val project = parentPsiElement.project
    val factory = DeclarativePsiFactory(project)
    val name = getNameTrimmedForParent(element)
    val externalNameInfo = maybeTrimForParent(element, this)
    val syntax = externalNameInfo.syntax.takeUnless { it == UNKNOWN } ?: element.externalSyntax
    element.externalSyntax = syntax
    val psiElement = when (element) {
      is GradleDslInfixExpression -> factory.createPrefixedFactory()
      is GradleDslExpressionList, is GradleDslExpressionMap ->
        when (syntax) {
          ASSIGNMENT -> factory.createAssignment(name, "\"placeholder\"")
          AUGMENTED_ASSIGNMENT -> factory.createAppendAssignment(name, "\"placeholder\"")
          else -> null
        }
      is GradleDslLiteral ->
         if (parent is GradleDslExpressionMap) {
          // when mapOf("first" to "second") argument is being created
          factory.createArgument(factory.createPair(element.name, element.value))
        } else
        if (parentPsiElement is DeclarativeArgumentsList)
          factory.createArgument(factory.createLiteral(element.value))
        else if (parent is DependenciesDslElement || externalNameInfo.syntax == METHOD)
          factory.createOneParameterFactory(name, "\"placeholder\"")
        else if (parent is GradleDslInfixExpression) {
          // this is only for id("").value("") with restriction to one parameter function call chain
          val literal = factory.createLiteral(element.value)
          factory.createOneParameterFactory(element.name, literal.text)
        } else if (parent is GradleDslExpressionList && parentPsiElement is DeclarativeSimpleFactory) {
          // when listOf("argument") argument is being created
          factory.createArgument(factory.createLiteral(element.value))
        }
        else // default syntax
          factory.createAssignment(name, "\"placeholder\"")
      is GradleDslNamedDomainElement -> element.accessMethodName?.let { factory.createOneParameterFactoryBlock(it, name) }
      is GradleDslElementList, is GradleDslBlockElement, is GradleDslNamedDomainContainer -> factory.createBlock(name)
      is GradleDslMethodCall -> {
        val function = if (element.isDoubleFunction()) {
          val internal = factory.createFactory(element.methodName)
          factory.createOneParameterFactory(name, internal.text)
        }
        else factory.createFactory(name)
        if (parentPsiElement is DeclarativeArgumentsList)
          factory.createArgument(function)
        else function
      }
      else -> null
    }
    psiElement ?: return null

    val anchor = getAnchor(parentPsiElement, element.anchor)
    val addedElement = parentPsiElement.addAfter(psiElement, anchor)

    // after processing
    val comma = factory.createComma()
    when (parentPsiElement) {
      is DeclarativeReceiverPrefixedFactory ->
        // if it's only one element in prefixedFactory it's not fully
        // constructed yet and methods may return exceptions/wrong results
        if(parentPsiElement.children.filterIsInstance<DeclarativeFactoryReceiver>().size > 1 )
          parentPsiElement.addBefore(factory.createDot(), addedElement)
      is DeclarativeBlockGroup -> addedElement.addAfter(factory.createNewline(), null)
      is DeclarativeArgumentsList ->
        if (parentPsiElement.arguments.size > 1)
          parentPsiElement.addBefore(comma, addedElement)
        else Unit
        // TODO add logic for inserting attribute in the first place
    }

    element.psiElement = when (addedElement) {
      is DeclarativeAssignment -> addedElement.value
      is DeclarativeArgument -> addedElement.value
      else -> addedElement
    }

    return element.psiElement
  }

  private fun getNameTrimmedForParent(element: GradleDslElement): String {
    val defaultName = element.name // use this when other mechanisms fail
    val externalNameInfo = maybeTrimForParent(element, this)
    return externalNameInfo.externalNameParts.getOrElse(0){
      // fallback to external naming mechanism for blocks
      val parent = element.parent
      if (parent is GradlePropertiesDslElement) {
        val name = element.nameElement.fullNameParts().lastOrNull() ?: defaultName
        externalNameForPropertiesParent(name, parent)
      }
      else defaultName
    }
  }

  // this is DSL function like `implementation project(":my")` where `implementation` is name and `project` is function name
  private fun GradleDslMethodCall.isDoubleFunction(): Boolean =
    methodName != name && methodName.isNotEmpty()

  private fun getAnchor(parent: PsiElement, dslAnchor: GradleDslAnchor?): PsiElement? {
    val lastParentChild = parent.lastChild
    var anchor = (dslAnchor as? GradleDslAnchor.After)?.let { findLastPsiElementIn(it.dslElement) }
    if (anchor == null && parent is DeclarativeBlockGroup) return parent.blockEntriesStart
    if (anchor == null && parent is DeclarativeArgumentsList) return parent.firstChild
    while (anchor != null && anchor.parent != parent) {
      anchor = anchor.parent
    }
    return anchor ?: lastParentChild
  }

  override fun deleteDslElement(element: GradleDslElement) {
    element.psiElement?.let {
      // For declarative we remove only elements that locates in current file
      // Software type properties editing happens via settings file
      // That's the opposite we do for Kotlin/Groovy where we delete all
      // elements - even APPLIED
      // TODO consider making same behavior for Kotlin/Groovy allprojects content
      //  and declarative software types b/375168954
      if (!isInSameFile(element, it)) return
    }

    deletePsiElement(element)
  }

  private fun isInSameFile(element: GradleDslElement, psi:PsiElement) =
    SharedImplUtil.getContainingFile(psi.node) == getFileThroughDsl(element)

  private fun getFileThroughDsl(element: GradleDslElement): PsiFile? {
    var currentElement:GradleDslElement? = element
    while (currentElement != null && currentElement !is GradleScriptFile) {
      currentElement = currentElement.parent
    }
    return currentElement?.psiElement?.containingFile
  }

  override fun createDslMethodCall(methodCall: GradleDslMethodCall): PsiElement {
    val call = createDslElement(methodCall) as DeclarativeSimpleFactory
    if (methodCall.isDoubleFunction())
      call.argumentsList?.arguments?.forEach {
        (it as? DeclarativeSimpleFactory)?.argumentsList?.let { arg -> methodCall.argumentsElement.psiElement = arg }
      }
    else
      methodCall.argumentsElement.psiElement = call.argumentsList
    methodCall.arguments.forEach { it.create() }
    return call
  }
  override fun applyDslMethodCall(methodCall: GradleDslMethodCall) {
    maybeUpdateName(methodCall)
    methodCall.argumentsElement.applyChanges()
  }

  override fun createDslExpressionList(expressionList: GradleDslExpressionList): PsiElement? {
    val element = expressionList.psiElement
    if (element != null) return element

    val psiElement = createDslElement(expressionList) ?: return null
    val factoryName = getExpressionListFactoryName(expressionList)
    val parent = psiElement.parent
    if (parent is DeclarativeAssignment) {
      val emptyList = DeclarativePsiFactory(psiElement.project).createFactory(factoryName)
      val insertedElement = psiElement.replace(emptyList)
      expressionList.psiElement = insertedElement
    }
    expressionList.expressions.forEach { it.create() }
    return expressionList.psiElement
  }

  private fun getExpressionListFactoryName(expressionList: GradleDslExpressionList) =
    when (expressionList.modelProperty?.type) {
      MUTABLE_LIST -> "listOf"
      MUTABLE_MAP -> "mapOf"
      MUTABLE_SET -> "setOf"
      else -> "listOf"
    }

  override fun applyDslExpressionList(expressionList: GradleDslExpressionList): Unit = maybeUpdateName(expressionList)

  override fun applyDslExpressionMap(expressionMap: GradleDslExpressionMap): Unit = maybeUpdateName(expressionMap)
  override fun applyDslPropertiesElement(element: GradlePropertiesDslElement): Unit = maybeUpdateName(element)

  override fun createDslExpressionMap(expressionMap: GradleDslExpressionMap): PsiElement? {
    val element = expressionMap.psiElement
    if (element != null) return element

    val psiElement = createDslElement(expressionMap) ?: return null
    val emptyMap = DeclarativePsiFactory(psiElement.project).createFactory("mapOf")
    val insertedElement = psiElement.replace(emptyMap)
    expressionMap.psiElement = insertedElement

    return expressionMap.psiElement
  }

  override fun createDslLiteral(literal: GradleDslLiteral) = createDslElement(literal)

  override fun applyDslLiteral(literal: GradleDslLiteral) {
    val psiElement = literal.psiElement ?: return
    val newElement = literal.unsavedValue ?: return
    maybeUpdateName(literal)
    val element =
      when (psiElement) {
        is DeclarativePair -> {
          // "placeholder" here happens because `first` in pair is not wrapped in any type - probably should be atomic_literal
          val newPair = DeclarativePsiFactory(psiElement.project).createPair(literal.nameElement.name(), "placeholder")
          newPair.second.replace(newElement)
          (psiElement.replace(newPair) as DeclarativePair).second
        }
        is DeclarativeAssignment -> psiElement.value?.replace(newElement) ?: return
        is DeclarativeSimpleFactory -> psiElement.argumentsList?.arguments?.firstOrNull()?.replace(newElement) ?: return
        is DeclarativeFactoryReceiver -> psiElement.argumentsList?.arguments?.firstOrNull()?.replace(newElement) ?: return
        else -> psiElement.replace(newElement)
      }
    literal.setExpression(element)
    literal.reset()
    literal.commit()
  }

  override fun deleteDslLiteral(literal: GradleDslLiteral) {
    deleteDslElement(literal)
  }

  private fun maybeUpdateName(element: GradleDslElement) {
    val nameElement = element.nameElement
    val localName = nameElement.localName
    if (localName.isNullOrEmpty() || nameElement.originalName == localName) return

    val oldName = nameElement.namedPsiElement ?: return

    val newName = GradleNameElement.unescape(localName)

    // only rename elements that already have name
    if (oldName is PsiNamedElement) {
      oldName.setName(newName)
      element.nameElement.commitNameChange(oldName, this, element.parent)
    }
  }
  private fun GradleDslElement.isAlreadyCreated(): Boolean =
    psiElement?.findParentOfType<DeclarativeFile>(strict = false) != null &&
    psiElement != null && isInSameFile(this, psiElement!!)

    /**
   * Delete the psiElement for the given dslElement.
   */
  private fun deletePsiElement(dslElement : GradleDslElement) {
    val psiElement = dslElement.psiElement
    if (psiElement == null || !psiElement.isValid) return

    val parentDsl = dslElement.parent ?: return
    psiElement.delete()
    maybeDeleteIfEmpty(parentDsl)
  }

  private fun maybeDeleteIfEmpty(dslElement: GradleDslElement) {
    val element = dslElement.psiElement

    element ?: return
    if (!element.isValid) {
      // Skip deleting
    }
    else {
      when (element) {
        is DeclarativeBlock -> {
          if(element.isEmptyBlock()){
            element.delete()
          } else {
            return
          }
        }
        is DeclarativeArgumentsList ->
          if (element.arguments.isEmpty())
            element.delete()
          else return

        is DeclarativeSimpleFactory ->
          if (element.argumentsList == null || element.argumentsList?.arguments?.isEmpty() == true) {
            element.delete()
          }
          else return

        is DeclarativeFile -> return
      }
    }

    val dslParent = getNextValidParent(dslElement)
    if (dslParent != null && dslParent != dslElement && dslParent.isInsignificantIfEmpty) {
      maybeDeleteIfEmpty(dslParent)
    }
  }
  private fun DeclarativeBlock.isEmptyBlock():Boolean = entries.isEmpty()

}
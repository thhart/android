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

import com.android.tools.idea.gradle.dcl.lang.psi.AssignmentType
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeArgumentsList
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFactoryReceiver
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFile
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeLiteral
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePair
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePsiFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeReceiverPrefixedFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeRecursiveVisitor
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeSimpleFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeValue
import com.android.tools.idea.gradle.dcl.lang.psi.kind
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.AUGMENTED_ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.LITERAL
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.intellij.psi.PsiElement

class DeclarativeDslParser(
  private val psiFile: DeclarativeFile,
  private val context: BuildModelContext,
  private val dslFile: GradleDslFile
) : GradleDslParser, DeclarativeDslNameConverter {
  override fun shouldInterpolate(elementToCheck: GradleDslElement): Boolean = false

  override fun getResolvedInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): MutableList<GradleReferenceInjection> =
    mutableListOf()

  override fun getInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): MutableList<GradleReferenceInjection> =
    mutableListOf()

  override fun getPropertiesElement(
    nameParts: MutableList<String>,
    parentElement: GradlePropertiesDslElement,
    nameElement: GradleNameElement?
  ): GradlePropertiesDslElement? {
    return null
  }

  override fun convertToPsiElement(context: GradleDslSimpleExpression, literal: Any): PsiElement? {
    val factory = DeclarativePsiFactory(context.dslFile.project)
    return when (literal) {
      is String -> factory.createStringLiteral("$literal")
      is Int -> factory.createIntLiteral(literal)
      is Boolean -> factory.createBooleanLiteral(literal)
      else -> null
    }
  }

  override fun setUpForNewValue(context: GradleDslLiteral, newValue: PsiElement?) = Unit

  override fun extractValue(context: GradleDslSimpleExpression, literal: PsiElement, resolve: Boolean): Any? =
    (literal as? DeclarativeLiteral)?.let { it.kind?.value } ?: literal.text

  override fun getContext(): BuildModelContext = context
  override fun parse() {
    fun getVisitor(context: GradlePropertiesDslElement, nameElement: GradleNameElement, parentSyntax: ExternalNameSyntax?): DeclarativeRecursiveVisitor =
      object : DeclarativeRecursiveVisitor() {
        override fun visitBlock(psi: DeclarativeBlock) {
          val name = psi.identifier.name ?: return
          val description = context.getChildPropertiesElementDescription(this@DeclarativeDslParser, name) ?: return
          val block: GradlePropertiesDslElement? =
            if (GradleDslNamedDomainElement::class.java.isAssignableFrom(description.clazz) &&
                description.namedObjectAssociatedName == name) {
              // named object - it's always `function("name") {} ` syntax
              val element = getDomainNameDslElement(psi, description, context)
              (element as? GradleDslNamedDomainElement)?.methodName = description.namedObjectAssociatedName
              element
            }
            else {
              val identifier = psi.identifier
              getOrCreateElement(description, context, identifier, psi)
            }
          if (block != null) {
            psi.blockGroup.entries.forEach { entry -> entry.accept(getVisitor(block, GradleNameElement.empty(), null)) }
          }
        }

        override fun visitAssignment(psi: DeclarativeAssignment) {
          val syntax = when (psi.assignmentType) {
            AssignmentType.ASSIGNMENT -> ASSIGNMENT
            AssignmentType.APPEND -> AUGMENTED_ASSIGNMENT
          }
          psi.value?.accept(getVisitor(context, GradleNameElement.from(psi.assignableProperty, this@DeclarativeDslParser), syntax))
        }

        override fun visitSimpleFactory(psi: DeclarativeSimpleFactory) {
          val dslElement = maybeCreateBlock(psi, context) ?: parseFactory(psi, context, nameElement, parentSyntax) ?: return
          context.addParsedElement(dslElement)
        }

        override fun visitReceiverPrefixedFactory(factory: DeclarativeReceiverPrefixedFactory) {
          val expression = GradleDslInfixExpression(context, factory)
          //parse factory if expression consists only one element
          factory.getReceiver().let { receiver ->
            if (receiver.getReceiver() != null) return // handle only two call a().b() max
            val list = listOf(receiver, factory)
            if (list.any { it.argumentsList?.arguments?.size == 1 }) {
              list.forEach { factoryElement ->
                val name = factoryElement.identifier.name
                val arg = factoryElement.argumentsList?.arguments?.first()
                if (name != null && arg != null && arg is DeclarativeLiteral)
                  arg.value?.let {
                    GradleDslLiteral(context, factoryElement, GradleNameElement.from(factoryElement.identifier, this@DeclarativeDslParser), arg, LITERAL).also {
                      it.externalSyntax = ExternalNameSyntax.METHOD
                      it.setElementType(PropertyType.REGULAR)
                      expression.addParsedElement(it)
                    }
                  }
              }
            }
            context.addParsedElement(expression)
          }
        }
        override fun visitLiteral(psi: DeclarativeLiteral) {
          val newLiteral = GradleDslLiteral(context, psi.parent, nameElement, psi, LITERAL).also {
            if (parentSyntax != null) it.externalSyntax = parentSyntax
          }
          context.addParsedElement(newLiteral)
        }
      }
    psiFile.accept(getVisitor(dslFile, GradleNameElement.empty(), null))
  }

  private fun getDomainNameDslElement(
    psi: DeclarativeBlock,
    description: PropertiesElementDescription<*>,
    context: GradlePropertiesDslElement
  ): GradlePropertiesDslElement? {
    val arguments = psi.embeddedFactory?.argumentsList
    return arguments?.argumentList?.firstOrNull()?.let { literal ->
      val value = (literal.value as? DeclarativeLiteral)?.value
      if (value is String) {
        getOrCreateElement(description, context, literal.value, psi)
      }
      else null
    }
  }

  private fun getOrCreateElement(
    description: PropertiesElementDescription<*>,
    context: GradlePropertiesDslElement,
    identifier: PsiElement,
    psi: PsiElement
  ): GradlePropertiesDslElement {

    val existingElement = if (description.name == null) {
      //domain name object
      context.getPropertyElement(identifier.text, description.clazz)
    }
    else {
      context.getPropertyElement(description)
    }

    if (existingElement != null) {
      existingElement.setParent(context)
      existingElement.psiElement = psi
      return existingElement
    }
    else {
      // new element
      return description.constructor.construct(context, GradleNameElement.from(identifier, this@DeclarativeDslParser)).also {
        it.psiElement = psi
        context.addParsedElement(it)
      }

    }
  }

  private fun getExpressionVisitor(list: GradlePropertiesDslElement,
                                   context: GradlePropertiesDslElement,
                                   nameElement: GradleNameElement): DeclarativeRecursiveVisitor =
    object : DeclarativeRecursiveVisitor() {
      override fun visitLiteral(psi: DeclarativeLiteral) {
        val literal = GradleDslLiteral(list, psi, nameElement, psi, LITERAL)
        literal.setElementType(PropertyType.REGULAR)
        list.addParsedElement(literal)
      }

      override fun visitFactoryReceiver(psi: DeclarativeFactoryReceiver) {
        val methodCall = parseFactory(psi, context, nameElement, null) ?: return
        list.addParsedElement(methodCall)
      }
    }

  private fun maybeCreateBlock(factory: DeclarativeSimpleFactory,
                               context: GradlePropertiesDslElement): GradleDslElement? {
    val name = factory.identifier.name
    val description = context.getChildPropertiesElementDescription(this@DeclarativeDslParser, name)
    if (factory.argumentsList?.argumentList?.isEmpty() == true && description != null) {
      val element = description.constructor.construct(context, GradleNameElement.from(factory.identifier, this@DeclarativeDslParser))
      element.psiElement = factory
      return element
    }
    return null
  }

  private fun parseFactory(psi: DeclarativeFactoryReceiver,
                           context: GradlePropertiesDslElement,
                           currentNameElement: GradleNameElement,
                           parentSyntax: ExternalNameSyntax?): GradleDslExpression? {
    val name = psi.identifier.name ?: return null

    val nameElement = if (currentNameElement.isEmpty)
      GradleNameElement.from(psi.identifier, this@DeclarativeDslParser)
    else
      currentNameElement

    val argumentList = psi.argumentsList
    return if (argumentList == null) {
      getMethodCall(context, psi, nameElement, name, null)
    }
    else {
      getCallExpression(context, psi, nameElement, argumentList, name, parentSyntax)
    }
  }

  private fun getMethodCall(
    context: GradlePropertiesDslElement,
    psiElement: PsiElement,
    name: GradleNameElement,
    methodName: String,
    argumentList: DeclarativeArgumentsList?,
  ): GradleDslMethodCall {
    val methodCall = GradleDslMethodCall(context, psiElement, name, methodName, false)
    if (argumentList == null) return methodCall

    val arguments = GradleDslExpressionList(methodCall, argumentList, false, GradleNameElement.empty())
    argumentList.arguments.forEach {
      it.accept(getExpressionVisitor(arguments, context, GradleNameElement.empty()))
    }
    methodCall.setParsedArgumentList(arguments)

    return methodCall
  }

  private fun getCallExpression(
    context: GradlePropertiesDslElement,
    psiElement : PsiElement,
    name : GradleNameElement,
    argumentsList : DeclarativeArgumentsList,
    methodName : String,
    parentSyntax: ExternalNameSyntax?
  ) : GradleDslExpression {
    return when (methodName) {
      "listOf", "mutableListOf", "setOf", "mutableSetOf" -> {
        val expression = getExpressionList(context, psiElement, name, argumentsList.arguments)
        if (parentSyntax != null) expression.externalSyntax = parentSyntax
        expression
      }
      "mapOf", "mutableMapOf" -> {
        val mapExpression = getExpressionMap(context, psiElement, name, argumentsList.arguments)
        if (parentSyntax != null) mapExpression.externalSyntax = parentSyntax
        mapExpression
      }
      else -> getMethodCall(context, psiElement, name, methodName, argumentsList)
    }
  }

  private fun getExpressionMap(parentElement: GradleDslElement,
                               mapPsiElement: PsiElement,
                               propertyName: GradleNameElement,
                               argumentsList: List<DeclarativeValue>): GradleDslExpressionMap {
    val expressionMap = GradleDslExpressionMap(parentElement, mapPsiElement, propertyName, false)
    argumentsList.filterIsInstance<DeclarativePair>().map { argument ->
      argument.second.accept(getExpressionVisitor(expressionMap, expressionMap, GradleNameElement.from(argument.first, this)))
    }
    return expressionMap
  }

  private fun getExpressionList(
    context: GradlePropertiesDslElement,
    listPsiElement: PsiElement,
    propertyName: GradleNameElement,
    valueArguments: List<DeclarativeValue>
  ): GradleDslExpressionList {
    val expressionList = GradleDslExpressionList(context, listPsiElement, false, propertyName)
    valueArguments.forEach {
      it.accept(getExpressionVisitor(expressionList, context, GradleNameElement.empty()))
    }
    return expressionList
  }
}